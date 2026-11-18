package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PermMedia
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.core.ChatAppearancePreferences
import com.example.data.local.LocalMediaEntity
import com.example.data.local.MediaAvailabilityState
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.ConversationMember
import com.example.data.model.GroupTopic
import com.example.data.model.UserProfile
import com.example.data.model.UsernameReservation
import com.example.media.VoicePlaybackState
import com.example.media.VoiceRecordingState
import com.example.ui.components.GlassCard
import com.example.ui.components.UserAvatar
import com.example.ui.theme.ChatColorPreset
import com.example.ui.theme.ChatWallpaperLayer
import com.example.ui.theme.LocalPrivacyXTokens
import com.example.ui.viewmodel.UploadUiState
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Composable
fun ChatDetailScreen(
    currentUser: UserProfile,
    conversation: Conversation,
    messages: List<ChatMessage>,
    allConversations: List<Conversation>,
    members: List<ConversationMember>,
    topics: List<GroupTopic>,
    directoryUsers: List<UsernameReservation> = emptyList(),
    chatAppearance: ChatAppearancePreferences = ChatAppearancePreferences(),
    isMuted: Boolean = false,
    slowModeRemainingSeconds: Long = 0L,
    draftText: String,
    uploadState: UploadUiState,
    recordingState: VoiceRecordingState,
    playbackState: VoicePlaybackState,
    smartReplies: List<String>,
    aiHelperResult: String?,
    aiConsentGranted: Boolean,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onTyping: () -> Unit,
    onLoadMore: () -> Unit,
    onSendMessage: (
        text: String,
        replyTo: ChatMessage?,
        topicId: String,
        disappearsInMinutes: Long?,
        scheduledDelayMinutes: Long?
    ) -> Unit,
    onSendSticker: (String) -> Unit,
    onUploadMedia: (Uri, String, Boolean) -> Unit,
    onCancelUpload: () -> Unit,
    onRetryUpload: () -> Unit,
    onStartRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onSendRecording: () -> Unit,
    onVoicePermissionDenied: () -> Unit = {},
    onTogglePlayVoice: (String) -> Unit,
    onCycleVoiceSpeed: () -> Unit,
    onEditMessage: (ChatMessage, String) -> Unit,
    onDeleteMessage: (ChatMessage) -> Unit,
    onToggleReaction: (ChatMessage, String) -> Unit,
    onVotePoll: (ChatMessage, String) -> Unit,
    onTogglePin: (ChatMessage) -> Unit,
    onForwardMessage: (Conversation, ChatMessage) -> Unit,
    onBookmarkMessage: (ChatMessage) -> Unit,
    onCreatePoll: (String, List<String>) -> Unit,
    onCreateTopic: (String, String) -> Unit,
    onSummarizeChat: () -> Unit,
    onTranslateMessage: (String) -> Unit,
    onGenerateSmartReplies: () -> Unit,
    onDismissAiHelper: () -> Unit,
    onStartVoiceCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onToggleMute: () -> Unit,
    onLeaveConversation: () -> Unit,
    onReportConversation: (String, String) -> Unit,
    onSelectColorPreset: (String) -> Unit = {},
    onSelectWallpaperPreset: (String) -> Unit = {},
    onPickCustomWallpaper: (Uri) -> Unit = {},
    onClearCustomWallpaper: () -> Unit = {},
    onWallpaperDimChange: (Float) -> Unit = {},
    onUploadSpaceAvatar: (Uri) -> Unit = {},
    onUpdateSpaceSettings: (String, String, Boolean, Long, Boolean, Boolean) -> Unit = { _, _, _, _, _, _ -> },
    onRegenerateInviteCode: () -> Unit = {},
    onAddMember: (String) -> Unit = {},
    onRemoveMember: (String) -> Unit = {},
    onPromoteMember: (String) -> Unit = {},
    onDemoteMember: (String) -> Unit = {},
    localMediaMap: Map<String, LocalMediaEntity> = emptyMap(),
    pendingMessageIds: Set<String> = emptySet(),
    knownBackupMediaIds: Set<String> = emptySet(),
    isOffline: Boolean = false,
    downloadingMessageId: String? = null,
    onDownloadMedia: (ChatMessage) -> Unit = {}
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val tokens = LocalPrivacyXTokens.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val colorPreset = remember(chatAppearance.colorPresetId) {
        ChatColorPreset.fromId(chatAppearance.colorPresetId)
    }
    val effectiveSentGradient = if (colorPreset == ChatColorPreset.DEFAULT) tokens.sentBubbleGradient else colorPreset.sentGradient
    val effectiveAccent = if (colorPreset == ChatColorPreset.DEFAULT) tokens.accentPrimary else colorPreset.accentColor

    var selectedTopicId by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var editingTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var contextMenuTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var forwardingTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var mediaInfoTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var showTopicDialog by remember { mutableStateOf(false) }
    var showChatInfoSheet by remember { mutableStateOf(false) }
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var sharedSheetInitialTab by remember { mutableStateOf<String?>(null) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var activeMatchCursor by remember { mutableIntStateOf(0) }

    var disappearsMinutes by remember { mutableStateOf<Long?>(null) }
    var scheduledMinutes by remember { mutableStateOf<Long?>(null) }
    var compressUploads by remember { mutableStateOf(true) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onUploadMedia(uri, "", compressUploads)
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onUploadMedia(uri, "", false)
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onUploadMedia(uri, "", false)
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartRecording() else onVoicePermissionDenied()
    }

    val visibleMessages = remember(messages, selectedTopicId) {
        val now = System.currentTimeMillis()
        messages.filter { msg ->
            val topicOk = selectedTopicId.isBlank() || msg.topicId == selectedTopicId
            val notExpired = msg.disappearsAt == null || msg.disappearsAt.toDate().time > now
            val scheduledReady = msg.scheduledFor == null || msg.scheduledFor.toDate().time <= now || msg.senderId == currentUser.userId
            topicOk && notExpired && scheduledReady
        }
    }

    val searchMatchIndices = remember(visibleMessages, searchQuery) {
        val q = searchQuery.trim()
        if (q.isBlank()) emptyList()
        else visibleMessages.mapIndexedNotNull { idx, msg ->
            val matches = msg.text.contains(q, ignoreCase = true) ||
                msg.senderName.contains(q, ignoreCase = true) ||
                msg.fileName.contains(q, ignoreCase = true)
            if (matches) idx else null
        }
    }

    val activeSearchMessageId = searchMatchIndices.getOrNull(activeMatchCursor)?.let { idx ->
        visibleMessages.getOrNull(idx)?.id
    }

    val pinnedMessages = remember(messages) { messages.filter { !it.isDeleted && it.isPinned } }

    LaunchedEffect(searchMatchIndices, activeMatchCursor) {
        val targetIndex = searchMatchIndices.getOrNull(activeMatchCursor)
        if (targetIndex != null) {
            listState.animateScrollToItem(targetIndex)
        }
    }

    LaunchedEffect(visibleMessages.size) {
        if (!isSearchOpen && visibleMessages.isNotEmpty()) {
            listState.animateScrollToItem(visibleMessages.lastIndex)
        }
    }

    val showScrollToBottom by remember {
        derivedStateOf {
            visibleMessages.isNotEmpty() && listState.firstVisibleItemIndex < (visibleMessages.size - 5).coerceAtLeast(0)
        }
    }

    val someoneTyping = remember(members) {
        val cutoff = System.currentTimeMillis() - 6_000L
        members.any { it.userId != currentUser.userId && (it.typingAt?.toDate()?.time ?: 0L) > cutoff }
    }

    val mentionCandidates = remember(draftText, directoryUsers, conversation.memberUids) {
        val lastToken = draftText.substringAfterLast(' ')
        if (conversation.type != "direct" && lastToken.startsWith("@")) {
            val needle = lastToken.drop(1)
            directoryUsers.filter { u ->
                u.userId in conversation.memberUids &&
                    u.userId != currentUser.userId &&
                    (needle.isBlank() || u.username.contains(needle, ignoreCase = true) || u.displayName.contains(needle, ignoreCase = true))
            }.take(5)
        } else emptyList()
    }

    val isMember = currentUser.userId in conversation.memberUids
    val isAdmin = currentUser.userId in conversation.adminUids || conversation.ownerId == currentUser.userId
    val canPost = isMember && (conversation.type != "channel" || isAdmin) && (!conversation.adminsOnlyMessaging || isAdmin)

    ChatWallpaperLayer(
        wallpaperPresetId = chatAppearance.wallpaperPresetId,
        customWallpaperUri = chatAppearance.customWallpaperUri,
        dimAlpha = chatAppearance.wallpaperDimAlpha
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Top Floating Glass Header
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                elevated = true
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp).testTag("chat_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    UserAvatar(
                        name = conversation.title,
                        photoUrl = conversation.photoUrl,
                        size = 38.dp,
                        gradientSeed = conversation.id.hashCode()
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showChatInfoSheet = true }
                    ) {
                        Text(
                            text = conversation.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when {
                                someoneTyping -> "typing..."
                                conversation.type == "direct" -> "Encrypted Direct Chat"
                                conversation.slowModeSeconds > 0L -> "${conversation.memberUids.size} members • Slow ${conversation.slowModeSeconds}s"
                                else -> "${conversation.memberUids.size} members • ${conversation.type}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (someoneTyping) effectiveAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (conversation.type != "channel") {
                        IconButton(
                            onClick = onStartVoiceCall,
                            modifier = Modifier.size(38.dp).testTag("chat_voice_call_button")
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = "Voice Call", tint = effectiveAccent)
                        }
                        IconButton(
                            onClick = onStartVideoCall,
                            modifier = Modifier.size(38.dp).testTag("chat_video_call_button")
                        ) {
                            Icon(Icons.Rounded.Videocam, contentDescription = "Video Call", tint = effectiveAccent)
                        }
                    }
                    IconButton(
                        onClick = {
                            isSearchOpen = !isSearchOpen
                            if (!isSearchOpen) searchQuery = ""
                        },
                        modifier = Modifier.size(38.dp).testTag("chat_search_button")
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search Messages", tint = effectiveAccent)
                    }
                    IconButton(
                        onClick = { showChatInfoSheet = true },
                        modifier = Modifier.size(38.dp).testTag("chat_info_button")
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = "Chat Info & Manage")
                    }
                }
            }

            if (isOffline) {
                Surface(
                    color = tokens.warningAmber.copy(alpha = 0.16f),
                    border = BorderStroke(0.8.dp, tokens.warningAmber.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.CloudOff, contentDescription = "Offline", tint = tokens.warningAmber, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Offline mode — cached messages & queued sending active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Expandable In-Chat Search Bar
            if (isSearchOpen) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    elevated = true
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search", tint = effectiveAccent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text("Search messages, names, files...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = {
                                    searchQuery = it
                                    activeMatchCursor = 0
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(effectiveAccent),
                                modifier = Modifier.fillMaxWidth().testTag("chat_search_input")
                            )
                        }
                        if (searchQuery.isNotBlank()) {
                            Text(
                                text = if (searchMatchIndices.isEmpty()) "0/0" else "${activeMatchCursor + 1}/${searchMatchIndices.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = effectiveAccent
                            )
                            IconButton(
                                onClick = {
                                    if (searchMatchIndices.isNotEmpty()) {
                                        activeMatchCursor = (activeMatchCursor - 1 + searchMatchIndices.size) % searchMatchIndices.size
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous Match")
                            }
                            IconButton(
                                onClick = {
                                    if (searchMatchIndices.isNotEmpty()) {
                                        activeMatchCursor = (activeMatchCursor + 1) % searchMatchIndices.size
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next Match")
                            }
                        }
                        IconButton(
                            onClick = {
                                isSearchOpen = false
                                searchQuery = ""
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close Search")
                        }
                    }
                }
            }

            // Quick Customization, Media, Pins & AI Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChatActionChip(
                    icon = Icons.Rounded.Palette,
                    label = "Colors & Wallpaper",
                    accent = effectiveAccent,
                    tag = "chat_theme_button",
                    onClick = { showAppearanceSheet = true }
                )
                ChatActionChip(
                    icon = Icons.Rounded.PermMedia,
                    label = "Media & Files",
                    accent = effectiveAccent,
                    tag = "chat_shared_media_button",
                    onClick = { sharedSheetInitialTab = "Media" }
                )
                ChatActionChip(
                    icon = Icons.Rounded.PushPin,
                    label = if (pinnedMessages.isNotEmpty()) "Pinned (${pinnedMessages.size})" else "Pinned",
                    accent = effectiveAccent,
                    tag = "chat_pinned_sheet_button",
                    onClick = { sharedSheetInitialTab = "Pinned" }
                )
                ChatActionChip(
                    icon = Icons.Rounded.AutoAwesome,
                    label = "AI Summary",
                    accent = effectiveAccent,
                    tag = "chat_ai_summary_button",
                    onClick = onSummarizeChat
                )
                if (conversation.type == "group" || conversation.type == "community") {
                    TopicChip("All Topics", selectedTopicId == "", effectiveAccent) { selectedTopicId = "" }
                    topics.forEach { topic ->
                        TopicChip("${topic.iconEmoji} ${topic.title}", selectedTopicId == topic.id, effectiveAccent) {
                            selectedTopicId = topic.id
                        }
                    }
                    TopicChip("+ Topic", false, effectiveAccent) { showTopicDialog = true }
                }
            }

            // Pinned Message Bar
            if (conversation.pinnedMessageText.isNotBlank() || pinnedMessages.isNotEmpty()) {
                val latestPinned = pinnedMessages.lastOrNull()
                val pinPreview = latestPinned?.text?.ifBlank { latestPinned.mediaType } ?: conversation.pinnedMessageText
                Surface(
                    color = tokens.surfaceGlassElevated,
                    border = BorderStroke(0.8.dp, tokens.surfaceBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .clickable {
                            val idx = latestPinned?.let { p -> visibleMessages.indexOfFirst { it.id == p.id } } ?: -1
                            if (idx >= 0) {
                                scope.launch { listState.animateScrollToItem(idx) }
                            } else {
                                sharedSheetInitialTab = "Pinned"
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PushPin, contentDescription = "Pinned", tint = effectiveAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pinned: $pinPreview",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (pinnedMessages.size > 1) "${pinnedMessages.size} pins" else "View",
                            style = MaterialTheme.typography.labelSmall,
                            color = effectiveAccent
                        )
                    }
                }
            }

            // AI Helper Result Card
            if (!aiHelperResult.isNullOrBlank()) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    elevated = true
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI", tint = effectiveAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(aiHelperResult, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = onDismissAiHelper, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }

            // Upload Progress / Retry Bar
            if (uploadState.isUploading || uploadState.errorMessage != null) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uploadState.errorMessage ?: "Uploading ${uploadState.fileName} (${(uploadState.progress * 100).toInt()}%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uploadState.errorMessage != null) tokens.dangerRed else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (uploadState.isUploading) {
                                TextButton(onClick = onCancelUpload) { Text("Cancel") }
                            } else {
                                Row {
                                    TextButton(onClick = onRetryUpload) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Retry")
                                    }
                                    TextButton(onClick = onCancelUpload) { Text("Dismiss") }
                                }
                            }
                        }
                        if (uploadState.isUploading) {
                            LinearProgressIndicator(
                                progress = { uploadState.progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Messages Area + Scroll-to-Bottom Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (visibleMessages.size >= 40) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                TextButton(onClick = onLoadMore) { Text("Load older messages") }
                            }
                        }
                    }

                    itemsIndexed(visibleMessages, key = { _, m -> m.id }) { idx, msg ->
                        val prevDate = visibleMessages.getOrNull(idx - 1)?.createdAt?.toDate()?.let {
                            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(it)
                        }
                        val currDate = msg.createdAt?.toDate()?.let {
                            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(it)
                        } ?: "Today"
                        if (idx == 0 || prevDate != currDate) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = tokens.surfaceGlassElevated,
                                    border = BorderStroke(0.6.dp, tokens.surfaceBorder)
                                ) {
                                    Text(
                                        text = currDate,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        val localMedia = localMediaMap[msg.mediaId.ifBlank { msg.id }] ?: localMediaMap[msg.id]
                        val localFile = localMedia?.localFilePath?.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
                        val nowMs = System.currentTimeMillis()
                        val cloudExpired = msg.mediaExpired || msg.mediaUrl.isBlank() ||
                            (msg.mediaExpiresAt > 0L && msg.mediaExpiresAt <= nowMs)
                        val availability = when {
                            !cloudExpired && localFile != null -> MediaAvailabilityState.CLOUD_AND_LOCAL
                            localFile != null -> MediaAvailabilityState.LOCAL_ONLY
                            !cloudExpired -> MediaAvailabilityState.CLOUD_ONLY
                            msg.mediaId.isNotBlank() && msg.mediaId in knownBackupMediaIds -> MediaAvailabilityState.BACKUP_ONLY
                            else -> MediaAvailabilityState.MISSING
                        }
                        val resolvedMediaUri = when {
                            localFile != null -> Uri.fromFile(localFile).toString()
                            !cloudExpired -> msg.mediaUrl
                            else -> ""
                        }
                        val expiresBadge = when {
                            localFile != null && cloudExpired -> "Saved Locally"
                            msg.mediaExpiresAt > nowMs -> {
                                val daysLeft = TimeUnit.MILLISECONDS.toDays(msg.mediaExpiresAt - nowMs).coerceAtLeast(1L)
                                if (localFile != null) "Local • ${daysLeft}d cloud" else "${daysLeft}d cloud"
                            }
                            availability == MediaAvailabilityState.BACKUP_ONLY -> "In Backup"
                            else -> ""
                        }

                        MessageBubbleItem(
                            message = msg,
                            isMine = msg.senderId == currentUser.userId,
                            currentUserId = currentUser.userId,
                            showSenderName = conversation.type != "direct" && msg.senderId != currentUser.userId,
                            sentBubbleGradient = effectiveSentGradient,
                            accentColor = effectiveAccent,
                            isSearchHighlight = msg.id == activeSearchMessageId || (searchQuery.isNotBlank() && msg.text.contains(searchQuery.trim(), ignoreCase = true)),
                            playbackState = playbackState,
                            resolvedMediaUri = resolvedMediaUri,
                            availabilityState = availability,
                            expiresBadge = expiresBadge,
                            isPendingOffline = msg.id in pendingMessageIds,
                            isSavedToDevice = localMedia?.downloadedToDevice == true,
                            isDownloading = downloadingMessageId == msg.id,
                            onTap = { contextMenuTarget = msg },
                            onDoubleTapHeart = { onToggleReaction(msg, "❤️") },
                            onSwipeReply = { replyTarget = msg },
                            onLongPress = { contextMenuTarget = msg },
                            onReactionTap = { emoji -> onToggleReaction(msg, emoji) },
                            onVotePoll = { opt -> onVotePoll(msg, opt) },
                            onTogglePlayVoice = { if (resolvedMediaUri.isNotBlank()) onTogglePlayVoice(resolvedMediaUri) },
                            onCycleVoiceSpeed = onCycleVoiceSpeed,
                            onOpenImage = { if (resolvedMediaUri.isNotBlank()) fullScreenImageUrl = resolvedMediaUri },
                            onOpenDocument = { url ->
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
                            onDownloadMedia = { onDownloadMedia(msg) },
                            onShowMediaInfo = { mediaInfoTarget = msg }
                        )
                    }
                }

                if (showScrollToBottom) {
                    Surface(
                        shape = CircleShape,
                        color = effectiveAccent,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable {
                                scope.launch {
                                    if (visibleMessages.isNotEmpty()) listState.animateScrollToItem(visibleMessages.lastIndex)
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Scroll to Bottom", tint = Color.White)
                        }
                    }
                }
            }

            // @Mention Autocomplete Row
            if (mentionCandidates.isNotEmpty() && canPost) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mentionCandidates.forEach { user ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = tokens.surfaceGlassElevated,
                            border = BorderStroke(1.dp, effectiveAccent.copy(alpha = 0.45f)),
                            modifier = Modifier.clickable {
                                val prefix = draftText.substringBeforeLast('@', "")
                                onDraftChange("$prefix@${user.username} ")
                            }
                        ) {
                            Text(
                                text = "@${user.username} (${user.displayName})",
                                style = MaterialTheme.typography.labelMedium,
                                color = effectiveAccent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Smart Replies Bar
            if (smartReplies.isNotEmpty() && canPost) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    smartReplies.forEach { reply ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = tokens.surfaceGlassElevated,
                            border = BorderStroke(1.dp, effectiveAccent.copy(alpha = 0.4f)),
                            modifier = Modifier.clickable {
                                onSendMessage(reply, replyTarget, selectedTopicId, disappearsMinutes, scheduledMinutes)
                                replyTarget = null
                            }
                        ) {
                            Text(
                                text = reply,
                                style = MaterialTheme.typography.labelMedium,
                                color = effectiveAccent,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Reply / Edit / Disappearing / Scheduled Active Banner
            if (replyTarget != null || editingTarget != null || disappearsMinutes != null || scheduledMinutes != null) {
                Surface(
                    color = tokens.surfaceGlassElevated,
                    border = BorderStroke(0.8.dp, tokens.surfaceBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val label = when {
                            editingTarget != null -> "Editing: ${editingTarget!!.text.take(40)}"
                            replyTarget != null -> "Replying to ${replyTarget!!.senderName}: ${replyTarget!!.text.ifBlank { replyTarget!!.mediaType }.take(40)}"
                            disappearsMinutes != null -> "Disappearing timer: ${disappearsMinutes}m"
                            else -> "Scheduled in ${scheduledMinutes}m"
                        }
                        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                replyTarget = null
                                editingTarget = null
                                disappearsMinutes = null
                                scheduledMinutes = null
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                }
            }

            // Composer Area
            if (!canPost) {
                Surface(
                    color = tokens.surfaceGlassElevated,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    val lockReason = when {
                        !isMember -> "Join this ${conversation.type} to send messages"
                        conversation.type == "channel" -> "Read-only channel (only admins can broadcast)"
                        else -> "Only admins can send messages in this ${conversation.type}"
                    }
                    Text(
                        text = lockReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else if (recordingState.isRecording) {
                VoiceRecordingComposerBar(
                    recordingState = recordingState,
                    onTogglePause = onPauseRecording,
                    onCancel = onCancelRecording,
                    onSend = onSendRecording
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = tokens.surfaceGlassElevated,
                    border = BorderStroke(tokens.borderWidth, tokens.surfaceBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showAttachmentSheet = true },
                            modifier = Modifier.testTag("chat_attach_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Attachments", tint = effectiveAccent)
                        }

                        Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            if (draftText.isEmpty()) {
                                Text(
                                    text = if (slowModeRemainingSeconds > 0L) "Slow mode: ${slowModeRemainingSeconds}s..." else "Type your message...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            BasicTextField(
                                value = draftText,
                                onValueChange = {
                                    onDraftChange(it)
                                    onTyping()
                                },
                                maxLines = 4,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(effectiveAccent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("chat_message_input")
                            )
                        }

                        if (aiConsentGranted) {
                            IconButton(onClick = onGenerateSmartReplies, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = "Smart Replies", tint = effectiveAccent, modifier = Modifier.size(18.dp))
                            }
                        }

                        if (slowModeRemainingSeconds > 0L) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = tokens.warningAmber.copy(alpha = 0.2f),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = "${slowModeRemainingSeconds}s",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = tokens.warningAmber,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        } else if (draftText.isBlank()) {
                            IconButton(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) onStartRecording()
                                    else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                modifier = Modifier.testTag("chat_record_voice_button")
                            ) {
                                Icon(Icons.Rounded.Mic, contentDescription = "Record Voice", tint = effectiveAccent)
                            }
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = effectiveAccent,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        val edit = editingTarget
                                        if (edit != null) {
                                            onEditMessage(edit, draftText)
                                            editingTarget = null
                                            onDraftChange("")
                                        } else {
                                            onSendMessage(
                                                draftText,
                                                replyTarget,
                                                selectedTopicId,
                                                disappearsMinutes,
                                                scheduledMinutes
                                            )
                                            replyTarget = null
                                            disappearsMinutes = null
                                            scheduledMinutes = null
                                        }
                                    }
                                    .testTag("chat_send_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(19.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Message Action Popover
    contextMenuTarget?.let { target ->
        val hasDownloadable = target.mediaType in setOf("image", "voice", "document", "video") && !target.isDeleted
        MessageActionPopoverDialog(
            message = target,
            isMine = target.senderId == currentUser.userId,
            canDownloadMedia = hasDownloadable,
            onDismiss = { contextMenuTarget = null },
            onReact = { emoji ->
                onToggleReaction(target, emoji)
                contextMenuTarget = null
            },
            onReply = {
                replyTarget = target
                contextMenuTarget = null
            },
            onForward = {
                forwardingTarget = target
                contextMenuTarget = null
            },
            onCopy = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                cm?.setPrimaryClip(ClipData.newPlainText("PrivacyX Message", target.text))
                contextMenuTarget = null
            },
            onPin = {
                onTogglePin(target)
                contextMenuTarget = null
            },
            onDownload = {
                onDownloadMedia(target)
                contextMenuTarget = null
            },
            onTranslate = {
                onTranslateMessage(target.text)
                contextMenuTarget = null
            },
            onBookmark = {
                onBookmarkMessage(target)
                contextMenuTarget = null
            },
            onEdit = {
                editingTarget = target
                onDraftChange(target.text)
                contextMenuTarget = null
            },
            onDelete = {
                onDeleteMessage(target)
                contextMenuTarget = null
            }
        )
    }

    if (showAttachmentSheet) {
        ChatAttachmentSheetDialog(
            compressImages = compressUploads,
            onToggleCompress = { compressUploads = it },
            onPickImage = {
                showAttachmentSheet = false
                mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onPickVideo = {
                showAttachmentSheet = false
                videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
            onPickDocument = {
                showAttachmentSheet = false
                documentPicker.launch(arrayOf("application/pdf", "text/plain", "application/zip", "application/msword", "*/*"))
            },
            onCreatePoll = {
                showAttachmentSheet = false
                showPollDialog = true
            },
            onSetDisappearing = { mins ->
                disappearsMinutes = mins
                showAttachmentSheet = false
            },
            onSetScheduled = { mins ->
                scheduledMinutes = mins
                showAttachmentSheet = false
            },
            onSendSticker = { sticker ->
                onSendSticker(sticker)
                showAttachmentSheet = false
            },
            onDismiss = { showAttachmentSheet = false }
        )
    }

    if (showPollDialog) {
        CreatePollDialog(
            onDismiss = { showPollDialog = false },
            onCreate = { q, opts ->
                showPollDialog = false
                onCreatePoll(q, opts)
            }
        )
    }

    if (showTopicDialog) {
        CreateTopicDialog(
            onDismiss = { showTopicDialog = false },
            onCreate = { title, emoji ->
                showTopicDialog = false
                onCreateTopic(title, emoji)
            }
        )
    }

    forwardingTarget?.let { msg ->
        ForwardConversationDialog(
            conversations = allConversations.filter { it.id != conversation.id },
            onDismiss = { forwardingTarget = null },
            onSelect = { dest ->
                onForwardMessage(dest, msg)
                forwardingTarget = null
            }
        )
    }

    fullScreenImageUrl?.let { url ->
        FullScreenZoomableImageDialog(imageUrl = url, onDismiss = { fullScreenImageUrl = null })
    }

    mediaInfoTarget?.let { target ->
        val local = localMediaMap[target.mediaId.ifBlank { target.id }] ?: localMediaMap[target.id]
        val hasLocal = local != null && File(local.localFilePath).exists()
        val fmt = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
        val createdStr = target.createdAt?.toDate()?.let { fmt.format(it) } ?: "Unknown"
        val expiryStr = when {
            target.mediaExpired -> "Expired (cloud copy deleted after 7 days)"
            target.mediaExpiresAt > 0L -> "Cloud auto-deletes on ${fmt.format(Date(target.mediaExpiresAt))}"
            else -> "7-day retention policy"
        }
        AlertDialog(
            onDismissRequest = { mediaInfoTarget = null },
            title = { Text("Media Availability & Retention") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Created: $createdStr", style = MaterialTheme.typography.bodySmall)
                    Text(expiryStr, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = if (hasLocal) "Local Copy: Saved permanently on device" else "Local Copy: Not downloaded yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasLocal) tokens.onlineGreen else tokens.warningAmber
                    )
                    if (target.mediaId in knownBackupMediaIds) {
                        Text("Backup Manifest: Included in local/Drive backup", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                if (!target.mediaExpired && !hasLocal) {
                    TextButton(onClick = {
                        onDownloadMedia(target)
                        mediaInfoTarget = null
                    }) { Text("Save Locally") }
                }
                TextButton(onClick = { mediaInfoTarget = null }) { Text("Close") }
            }
        )
    }

    if (showAppearanceSheet) {
        ChatAppearanceSheetDialog(
            appearance = chatAppearance,
            onSelectColorPreset = onSelectColorPreset,
            onSelectWallpaperPreset = onSelectWallpaperPreset,
            onPickCustomWallpaper = onPickCustomWallpaper,
            onClearCustomWallpaper = onClearCustomWallpaper,
            onDimChange = onWallpaperDimChange,
            onDismiss = { showAppearanceSheet = false }
        )
    }

    sharedSheetInitialTab?.let { startTab ->
        ChatSharedMediaAndPinnedSheetDialog(
            messages = visibleMessages,
            playbackState = playbackState,
            localMediaMap = localMediaMap,
            knownBackupMediaIds = knownBackupMediaIds,
            initialTab = startTab,
            onJumpToMessage = { msg ->
                sharedSheetInitialTab = null
                val idx = visibleMessages.indexOfFirst { it.id == msg.id }
                if (idx >= 0) scope.launch { listState.animateScrollToItem(idx) }
            },
            onTogglePin = onTogglePin,
            onTogglePlayVoice = onTogglePlayVoice,
            onOpenImage = { fullScreenImageUrl = it },
            onOpenDocument = { url ->
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            },
            onDownloadMedia = onDownloadMedia,
            onDismiss = { sharedSheetInitialTab = null }
        )
    }

    if (showChatInfoSheet) {
        SpaceInfoAndManagementSheetDialog(
            conversation = conversation,
            currentUserId = currentUser.userId,
            isMuted = isMuted,
            directoryUsers = directoryUsers,
            onUploadAvatar = onUploadSpaceAvatar,
            onUpdateSettings = onUpdateSpaceSettings,
            onRegenerateInviteCode = onRegenerateInviteCode,
            onAddMember = onAddMember,
            onRemoveMember = onRemoveMember,
            onPromoteMember = onPromoteMember,
            onDemoteMember = onDemoteMember,
            onToggleMute = onToggleMute,
            onOpenSearch = { isSearchOpen = true },
            onOpenAppearance = { showAppearanceSheet = true },
            onOpenSharedMedia = { sharedSheetInitialTab = "Media" },
            onStartVoiceCall = onStartVoiceCall,
            onStartVideoCall = onStartVideoCall,
            onLeave = {
                showChatInfoSheet = false
                onLeaveConversation()
            },
            onReport = { reason ->
                showChatInfoSheet = false
                onReportConversation(reason, "Reported from chat info")
            },
            onDismiss = { showChatInfoSheet = false }
        )
    }
}

@Composable
private fun ChatActionChip(
    icon: ImageVector,
    label: String,
    accent: Color,
    tag: String,
    onClick: () -> Unit
) {
    val tokens = LocalPrivacyXTokens.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = tokens.surfaceGlassElevated,
        border = BorderStroke(0.8.dp, tokens.surfaceBorder),
        modifier = Modifier.clickable(onClick = onClick).testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun TopicChip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val tokens = LocalPrivacyXTokens.current
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) accent else tokens.surfaceGlassElevated,
        border = BorderStroke(0.8.dp, tokens.surfaceBorder),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubbleItem(
    message: ChatMessage,
    isMine: Boolean,
    currentUserId: String,
    showSenderName: Boolean,
    sentBubbleGradient: List<Color>,
    accentColor: Color,
    isSearchHighlight: Boolean,
    playbackState: VoicePlaybackState,
    resolvedMediaUri: String,
    availabilityState: MediaAvailabilityState,
    expiresBadge: String,
    isPendingOffline: Boolean,
    isSavedToDevice: Boolean,
    isDownloading: Boolean,
    onTap: () -> Unit,
    onDoubleTapHeart: () -> Unit,
    onSwipeReply: () -> Unit,
    onLongPress: () -> Unit,
    onReactionTap: (String) -> Unit,
    onVotePoll: (String) -> Unit,
    onTogglePlayVoice: () -> Unit,
    onCycleVoiceSpeed: () -> Unit,
    onOpenImage: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onDownloadMedia: () -> Unit,
    onShowMediaInfo: () -> Unit
) {
    val tokens = LocalPrivacyXTokens.current
    var swipeOffsetPx by remember(message.id) { mutableFloatStateOf(0f) }

    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isMine) 20.dp else 6.dp,
        bottomEnd = if (isMine) 6.dp else 20.dp
    )
    val timeText = remember(message.createdAt) {
        message.createdAt?.toDate()?.let {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(it)
        } ?: ""
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        val next = (swipeOffsetPx + dragAmount).coerceIn(0f, 150f)
                        if (next > 0f || dragAmount > 0f) {
                            swipeOffsetPx = next
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        if (swipeOffsetPx >= 75f) onSwipeReply()
                        swipeOffsetPx = 0f
                    },
                    onDragCancel = { swipeOffsetPx = 0f }
                )
            }
    ) {
        if (swipeOffsetPx > 14f) {
            Surface(
                shape = CircleShape,
                color = accentColor.copy(alpha = 0.22f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.Reply, contentDescription = "Swipe to reply", tint = accentColor, modifier = Modifier.size(18.dp))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeOffsetPx.roundToInt(), 0) },
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .clip(bubbleShape)
                    .then(
                        if (isMine) Modifier.background(Brush.linearGradient(sentBubbleGradient))
                        else Modifier.background(tokens.receivedBubbleColor)
                    )
                    .then(
                        if (isSearchHighlight) Modifier.border(2.dp, Color(0xFFFACC15), bubbleShape)
                        else Modifier
                    )
                    .combinedClickable(
                        onClick = onTap,
                        onLongClick = onLongPress,
                        onDoubleClick = onDoubleTapHeart
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("message_bubble_${message.id}")
            ) {
                val fgColor = if (isMine) tokens.sentBubbleText else tokens.receivedBubbleText
                Column {
                    if (showSenderName) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    if (message.forwardedFrom.isNotBlank()) {
                        Text(
                            text = "Forwarded from ${message.forwardedFrom}",
                            style = MaterialTheme.typography.labelSmall,
                            color = fgColor.copy(alpha = 0.78f)
                        )
                        Spacer(Modifier.height(2.dp))
                    }
                    if (message.replyToPreview.isNotBlank()) {
                        Surface(
                            color = fgColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = message.replyToPreview,
                                style = MaterialTheme.typography.bodySmall,
                                color = fgColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    when (message.mediaType) {
                        "image" -> {
                            if (resolvedMediaUri.isNotBlank()) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    AsyncImage(
                                        model = resolvedMediaUri,
                                        contentDescription = "Shared photo",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(190.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable(onClick = onOpenImage)
                                    )
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (expiresBadge.isNotBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = Color.Black.copy(alpha = 0.62f),
                                                modifier = Modifier.clickable(onClick = onShowMediaInfo)
                                            ) {
                                                Text(
                                                    text = expiresBadge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.62f),
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clickable(enabled = !isDownloading, onClick = onDownloadMedia)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                if (isDownloading) {
                                                    CircularProgressIndicator(
                                                        color = Color.White,
                                                        strokeWidth = 2.dp,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = if (isSavedToDevice) Icons.Rounded.CloudDone else Icons.Rounded.Download,
                                                        contentDescription = "Download Photo",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            } else {
                                Surface(
                                    color = fgColor.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onShowMediaInfo)
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.CloudOff, contentDescription = "Unavailable", tint = fgColor.copy(alpha = 0.78f))
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = if (availabilityState == MediaAvailabilityState.BACKUP_ONLY) "Available in Backup" else "Media unavailable",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = fgColor
                                            )
                                            Text(
                                                text = "Cloud copy expired after 7 days",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = fgColor.copy(alpha = 0.72f)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                        "video" -> {
                            if (resolvedMediaUri.isNotBlank()) {
                                AndroidView(
                                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)),
                                    factory = { context ->
                                        VideoView(context).apply {
                                            setMediaController(MediaController(context).also { it.setAnchorView(this) })
                                            setVideoURI(Uri.parse(resolvedMediaUri))
                                            setOnPreparedListener { it.start() }
                                        }
                                    }
                                )
                            } else {
                                Text("Video unavailable", style = MaterialTheme.typography.bodySmall, color = fgColor)
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        "voice" -> {
                            if (resolvedMediaUri.isNotBlank()) {
                                val isPlayingThis = playbackState.activeUrl == resolvedMediaUri && playbackState.isPlaying
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = onTogglePlayVoice, modifier = Modifier.size(34.dp)) {
                                        Icon(
                                            imageVector = if (isPlayingThis) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = "Play Voice",
                                            tint = fgColor
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        LinearProgressIndicator(
                                            progress = {
                                                if (playbackState.activeUrl == resolvedMediaUri) playbackState.progressFraction else 0f
                                            },
                                            color = fgColor,
                                            trackColor = fgColor.copy(alpha = 0.25f)
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "${(message.durationMs / 1000L).coerceAtLeast(1)}s voice",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = fgColor.copy(alpha = 0.85f)
                                            )
                                            if (expiresBadge.isNotBlank()) {
                                                Text(
                                                    text = "• $expiresBadge",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = fgColor.copy(alpha = 0.72f),
                                                    modifier = Modifier.clickable(onClick = onShowMediaInfo)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = fgColor.copy(alpha = 0.18f),
                                        modifier = Modifier.clickable(onClick = onCycleVoiceSpeed)
                                    ) {
                                        Text(
                                            text = "${playbackState.speed}x",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = fgColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = onDownloadMedia,
                                        enabled = !isDownloading,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isSavedToDevice) Icons.Rounded.CloudDone else Icons.Rounded.Download,
                                            contentDescription = "Download Voice Note",
                                            tint = fgColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    color = fgColor.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onShowMediaInfo)
                                ) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.CloudOff, contentDescription = "Unavailable", tint = fgColor.copy(alpha = 0.78f))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (availabilityState == MediaAvailabilityState.BACKUP_ONLY) {
                                                "Voice note available in Backup"
                                            } else {
                                                "Voice note unavailable (7-day expired)"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = fgColor
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        "document" -> {
                            Surface(
                                color = fgColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (resolvedMediaUri.isNotBlank()) onOpenDocument(resolvedMediaUri)
                                        else onShowMediaInfo()
                                    }
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Description, contentDescription = "Document", tint = fgColor)
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = message.fileName.ifBlank { "Attachment" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = fgColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (resolvedMediaUri.isNotBlank()) {
                                                "${(message.fileSizeBytes / 1024L).coerceAtLeast(1)} KB"
                                            } else {
                                                "Unavailable after 7 days"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = fgColor.copy(alpha = 0.78f)
                                        )
                                    }
                                    if (resolvedMediaUri.isNotBlank()) {
                                        IconButton(onClick = onDownloadMedia, modifier = Modifier.size(30.dp)) {
                                            Icon(
                                                imageVector = if (isSavedToDevice) Icons.Rounded.CloudDone else Icons.Rounded.Download,
                                                contentDescription = "Save Document",
                                                tint = fgColor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        "poll" -> {
                            Text(
                                text = message.pollQuestion,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = fgColor
                            )
                            Spacer(Modifier.height(6.dp))
                            val totalVotes = message.pollVotes.values.sumOf { it.size }.coerceAtLeast(1)
                            message.pollOptions.forEach { opt ->
                                val voters = message.pollVotes[opt].orEmpty()
                                val voted = currentUserId in voters
                                Surface(
                                    color = if (voted) fgColor.copy(alpha = 0.28f) else fgColor.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .clickable { onVotePoll(opt) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(opt, style = MaterialTheme.typography.bodySmall, color = fgColor)
                                        Text("${voters.size} (${(voters.size * 100) / totalVotes}%)", style = MaterialTheme.typography.labelSmall, color = fgColor)
                                    }
                                }
                            }
                        }
                    }

                    if (message.text.isNotBlank() && message.mediaType != "poll") {
                        Text(
                            text = message.text,
                            style = if (message.mediaType == "sticker") MaterialTheme.typography.displayMedium else MaterialTheme.typography.bodyMedium,
                            color = fgColor
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (message.isPinned) {
                            Icon(Icons.Rounded.PushPin, contentDescription = "Pinned message", tint = fgColor.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                        }
                        if (message.disappearsAt != null) {
                            Icon(Icons.Rounded.Timer, contentDescription = "Disappearing", tint = fgColor.copy(alpha = 0.75f), modifier = Modifier.size(12.dp))
                        }
                        if (message.editedAt != null) {
                            Text("edited", style = MaterialTheme.typography.labelSmall, color = fgColor.copy(alpha = 0.72f))
                        }
                        Text(
                            text = if (isPendingOffline) "Queued" else timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = fgColor.copy(alpha = 0.78f)
                        )
                        if (isMine) {
                            if (isPendingOffline) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = "Queued offline",
                                    tint = fgColor.copy(alpha = 0.78f),
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                val readByOthers = message.readBy.any { it != currentUserId }
                                Icon(
                                    imageVector = Icons.Rounded.DoneAll,
                                    contentDescription = "Receipt",
                                    tint = if (readByOthers) Color(0xFF6EE7B7) else fgColor.copy(alpha = 0.78f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (message.reactions.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    message.reactions.forEach { (emoji, users) ->
                        if (users.isNotEmpty()) {
                            val reactedByMe = currentUserId in users
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (reactedByMe) accentColor.copy(alpha = 0.24f) else tokens.surfaceGlassElevated,
                                border = BorderStroke(0.8.dp, tokens.surfaceBorder),
                                modifier = Modifier.clickable { onReactionTap(emoji) }
                            ) {
                                Text(
                                    text = "$emoji ${users.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
