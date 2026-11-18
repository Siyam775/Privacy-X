package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.backup.GoogleDriveBackupManager
import com.example.core.AppConstants
import com.example.core.ChatAppearancePreferences
import com.example.core.ThemePreferencesStore
import com.example.data.local.LocalMediaEntity
import com.example.data.local.LocalMediaRepository
import com.example.data.local.PendingOutgoingMessageEntity
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.ConversationMember
import com.example.data.model.GroupTopic
import com.example.data.model.UserProfile
import com.example.data.repository.AiAssistantRepository
import com.example.data.repository.MessagingRepository
import com.example.data.repository.SocialRepository
import com.example.media.SupabaseMediaService
import com.example.media.VoicePlaybackState
import com.example.media.VoiceRecorderPlayer
import com.example.media.VoiceRecordingState
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

data class UploadUiState(
    val isUploading: Boolean = false,
    val progress: Float = 0f,
    val fileName: String = "",
    val errorMessage: String? = null,
    val pendingUri: Uri? = null,
    val pendingConversation: Conversation? = null,
    val pendingCaption: String = "",
    val compressImage: Boolean = true,
    val pendingIsVoice: Boolean = false,
    val pendingDurationMs: Long = 0L
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val messagingRepo = MessagingRepository(context)
    private val socialRepo = SocialRepository(context)
    private val aiRepo = AiAssistantRepository(context)
    private val themeStore = ThemePreferencesStore(context)
    val localRepo = LocalMediaRepository(context)
    private val backupManager = GoogleDriveBackupManager(context)
    val mediaService = SupabaseMediaService(context)
    val voiceRecorderPlayer = VoiceRecorderPlayer(context)

    private val auth = Firebase.auth
    private val photoCachingIds = mutableSetOf<String>()

    private val _myConversations = MutableStateFlow<List<Conversation>>(emptyList())
    val myConversations: StateFlow<List<Conversation>> = _myConversations.asStateFlow()

    private val _publicConversations = MutableStateFlow<List<Conversation>>(emptyList())
    val publicConversations: StateFlow<List<Conversation>> = _publicConversations.asStateFlow()

    private val _activeConversation = MutableStateFlow<Conversation?>(null)
    val activeConversation: StateFlow<Conversation?> = _activeConversation.asStateFlow()

    private val _remoteMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _cachedMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _pendingOutgoing = localRepo.pendingOutgoingFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val messages: StateFlow<List<ChatMessage>> = combine(
        _remoteMessages,
        _cachedMessages,
        _pendingOutgoing,
        _activeConversation
    ) { remote, cached, pending, conv ->
        val base = if (remote.isNotEmpty()) remote else cached
        val activeConvId = conv?.id.orEmpty()
        val remoteIds = base.mapTo(HashSet()) { it.id }
        val localPendingForChat = pending
            .filter { it.conversationId == activeConvId && it.clientMessageId !in remoteIds }
            .map { p ->
                ChatMessage(
                    id = p.clientMessageId,
                    conversationId = p.conversationId,
                    senderId = p.senderId,
                    senderName = p.senderName,
                    senderAvatarUrl = p.senderAvatarUrl,
                    text = p.text,
                    mediaType = "text",
                    replyToId = p.replyToId,
                    replyToPreview = p.replyToPreview,
                    topicId = p.topicId,
                    createdAt = Timestamp(Date(p.createdAtMs)),
                    updatedAt = Timestamp(Date(p.createdAtMs))
                )
            }
        base + localPendingForChat
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val localMediaMap: StateFlow<Map<String, LocalMediaEntity>> = localRepo.localMediaFlow
        .map { list ->
            buildMap {
                list.forEach { item ->
                    if (item.mediaId.isNotBlank()) put(item.mediaId, item)
                    if (item.messageId.isNotBlank()) put(item.messageId, item)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val pendingMessageIds: StateFlow<Set<String>> = _pendingOutgoing
        .map { list -> list.mapTo(HashSet()) { it.clientMessageId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val knownBackupMediaIds: StateFlow<Set<String>> = backupManager.preferencesFlow
        .map { it.knownBackupMediaIds }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    private val _downloadingMessageId = MutableStateFlow<String?>(null)
    val downloadingMessageId: StateFlow<String?> = _downloadingMessageId.asStateFlow()

    private val _myMemberState = MutableStateFlow<ConversationMember?>(null)
    val myMemberState: StateFlow<ConversationMember?> = _myMemberState.asStateFlow()

    private val _allMembers = MutableStateFlow<List<ConversationMember>>(emptyList())
    val allMembers: StateFlow<List<ConversationMember>> = _allMembers.asStateFlow()

    private val _topics = MutableStateFlow<List<GroupTopic>>(emptyList())
    val topics: StateFlow<List<GroupTopic>> = _topics.asStateFlow()

    private val _draftText = MutableStateFlow("")
    val draftText: StateFlow<String> = _draftText.asStateFlow()

    private val _chatAppearance = MutableStateFlow(ChatAppearancePreferences())
    val chatAppearance: StateFlow<ChatAppearancePreferences> = _chatAppearance.asStateFlow()

    private val _uploadState = MutableStateFlow(UploadUiState())
    val uploadState: StateFlow<UploadUiState> = _uploadState.asStateFlow()

    private val _aiChatHelperResult = MutableStateFlow<String?>(null)
    val aiChatHelperResult: StateFlow<String?> = _aiChatHelperResult.asStateFlow()

    private val _smartReplies = MutableStateFlow<List<String>>(emptyList())
    val smartReplies: StateFlow<List<String>> = _smartReplies.asStateFlow()

    private val _aiHelperLoading = MutableStateFlow(false)
    val aiHelperLoading: StateFlow<Boolean> = _aiHelperLoading.asStateFlow()

    private val _chatNotice = MutableStateFlow<String?>(null)
    val chatNotice: StateFlow<String?> = _chatNotice.asStateFlow()

    private val _slowModeRemainingSeconds = MutableStateFlow(0L)
    val slowModeRemainingSeconds: StateFlow<Long> = _slowModeRemainingSeconds.asStateFlow()

    val recordingState: StateFlow<VoiceRecordingState> = voiceRecorderPlayer.recordingState
    val playbackState: StateFlow<VoicePlaybackState> = voiceRecorderPlayer.playbackState

    private var listJobs: Job? = null
    private var detailJobs: Job? = null
    private var uploadJob: Job? = null
    private var slowModeJob: Job? = null
    private var syncOutboxJob: Job? = null
    private var lastSentByConversation = mutableMapOf<String, Long>()
    private var currentLimit = AppConstants.MESSAGE_PAGE_SIZE
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        viewModelScope.launch {
            playbackState.collect { st ->
                if (!st.errorMessage.isNullOrBlank()) {
                    _chatNotice.value = st.errorMessage
                }
            }
        }
        viewModelScope.launch {
            localRepo.markExpiredCloudMedia()
        }
        setupNetworkObserver()
    }

    private fun setupNetworkObserver() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val currentlyConnected = runCatching {
            val net = cm.activeNetwork
            val caps = net?.let { cm.getNetworkCapabilities(it) }
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }.getOrDefault(true)
        _isOffline.value = !currentlyConnected

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                _isOffline.value = false
                flushPendingOutbox()
            }

            override fun onLost(network: Network) {
                _isOffline.value = true
            }
        }
        networkCallback = cb
        runCatching { cm.registerDefaultNetworkCallback(cb) }
    }

    fun bindConversationLists(uid: String) {
        listJobs?.cancel()
        listJobs = viewModelScope.launch {
            launch {
                val cached = localRepo.getCachedConversations()
                if (cached.isNotEmpty() && _myConversations.value.isEmpty()) {
                    _myConversations.value = cached
                }
            }
            launch {
                messagingRepo.observeMyConversations(uid)
                    .catch { _chatNotice.value = it.localizedMessage }
                    .collect { list ->
                        _myConversations.value = list
                        localRepo.cacheConversations(list)
                    }
            }
            launch {
                messagingRepo.observePublicDiscoveryConversations()
                    .catch {}
                    .collect { _publicConversations.value = it }
            }
            launch { flushPendingOutbox() }
        }
    }

    fun openConversation(conversationId: String, isPublicChannelPreview: Boolean = false) {
        val uid = auth.currentUser?.uid ?: return
        currentLimit = AppConstants.MESSAGE_PAGE_SIZE
        _smartReplies.value = emptyList()
        _aiChatHelperResult.value = null
        _slowModeRemainingSeconds.value = 0L
        _remoteMessages.value = emptyList()
        _cachedMessages.value = emptyList()
        detailJobs?.cancel()
        detailJobs = viewModelScope.launch {
            launch {
                localRepo.observeCachedMessages(conversationId).collect { cached ->
                    _cachedMessages.value = cached
                }
            }
            launch {
                themeStore.observeDraft(conversationId).collect { _draftText.value = it }
            }
            launch {
                themeStore.observeChatAppearance(conversationId).collect { _chatAppearance.value = it }
            }
            launch {
                messagingRepo.observeConversation(conversationId)
                    .catch { _chatNotice.value = it.localizedMessage }
                    .collect { conv ->
                        _activeConversation.value = conv
                        if (conv != null) refreshSlowModeTimer(conv, uid)
                    }
            }
            launch {
                val flow = if (isPublicChannelPreview) {
                    messagingRepo.observePublicChannelMessages(conversationId, currentLimit)
                } else {
                    messagingRepo.observeMessages(conversationId, currentLimit)
                }
                flow.catch { _chatNotice.value = it.localizedMessage }
                    .collect { msgs ->
                        _remoteMessages.value = msgs
                        localRepo.cacheMessages(msgs)
                        msgs.filter { it.mediaType == "image" }.forEach { message ->
                            val cacheId = message.mediaId.ifBlank { message.id }
                            if (photoCachingIds.add(cacheId)) {
                                launch {
                                    try {
                                        mediaService.cachePhotoLocally(message)
                                    } finally {
                                        photoCachingIds.remove(cacheId)
                                    }
                                }
                            }
                        }
                        localRepo.markExpiredCloudMedia()
                        messagingRepo.markMessagesRead(conversationId, msgs)
                    }
            }
            launch {
                messagingRepo.observeMemberState(conversationId, uid)
                    .catch {}
                    .collect { member ->
                        _myMemberState.value = member
                        if (member != null && member.customTheme.isNotBlank() &&
                            _chatAppearance.value.colorPresetId == "default"
                        ) {
                            themeStore.saveChatAppearance(conversationId, colorPresetId = member.customTheme)
                        }
                    }
            }
            launch {
                messagingRepo.observeConversationMembers(conversationId)
                    .catch {}
                    .collect { _allMembers.value = it }
            }
            launch {
                messagingRepo.observeTopics(conversationId)
                    .catch {}
                    .collect { _topics.value = it }
            }
        }
    }

    private fun refreshSlowModeTimer(conversation: Conversation, uid: String) {
        slowModeJob?.cancel()
        if (conversation.slowModeSeconds <= 0L || uid in conversation.adminUids) {
            _slowModeRemainingSeconds.value = 0L
            return
        }
        val lastSent = lastSentByConversation[conversation.id] ?: 0L
        val elapsedSec = (System.currentTimeMillis() - lastSent) / 1000L
        val remaining = (conversation.slowModeSeconds - elapsedSec).coerceAtLeast(0L)
        if (remaining <= 0L) {
            _slowModeRemainingSeconds.value = 0L
            return
        }
        slowModeJob = viewModelScope.launch {
            var left = remaining
            while (isActive && left > 0L) {
                _slowModeRemainingSeconds.value = left
                delay(1000L)
                left -= 1L
            }
            _slowModeRemainingSeconds.value = 0L
        }
    }

    fun loadMoreMessages(conversationId: String, isPublicChannelPreview: Boolean = false) {
        currentLimit += AppConstants.MESSAGE_PAGE_SIZE
        openConversation(conversationId, isPublicChannelPreview)
    }

    fun updateDraft(conversationId: String, text: String) {
        _draftText.value = text
        viewModelScope.launch {
            themeStore.saveDraft(conversationId, text)
        }
    }

    fun sendTypingIndicator(conversation: Conversation) {
        viewModelScope.launch {
            messagingRepo.updateMemberState(
                conversationId = conversation.id,
                memberUids = conversation.memberUids,
                typingNow = true
            )
        }
    }

    suspend fun startDirectChat(
        otherUid: String,
        otherDisplayName: String,
        otherPhotoUrl: String
    ): Result<String> =
        messagingRepo.createOrGetDirectConversation(otherUid, otherDisplayName, otherPhotoUrl)

    suspend fun createGroupOrCommunityOrChannel(
        type: String,
        title: String,
        description: String,
        isPublic: Boolean,
        memberUids: List<String> = emptyList(),
        communityId: String = ""
    ): Result<String> = messagingRepo.createGroupOrCommunityOrChannel(
        type = type,
        title = title,
        description = description,
        isPublic = isPublic,
        memberUids = memberUids,
        communityId = communityId
    )

    fun joinPublicConversation(conversationId: String) {
        viewModelScope.launch {
            val res = messagingRepo.joinPublicConversation(conversationId)
            _chatNotice.value = if (res.isSuccess) "Joined!" else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun leaveConversation(conversationId: String, onLeft: () -> Unit) {
        viewModelScope.launch {
            val res = messagingRepo.leaveConversation(conversationId)
            if (res.isSuccess) onLeft()
            else _chatNotice.value = res.exceptionOrNull()?.localizedMessage
        }
    }

    fun sendTextOrMediaMessage(
        conversation: Conversation,
        profile: UserProfile,
        text: String,
        mediaType: String = "text",
        mediaUrl: String = "",
        mediaId: String = "",
        mediaThumbnailUrl: String = "",
        mediaExpiresAt: Long = 0L,
        fileName: String = "",
        fileSizeBytes: Long = 0L,
        mimeType: String = "",
        durationMs: Long = 0L,
        replyTo: ChatMessage? = null,
        topicId: String = "",
        disappearsInMinutes: Long? = null,
        scheduledDelayMinutes: Long? = null
    ) {
        if (conversation.slowModeSeconds > 0L && profile.userId !in conversation.adminUids) {
            val left = _slowModeRemainingSeconds.value
            if (left > 0L) {
                _chatNotice.value = "Slow mode active: wait ${left}s before sending."
                return
            }
        }
        viewModelScope.launch {
            val clientMsgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(18)}"
            val replyPreview = replyTo?.let { "${it.senderName}: ${it.text.ifBlank { it.mediaType }}" }?.take(120).orEmpty()

            if (_isOffline.value && mediaType == "text") {
                localRepo.enqueueOfflineMessage(
                    PendingOutgoingMessageEntity(
                        clientMessageId = clientMsgId,
                        conversationId = conversation.id,
                        senderId = profile.userId,
                        senderName = profile.displayName,
                        senderAvatarUrl = profile.photoUrl,
                        text = text,
                        replyToId = replyTo?.id.orEmpty(),
                        replyToPreview = replyPreview,
                        topicId = topicId,
                        disappearsInMinutes = disappearsInMinutes,
                        scheduledDelayMinutes = scheduledDelayMinutes
                    )
                )
                themeStore.saveDraft(conversation.id, "")
                _draftText.value = ""
                _chatNotice.value = "Offline — message queued to sync automatically."
                return@launch
            }

            val res = messagingRepo.sendMessage(
                conversation = conversation,
                senderName = profile.displayName,
                senderAvatarUrl = profile.photoUrl,
                text = text,
                mediaType = mediaType,
                mediaUrl = mediaUrl,
                mediaId = mediaId,
                mediaThumbnailUrl = mediaThumbnailUrl,
                mediaExpiresAt = mediaExpiresAt,
                mediaExpired = false,
                fileName = fileName,
                fileSizeBytes = fileSizeBytes,
                mimeType = mimeType,
                durationMs = durationMs,
                replyToId = replyTo?.id.orEmpty(),
                replyToPreview = replyPreview,
                topicId = topicId,
                disappearsInMinutes = disappearsInMinutes,
                scheduledDelayMinutes = scheduledDelayMinutes,
                customMessageId = clientMsgId
            )
            if (res.isSuccess) {
                val savedMsgId = res.getOrDefault(clientMsgId)
                if (mediaId.isNotBlank()) {
                    localRepo.linkMessageId(mediaId, savedMsgId)
                }
                lastSentByConversation[conversation.id] = System.currentTimeMillis()
                refreshSlowModeTimer(conversation, profile.userId)
                themeStore.saveDraft(conversation.id, "")
                _draftText.value = ""
                val peerUid = conversation.memberUids.firstOrNull { it != profile.userId }
                if (conversation.type == "direct" && peerUid != null) {
                    socialRepo.sendInAppNotification(
                        targetUserId = peerUid,
                        type = "message",
                        title = profile.displayName,
                        body = text.ifBlank { "Sent $mediaType" },
                        targetId = conversation.id
                    )
                }
            } else {
                if (mediaType == "text") {
                    localRepo.enqueueOfflineMessage(
                        PendingOutgoingMessageEntity(
                            clientMessageId = clientMsgId,
                            conversationId = conversation.id,
                            senderId = profile.userId,
                            senderName = profile.displayName,
                            senderAvatarUrl = profile.photoUrl,
                            text = text,
                            replyToId = replyTo?.id.orEmpty(),
                            replyToPreview = replyPreview,
                            topicId = topicId,
                            disappearsInMinutes = disappearsInMinutes,
                            scheduledDelayMinutes = scheduledDelayMinutes
                        )
                    )
                    themeStore.saveDraft(conversation.id, "")
                    _draftText.value = ""
                    _chatNotice.value = "Queued offline; will sync automatically when reconnected."
                } else {
                    _chatNotice.value = res.exceptionOrNull()?.localizedMessage ?: "Send failed"
                }
            }
        }
    }

    fun flushPendingOutbox() {
        if (syncOutboxJob?.isActive == true) return
        syncOutboxJob = viewModelScope.launch {
            val queue = localRepo.getPendingOutgoingMessages()
            if (queue.isEmpty()) return@launch
            val convMap = _myConversations.value.associateBy { it.id }
            queue.forEach { pending ->
                val conv = convMap[pending.conversationId] ?: return@forEach
                val res = messagingRepo.sendMessage(
                    conversation = conv,
                    senderName = pending.senderName,
                    senderAvatarUrl = pending.senderAvatarUrl,
                    text = pending.text,
                    mediaType = "text",
                    replyToId = pending.replyToId,
                    replyToPreview = pending.replyToPreview,
                    topicId = pending.topicId,
                    disappearsInMinutes = pending.disappearsInMinutes,
                    scheduledDelayMinutes = pending.scheduledDelayMinutes,
                    customMessageId = pending.clientMessageId
                )
                if (res.isSuccess) {
                    localRepo.removePendingOutgoing(pending.clientMessageId)
                }
            }
        }
    }

    fun createPoll(
        conversation: Conversation,
        profile: UserProfile,
        question: String,
        options: List<String>
    ) {
        viewModelScope.launch {
            val cleanOptions = options.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (question.isBlank() || cleanOptions.size < 2) {
                _chatNotice.value = "Poll requires a question and at least 2 options."
                return@launch
            }
            messagingRepo.sendMessage(
                conversation = conversation,
                senderName = profile.displayName,
                senderAvatarUrl = profile.photoUrl,
                text = question,
                mediaType = "poll",
                pollQuestion = question,
                pollOptions = cleanOptions
            )
        }
    }

    fun uploadAndSendMedia(
        uri: Uri,
        conversation: Conversation,
        profile: UserProfile,
        caption: String = "",
        compressImage: Boolean = true,
        maxUploadMb: Long = AppConstants.DEFAULT_MAX_UPLOAD_MB
    ) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            _uploadState.value = UploadUiState(
                isUploading = true,
                progress = 0f,
                fileName = uri.lastPathSegment ?: "media",
                pendingUri = uri,
                pendingConversation = conversation,
                pendingCaption = caption,
                compressImage = compressImage,
                pendingIsVoice = false
            )
            val result = mediaService.uploadMedia(
                uri = uri,
                conversationId = conversation.id,
                compressImage = compressImage,
                maxUploadMb = maxUploadMb,
                onProgress = { pct ->
                    _uploadState.value = _uploadState.value.copy(progress = pct)
                }
            )
            result.onSuccess { uploaded ->
                _uploadState.value = UploadUiState()
                sendTextOrMediaMessage(
                    conversation = conversation,
                    profile = profile,
                    text = caption,
                    mediaType = uploaded.category,
                    mediaUrl = uploaded.secureUrl,
                    mediaId = uploaded.mediaId,
                    mediaThumbnailUrl = uploaded.thumbnailUrl,
                    mediaExpiresAt = uploaded.expiresAtMs,
                    fileName = uploaded.fileName,
                    fileSizeBytes = uploaded.sizeBytes,
                    mimeType = uploaded.mimeType
                )
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Upload failed"
                _uploadState.value = _uploadState.value.copy(
                    isUploading = false,
                    errorMessage = msg
                )
                _chatNotice.value = msg
            }
        }
    }

    fun cancelUpload() {
        uploadJob?.cancel()
        uploadJob = null
        _uploadState.value = UploadUiState()
    }

    fun retryUpload(profile: UserProfile) {
        val state = _uploadState.value
        val uri = state.pendingUri ?: return
        val conv = state.pendingConversation ?: return
        if (state.pendingIsVoice) {
            uploadVoiceUri(uri, state.pendingDurationMs, conv, profile)
        } else {
            uploadAndSendMedia(uri, conv, profile, state.pendingCaption, state.compressImage)
        }
    }

    fun startVoiceRecording() {
        val res = voiceRecorderPlayer.startRecording()
        res.onFailure {
            _chatNotice.value = it.localizedMessage ?: "Microphone unavailable"
        }
    }

    fun togglePauseVoiceRecording() = voiceRecorderPlayer.togglePauseRecording()

    fun cancelVoiceRecording() = voiceRecorderPlayer.cancelRecording()

    fun finishAndSendVoiceRecording(conversation: Conversation, profile: UserProfile) {
        val finished = voiceRecorderPlayer.finishRecording()
        finished.onFailure { err ->
            _chatNotice.value = err.localizedMessage ?: "Unable to save voice recording."
            return
        }
        val (uri, durationMs) = finished.getOrThrow()
        uploadVoiceUri(uri, durationMs, conversation, profile)
    }

    private fun uploadVoiceUri(
        uri: Uri,
        durationMs: Long,
        conversation: Conversation,
        profile: UserProfile
    ) {
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            _uploadState.value = UploadUiState(
                isUploading = true,
                progress = 0f,
                fileName = "Voice note",
                pendingUri = uri,
                pendingConversation = conversation,
                compressImage = false,
                pendingIsVoice = true,
                pendingDurationMs = durationMs
            )
            val result = mediaService.uploadMedia(
                uri = uri,
                conversationId = conversation.id,
                compressImage = false,
                durationMs = durationMs
            ) { pct ->
                _uploadState.value = _uploadState.value.copy(progress = pct)
            }
            result.onSuccess { uploaded ->
                _uploadState.value = UploadUiState()
                sendTextOrMediaMessage(
                    conversation = conversation,
                    profile = profile,
                    text = "",
                    mediaType = "voice",
                    mediaUrl = uploaded.secureUrl,
                    mediaId = uploaded.mediaId,
                    mediaThumbnailUrl = uploaded.thumbnailUrl,
                    mediaExpiresAt = uploaded.expiresAtMs,
                    fileName = uploaded.fileName,
                    fileSizeBytes = uploaded.sizeBytes,
                    mimeType = uploaded.mimeType,
                    durationMs = durationMs
                )
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Voice upload failed"
                _uploadState.value = _uploadState.value.copy(
                    isUploading = false,
                    errorMessage = msg
                )
                _chatNotice.value = msg
            }
        }
    }

    fun downloadMessageMedia(message: ChatMessage) {
        viewModelScope.launch {
            _downloadingMessageId.value = message.id
            val res = mediaService.downloadMediaToDevice(message)
            _downloadingMessageId.value = null
            res.onSuccess { dl ->
                _chatNotice.value = "Saved permanently & exported to ${dl.exportedDisplayPath}"
            }.onFailure { err ->
                _chatNotice.value = err.localizedMessage ?: "Download failed"
            }
        }
    }

    fun toggleVoicePlayback(url: String) = voiceRecorderPlayer.togglePlay(url)
    fun cycleVoiceSpeed() = voiceRecorderPlayer.cyclePlaybackSpeed()

    fun editMessage(conversationId: String, messageId: String, newText: String) {
        viewModelScope.launch { messagingRepo.editMessage(conversationId, messageId, newText) }
    }

    fun deleteMessage(conversationId: String, messageId: String) {
        viewModelScope.launch { messagingRepo.deleteMessage(conversationId, messageId) }
    }

    fun toggleReaction(conversationId: String, messageId: String, emoji: String) {
        viewModelScope.launch { messagingRepo.toggleReaction(conversationId, messageId, emoji) }
    }

    fun votePoll(conversationId: String, messageId: String, option: String) {
        viewModelScope.launch { messagingRepo.votePoll(conversationId, messageId, option) }
    }

    fun togglePin(conversationId: String, message: ChatMessage) {
        viewModelScope.launch {
            val res = messagingRepo.togglePinMessage(
                conversationId = conversationId,
                messageId = message.id,
                previewText = message.text.ifBlank { message.mediaType },
                pin = !message.isPinned
            )
            _chatNotice.value = if (res.isSuccess) {
                if (!message.isPinned) "Message pinned" else "Message unpinned"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Pin update failed"
            }
        }
    }

    fun forwardMessage(targetConversation: Conversation, original: ChatMessage, profile: UserProfile) {
        viewModelScope.launch {
            messagingRepo.sendMessage(
                conversation = targetConversation,
                senderName = profile.displayName,
                senderAvatarUrl = profile.photoUrl,
                text = original.text,
                mediaType = original.mediaType,
                mediaUrl = original.mediaUrl,
                fileName = original.fileName,
                fileSizeBytes = original.fileSizeBytes,
                mimeType = original.mimeType,
                durationMs = original.durationMs,
                forwardedFrom = original.senderName
            )
            _chatNotice.value = "Forwarded to ${targetConversation.title}"
        }
    }

    fun bookmarkMessage(message: ChatMessage) {
        viewModelScope.launch {
            val res = socialRepo.saveMessageBookmark(
                content = message.text.ifBlank { message.fileName.ifBlank { message.mediaType } },
                sourceConversationId = message.conversationId,
                sourceSenderName = message.senderName,
                mediaUrl = message.mediaUrl,
                mediaType = message.mediaType
            )
            _chatNotice.value = if (res.isSuccess) "Saved to Saved Messages" else "Save failed"
        }
    }

    fun toggleMuteConversation(conversation: Conversation) {
        val currentMuted = _myMemberState.value?.isMuted == true
        updateMemberState(conversation, isMuted = !currentMuted)
        _chatNotice.value = if (!currentMuted) "Notifications muted" else "Notifications unmuted"
    }

    fun updateMemberState(
        conversation: Conversation,
        isMuted: Boolean? = null,
        isArchived: Boolean? = null,
        markedUnread: Boolean? = null,
        folder: String? = null,
        customTheme: String? = null,
        customWallpaper: String? = null
    ) {
        val current = _myMemberState.value
        val uid = auth.currentUser?.uid
        val role = when {
            uid == conversation.ownerId -> "owner"
            uid != null && uid in conversation.adminUids -> "admin"
            conversation.type == "channel" -> "subscriber"
            else -> "member"
        }
        viewModelScope.launch {
            messagingRepo.updateMemberState(
                conversationId = conversation.id,
                memberUids = conversation.memberUids,
                role = role,
                isMuted = isMuted ?: current?.isMuted,
                isArchived = isArchived ?: current?.isArchived,
                markedUnread = markedUnread ?: current?.markedUnread,
                folder = folder ?: current?.folder,
                customTheme = customTheme ?: current?.customTheme,
                customWallpaper = customWallpaper ?: current?.customWallpaper
            )
        }
    }

    // Per-Chat Colors & Wallpapers
    fun selectChatColorPreset(conversation: Conversation, colorPresetId: String) {
        viewModelScope.launch {
            themeStore.saveChatAppearance(conversation.id, colorPresetId = colorPresetId)
            updateMemberState(conversation, customTheme = colorPresetId)
            _chatNotice.value = "Chat theme color updated"
        }
    }

    fun selectBuiltInWallpaper(conversation: Conversation, wallpaperPresetId: String) {
        viewModelScope.launch {
            themeStore.saveChatAppearance(
                conversationId = conversation.id,
                wallpaperPresetId = wallpaperPresetId,
                customWallpaperUri = ""
            )
            updateMemberState(conversation, customWallpaper = wallpaperPresetId)
            _chatNotice.value = "Wallpaper applied"
        }
    }

    fun setCustomGalleryWallpaper(conversation: Conversation, uri: Uri) {
        viewModelScope.launch {
            val localCopy = mediaService.saveLocalWallpaperCopy(uri, conversation.id)
            localCopy.onSuccess { fileUri ->
                themeStore.saveChatAppearance(
                    conversationId = conversation.id,
                    wallpaperPresetId = "custom",
                    customWallpaperUri = fileUri
                )
                updateMemberState(conversation, customWallpaper = "custom_local")
                _chatNotice.value = "Custom wallpaper applied"
            }.onFailure { err ->
                _chatNotice.value = err.localizedMessage ?: "Unable to apply wallpaper"
            }
        }
    }

    fun updateWallpaperDim(conversationId: String, dimAlpha: Float) {
        viewModelScope.launch {
            themeStore.saveChatAppearance(conversationId = conversationId, wallpaperDimAlpha = dimAlpha)
        }
    }

    // Group, Community & Channel Admin Controls
    fun updateConversationSettings(
        conversationId: String,
        title: String? = null,
        description: String? = null,
        isPublic: Boolean? = null,
        slowModeSeconds: Long? = null,
        memberApprovalRequired: Boolean? = null,
        adminsOnlyMessaging: Boolean? = null
    ) {
        viewModelScope.launch {
            val res = messagingRepo.updateConversationSettings(
                conversationId = conversationId,
                title = title,
                description = description,
                isPublic = isPublic,
                slowModeSeconds = slowModeSeconds,
                memberApprovalRequired = memberApprovalRequired,
                adminsOnlyMessaging = adminsOnlyMessaging
            )
            _chatNotice.value = if (res.isSuccess) "Settings saved" else res.exceptionOrNull()?.localizedMessage ?: "Failed to update"
        }
    }

    fun uploadAndSetConversationAvatar(conversationId: String, uri: Uri) {
        viewModelScope.launch {
            val uploadRes = mediaService.uploadMedia(uri, compressImage = true)
            uploadRes.onSuccess { uploaded ->
                val res = messagingRepo.updateConversationSettings(
                    conversationId = conversationId,
                    photoUrl = uploaded.secureUrl
                )
                _chatNotice.value = if (res.isSuccess) "Group photo updated" else res.exceptionOrNull()?.localizedMessage
            }.onFailure { err ->
                _chatNotice.value = err.localizedMessage ?: "Avatar upload failed"
            }
        }
    }

    fun regenerateInviteCode(conversationId: String) {
        viewModelScope.launch {
            val res = messagingRepo.regenerateInviteCode(conversationId)
            _chatNotice.value = if (res.isSuccess) "New invite code: ${res.getOrNull()}" else "Failed to reset code"
        }
    }

    fun createTopic(conversation: Conversation, title: String, emoji: String) {
        viewModelScope.launch {
            messagingRepo.createTopic(conversation, title, emoji)
        }
    }

    fun addMember(conversationId: String, uid: String) {
        viewModelScope.launch {
            val res = messagingRepo.addMember(conversationId, uid)
            _chatNotice.value = if (res.isSuccess) "Member added" else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun removeMember(conversationId: String, uid: String) {
        viewModelScope.launch {
            val res = messagingRepo.removeMember(conversationId, uid)
            _chatNotice.value = if (res.isSuccess) "Member removed" else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun promoteMember(conversationId: String, uid: String) {
        viewModelScope.launch {
            val res = messagingRepo.promoteMemberToAdmin(conversationId, uid)
            _chatNotice.value = if (res.isSuccess) "Promoted to Admin" else res.exceptionOrNull()?.localizedMessage
        }
    }

    fun demoteMember(conversationId: String, uid: String) {
        viewModelScope.launch {
            val res = messagingRepo.demoteMemberFromAdmin(conversationId, uid)
            _chatNotice.value = if (res.isSuccess) "Admin role removed" else res.exceptionOrNull()?.localizedMessage
        }
    }

    // Firebase AI Logic Helpers in Chat
    fun translateMessage(text: String, targetLanguage: String = "English", aiConsentGranted: Boolean) {
        if (!aiConsentGranted) {
            _chatNotice.value = "Enable AI Assistant consent in Settings or AI tab first."
            return
        }
        viewModelScope.launch {
            _aiHelperLoading.value = true
            val res = aiRepo.generateQuickText("Translate the following message cleanly into $targetLanguage:\n\n$text")
            _aiHelperLoading.value = false
            res.onSuccess { _aiChatHelperResult.value = "Translation ($targetLanguage):\n$it" }
                .onFailure { _chatNotice.value = it.localizedMessage ?: "Translation failed" }
        }
    }

    fun summarizeActiveChat(aiConsentGranted: Boolean) {
        if (!aiConsentGranted) {
            _chatNotice.value = "Enable AI Assistant consent in Settings or AI tab first."
            return
        }
        val snippet = messages.value.takeLast(20).joinToString("\n") { "${it.senderName}: ${it.text}" }
        if (snippet.isBlank()) return
        viewModelScope.launch {
            _aiHelperLoading.value = true
            val res = aiRepo.generateQuickText("Summarize these recent chat messages in 3 concise bullet points:\n\n$snippet")
            _aiHelperLoading.value = false
            res.onSuccess { _aiChatHelperResult.value = "AI Chat Summary:\n$it" }
                .onFailure { _chatNotice.value = it.localizedMessage ?: "Summary failed" }
        }
    }

    fun generateSmartReplies(aiConsentGranted: Boolean) {
        if (!aiConsentGranted) return
        val last = messages.value.lastOrNull()?.text.orEmpty()
        if (last.isBlank()) return
        viewModelScope.launch {
            val res = aiRepo.generateQuickText(
                "Give 3 very short natural chat reply options (max 6 words each, separated by |) for: \"$last\""
            )
            res.onSuccess { raw ->
                _smartReplies.value = raw.split("|").map { it.trim().trim('"') }.filter { it.isNotEmpty() }.take(3)
            }
        }
    }

    fun postNotice(message: String) {
        _chatNotice.value = message
    }

    fun clearAiHelper() {
        _aiChatHelperResult.value = null
    }

    fun clearNotice() {
        _chatNotice.value = null
    }

    override fun onCleared() {
        networkCallback?.let { cb ->
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            runCatching { cm?.unregisterNetworkCallback(cb) }
        }
        voiceRecorderPlayer.release()
        super.onCleared()
    }
}
