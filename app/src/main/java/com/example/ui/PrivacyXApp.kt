package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.NotificationHelper
import com.example.data.model.Conversation
import com.example.data.model.UserProfile
import com.example.ui.components.AdaptiveNavRail
import com.example.ui.components.FloatingCapsuleNavBar
import com.example.ui.components.MainNavTab
import com.example.ui.components.PrivacyXBackground
import com.example.ui.components.RadialAction
import com.example.ui.components.RadialFabMenuOverlay
import com.example.ui.screens.ActiveCallOverlay
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.AuthSignInScreen
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatsDashboardScreen
import com.example.ui.screens.CommunitiesScreen
import com.example.ui.screens.DiscoverAndSocialScreen
import com.example.ui.screens.IncomingCallBanner
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ProfileAndSettingsScreen
import com.example.ui.screens.SavedMessagesScreen
import com.example.ui.screens.StorageAndBackupScreen
import com.example.ui.screens.StoriesScreen
import com.example.ui.screens.SuperAdminDashboardScreen
import com.example.ui.screens.UsernameOnboardingScreen
import com.example.ui.theme.PrivacyXTheme
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.AiViewModel
import com.example.ui.viewmodel.AppViewModel
import com.example.ui.viewmodel.AuthGateState
import com.example.ui.viewmodel.CallViewModel
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.SocialViewModel
import kotlinx.coroutines.launch

sealed interface SecondaryDestination {
    data object None : SecondaryDestination
    data class ChatDetail(val conversationId: String, val isPublicPreview: Boolean = false) : SecondaryDestination
    data object AiAssistant : SecondaryDestination
    data object Notifications : SecondaryDestination
    data object SavedMessages : SecondaryDestination
    data object StorageAndBackup : SecondaryDestination
    data object SuperAdmin : SecondaryDestination
}

private data class PendingCallRequest(
    val targetUids: List<String>,
    val conversationId: String,
    val isVideo: Boolean,
    val spaceTitle: String = ""
)

@Composable
fun PrivacyXApp(
    appViewModel: AppViewModel = viewModel(),
    chatViewModel: ChatViewModel = viewModel(),
    socialViewModel: SocialViewModel = viewModel(),
    callViewModel: CallViewModel = viewModel(),
    aiViewModel: AiViewModel = viewModel(),
    adminViewModel: AdminViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val localThemePrefs by appViewModel.localThemePrefs.collectAsStateWithLifecycle()
    val authGate by appViewModel.authGate.collectAsStateWithLifecycle()
    val userSettings by appViewModel.userSettings.collectAsStateWithLifecycle()
    val privateAccount by appViewModel.privateAccount.collectAsStateWithLifecycle()
    val sessions by appViewModel.sessions.collectAsStateWithLifecycle()
    val notifications by appViewModel.notifications.collectAsStateWithLifecycle()
    val following by appViewModel.following.collectAsStateWithLifecycle()
    val followers by appViewModel.followers.collectAsStateWithLifecycle()
    val blockedUsers by appViewModel.blockedUsers.collectAsStateWithLifecycle()
    val announcements by appViewModel.announcements.collectAsStateWithLifecycle()
    val platformSettings by appViewModel.platformSettings.collectAsStateWithLifecycle()
    val directoryUsers by appViewModel.directoryUsers.collectAsStateWithLifecycle()
    val isBusy by appViewModel.isBusy.collectAsStateWithLifecycle()
    val statusBanner by appViewModel.statusBanner.collectAsStateWithLifecycle()
    val isRadialMenuOpen by appViewModel.isRadialMenuOpen.collectAsStateWithLifecycle()
    val storageStats by appViewModel.storageStats.collectAsStateWithLifecycle()
    val backupPreferences by appViewModel.backupPreferences.collectAsStateWithLifecycle()
    val backupHistory by appViewModel.backupHistory.collectAsStateWithLifecycle()
    val driveBackups by appViewModel.driveBackups.collectAsStateWithLifecycle()
    val isBackupBusy by appViewModel.isBackupBusy.collectAsStateWithLifecycle()
    val pendingDriveConsent by appViewModel.pendingDriveConsent.collectAsStateWithLifecycle()

    val myConversations by chatViewModel.myConversations.collectAsStateWithLifecycle()
    val publicConversations by chatViewModel.publicConversations.collectAsStateWithLifecycle()
    val activeConversation by chatViewModel.activeConversation.collectAsStateWithLifecycle()
    val messages by chatViewModel.messages.collectAsStateWithLifecycle()
    val localMediaMap by chatViewModel.localMediaMap.collectAsStateWithLifecycle()
    val pendingMessageIds by chatViewModel.pendingMessageIds.collectAsStateWithLifecycle()
    val knownBackupMediaIds by chatViewModel.knownBackupMediaIds.collectAsStateWithLifecycle()
    val isOffline by chatViewModel.isOffline.collectAsStateWithLifecycle()
    val downloadingMessageId by chatViewModel.downloadingMessageId.collectAsStateWithLifecycle()
    val allMembers by chatViewModel.allMembers.collectAsStateWithLifecycle()
    val myMemberState by chatViewModel.myMemberState.collectAsStateWithLifecycle()
    val topics by chatViewModel.topics.collectAsStateWithLifecycle()
    val draftText by chatViewModel.draftText.collectAsStateWithLifecycle()
    val uploadState by chatViewModel.uploadState.collectAsStateWithLifecycle()
    val aiChatHelperResult by chatViewModel.aiChatHelperResult.collectAsStateWithLifecycle()
    val smartReplies by chatViewModel.smartReplies.collectAsStateWithLifecycle()
    val recordingState by chatViewModel.recordingState.collectAsStateWithLifecycle()
    val playbackState by chatViewModel.playbackState.collectAsStateWithLifecycle()
    val chatAppearance by chatViewModel.chatAppearance.collectAsStateWithLifecycle()
    val slowModeRemaining by chatViewModel.slowModeRemainingSeconds.collectAsStateWithLifecycle()
    val chatNotice by chatViewModel.chatNotice.collectAsStateWithLifecycle()

    val activeStories by socialViewModel.activeStories.collectAsStateWithLifecycle()
    val myStories by socialViewModel.myStories.collectAsStateWithLifecycle()
    val storyViewers by socialViewModel.storyViewers.collectAsStateWithLifecycle()
    val posts by socialViewModel.posts.collectAsStateWithLifecycle()
    val postComments by socialViewModel.postComments.collectAsStateWithLifecycle()
    val savedMessages by socialViewModel.savedMessages.collectAsStateWithLifecycle()
    val isUploadingStoryOrPost by socialViewModel.isUploadingStoryOrPost.collectAsStateWithLifecycle()
    val socialNotice by socialViewModel.socialNotice.collectAsStateWithLifecycle()

    val callHistory by callViewModel.callHistory.collectAsStateWithLifecycle()
    val activeCall by callViewModel.activeCall.collectAsStateWithLifecycle()
    val incomingCall by callViewModel.incomingCall.collectAsStateWithLifecycle()
    val elapsedSeconds by callViewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val webrtcState by callViewModel.webrtcState.collectAsStateWithLifecycle()
    val callNotice by callViewModel.callNotice.collectAsStateWithLifecycle()

    val aiSessions by aiViewModel.sessions.collectAsStateWithLifecycle()
    val activeAiSessionId by aiViewModel.activeSessionId.collectAsStateWithLifecycle()
    val aiMessages by aiViewModel.messages.collectAsStateWithLifecycle()
    val streamingText by aiViewModel.streamingText.collectAsStateWithLifecycle()
    val isStreaming by aiViewModel.isStreaming.collectAsStateWithLifecycle()
    val aiErrorMessage by aiViewModel.errorMessage.collectAsStateWithLifecycle()

    val adminUsers by adminViewModel.users.collectAsStateWithLifecycle()
    val adminConversations by adminViewModel.conversations.collectAsStateWithLifecycle()
    val adminReports by adminViewModel.reports.collectAsStateWithLifecycle()
    val adminLogs by adminViewModel.logs.collectAsStateWithLifecycle()
    val adminPlatformSettings by adminViewModel.platformSettings.collectAsStateWithLifecycle()
    val adminNotice by adminViewModel.adminNotice.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        NotificationHelper.ensureChannels(context)
    }

    LaunchedEffect(statusBanner, chatNotice, socialNotice, callNotice, adminNotice) {
        val msg = statusBanner ?: chatNotice ?: socialNotice ?: callNotice ?: adminNotice
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            appViewModel.clearBanner()
            chatViewModel.clearNotice()
            socialViewModel.clearNotice()
            callViewModel.clearNotice()
            adminViewModel.clearNotice()
        }
    }

    PrivacyXTheme(
        themeId = localThemePrefs.themeId,
        appearanceMode = localThemePrefs.appearanceMode,
        customAccentHex = localThemePrefs.accentColorHex.ifBlank { null },
        reducedMotion = localThemePrefs.reducedMotion
    ) {
        when (val gate = authGate) {
            AuthGateState.Loading -> {
                PrivacyXBackground {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            AuthGateState.SignedOut -> {
                AuthSignInScreen(
                    isBusy = isBusy,
                    errorMessage = statusBanner,
                    onGoogleSignIn = {
                        scope.launch { appViewModel.signInWithGoogle(context) }
                    }
                )
            }

            is AuthGateState.NeedsOnboarding -> {
                UsernameOnboardingScreen(
                    suggestedName = gate.suggestedName,
                    email = gate.email,
                    isBusy = isBusy,
                    errorMessage = statusBanner,
                    onCheckAvailability = { appViewModel.checkUsernameAvailable(it) },
                    onSubmitProfile = { displayName, username, bio ->
                        appViewModel.claimUsername(displayName, username, bio)
                    },
                    onSignOut = { appViewModel.signOut() }
                )
            }

            is AuthGateState.Ready -> {
                val profile = gate.profile
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, gate.uid) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> appViewModel.setOnlinePresence(true)
                            Lifecycle.Event.ON_STOP -> appViewModel.setOnlinePresence(false)
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(gate.uid) {
                    chatViewModel.bindConversationLists(gate.uid)
                    socialViewModel.bindSocialStreams(gate.uid)
                    callViewModel.bindCallHistory(gate.uid)
                    aiViewModel.bindAiSessions(gate.uid)
                }

                var selectedTab by remember { mutableStateOf(MainNavTab.CHATS) }
                var secondary by remember { mutableStateOf<SecondaryDestination>(SecondaryDestination.None) }

                var showNewChatExternal by remember { mutableStateOf(false) }
                var showCreateGroupExternal by remember { mutableStateOf(false) }
                var showCreateCommunityExternal by remember { mutableStateOf(false) }
                var showCreateStoryExternal by remember { mutableStateOf(false) }
                var showDialerExternal by remember { mutableStateOf(false) }
                var dialerVideoDefault by remember { mutableStateOf(false) }
                var openThemeStudioExternal by remember { mutableStateOf(false) }
                var pendingQuickUploadUri by remember { mutableStateOf<Uri?>(null) }
                var pendingCallTarget by remember { mutableStateOf<PendingCallRequest?>(null) }

                val callPermLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { grants ->
                    val target = pendingCallTarget
                    pendingCallTarget = null
                    val audioOk = grants[Manifest.permission.RECORD_AUDIO] == true
                    val videoOk = target?.isVideo != true || grants[Manifest.permission.CAMERA] == true
                    if (target != null && audioOk && videoOk) {
                        callViewModel.startOutgoingCall(
                            callerName = profile.displayName,
                            targetUids = target.targetUids,
                            conversationId = target.conversationId,
                            isVideo = target.isVideo,
                            spaceTitle = target.spaceTitle
                        )
                    } else if (target != null) {
                        appViewModel.postBanner("Microphone/Camera permission required for call.")
                    }
                }

                fun initiateCall(
                    targetUids: List<String>,
                    conversationId: String,
                    isVideo: Boolean,
                    spaceTitle: String = ""
                ) {
                    val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (isVideo) needed.add(Manifest.permission.CAMERA)
                    val missing = needed.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isEmpty()) {
                        callViewModel.startOutgoingCall(
                            callerName = profile.displayName,
                            targetUids = targetUids,
                            conversationId = conversationId,
                            isVideo = isVideo,
                            spaceTitle = spaceTitle
                        )
                    } else {
                        pendingCallTarget = PendingCallRequest(targetUids, conversationId, isVideo, spaceTitle)
                        callPermLauncher.launch(missing.toTypedArray())
                    }
                }

                val quickMediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                    if (uri != null) pendingQuickUploadUri = uri
                }
                val quickDocPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) pendingQuickUploadUri = uri
                }

                var pendingExportPassphrase by remember { mutableStateOf("") }
                var pendingRestorePassphrase by remember { mutableStateOf("") }

                val exportBackupLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/octet-stream")
                ) { uri ->
                    if (uri != null) appViewModel.exportLocalBackupToUri(uri, pendingExportPassphrase)
                }

                val restoreBackupLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) appViewModel.restoreFromLocalBackupUri(uri, pendingRestorePassphrase)
                }

                val driveConsentLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        appViewModel.onDriveConsentIntentResult(result.data)
                    } else {
                        appViewModel.clearPendingDriveConsent()
                        appViewModel.postBanner("Google Drive authorization cancelled")
                    }
                }

                LaunchedEffect(pendingDriveConsent) {
                    val pi = pendingDriveConsent
                    if (pi != null) {
                        driveConsentLauncher.launch(IntentSenderRequest.Builder(pi).build())
                    }
                }

                fun openChat(conv: Conversation, isPublicPreview: Boolean = false) {
                    chatViewModel.openConversation(conv.id, isPublicPreview)
                    secondary = SecondaryDestination.ChatDetail(conv.id, isPublicPreview)
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (val sec = secondary) {
                        SecondaryDestination.None -> {
                            PrivacyXBackground {
                                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                    val useRail = maxWidth >= 700.dp
                                    Scaffold(
                                        containerColor = Color.Transparent
                                    ) { innerPadding ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(innerPadding)
                                        ) {
                                            if (useRail) {
                                                AdaptiveNavRail(
                                                    selectedTab = selectedTab,
                                                    onSelectTab = { selectedTab = it }
                                                )
                                            }
                                            Box(
                                                modifier = Modifier.weight(1f).fillMaxSize(),
                                                contentAlignment = Alignment.TopCenter
                                            ) {
                                                Box(modifier = Modifier.widthIn(max = 860.dp).fillMaxSize()) {
                                                    when (selectedTab) {
                                                        MainNavTab.CHATS -> ChatsDashboardScreen(
                                                            currentUser = profile,
                                                            isSuperAdmin = appViewModel.isSuperAdmin(),
                                                            conversations = myConversations,
                                                            directoryUsers = directoryUsers,
                                                            announcements = announcements,
                                                            unreadNotificationsCount = notifications.count { !it.isRead },
                                                            onSearchQuery = { appViewModel.refreshDirectoryUsers(it) },
                                                            onOpenConversation = { openChat(it) },
                                                            onStartDirectChat = { user ->
                                                                scope.launch {
                                                                    chatViewModel.startDirectChat(user.userId, user.displayName, user.photoUrl)
                                                                        .onSuccess { cid ->
                                                                            chatViewModel.openConversation(cid)
                                                                            secondary = SecondaryDestination.ChatDetail(cid)
                                                                        }
                                                                        .onFailure { appViewModel.postBanner(it.localizedMessage ?: "Failed to start chat") }
                                                                }
                                                            },
                                                            onCreateSpace = { type, title, desc, isPublic, members ->
                                                                scope.launch {
                                                                    chatViewModel.createGroupOrCommunityOrChannel(type, title, desc, isPublic, members)
                                                                        .onSuccess { cid ->
                                                                            chatViewModel.openConversation(cid)
                                                                            secondary = SecondaryDestination.ChatDetail(cid)
                                                                        }
                                                                }
                                                            },
                                                            onOpenNotifications = { secondary = SecondaryDestination.Notifications },
                                                            onOpenAiAssistant = { secondary = SecondaryDestination.AiAssistant },
                                                            onOpenAdminDashboard = {
                                                                adminViewModel.bindAdminDashboard()
                                                                secondary = SecondaryDestination.SuperAdmin
                                                            },
                                                            onOpenProfile = { selectedTab = MainNavTab.PROFILE },
                                                            onSignOut = { appViewModel.signOut() },
                                                            showNewChatDialogExternal = showNewChatExternal,
                                                            showCreateGroupDialogExternal = showCreateGroupExternal,
                                                            showCreateCommunityDialogExternal = showCreateCommunityExternal,
                                                            onDismissExternalDialogs = {
                                                                showNewChatExternal = false
                                                                showCreateGroupExternal = false
                                                                showCreateCommunityExternal = false
                                                            }
                                                        )

                                                        MainNavTab.DISCOVER -> DiscoverAndSocialScreen(
                                                            currentUser = profile,
                                                            directoryUsers = directoryUsers,
                                                            publicConversations = publicConversations,
                                                            posts = posts,
                                                            postComments = postComments,
                                                            following = following,
                                                            blockedUsers = blockedUsers,
                                                            isUploadingPost = isUploadingStoryOrPost,
                                                            onSearchUsers = { appViewModel.refreshDirectoryUsers(it) },
                                                            onLoadUserProfile = { appViewModel.loadUserProfile(it) },
                                                            onStartDirectChat = { user ->
                                                                scope.launch {
                                                                    chatViewModel.startDirectChat(user.userId, user.displayName, user.photoUrl)
                                                                        .onSuccess { cid ->
                                                                            chatViewModel.openConversation(cid)
                                                                            secondary = SecondaryDestination.ChatDetail(cid)
                                                                        }
                                                                }
                                                            },
                                                            onStartCall = { uid, _, isVideo ->
                                                                initiateCall(listOf(uid), "direct_call", isVideo)
                                                            },
                                                            onJoinOrOpenPublicSpace = { space ->
                                                                if (profile.userId !in space.memberUids) {
                                                                    chatViewModel.joinPublicConversation(space.id)
                                                                }
                                                                openChat(space, isPublicPreview = space.type == "channel")
                                                            },
                                                            onToggleFollow = { appViewModel.toggleFollow(it) },
                                                            onToggleBlock = { appViewModel.toggleBlock(it) },
                                                            onSubmitReport = { t, id, r, d -> appViewModel.submitReport(t, id, r, d) },
                                                            onCreatePost = { cap, uri, tag, onDone ->
                                                                socialViewModel.createPost(profile, cap, uri, tag, onDone)
                                                            },
                                                            onTogglePostLike = { socialViewModel.togglePostLike(it) },
                                                            onOpenComments = { socialViewModel.openPostComments(it) },
                                                            onAddComment = { pid, txt -> socialViewModel.addPostComment(pid, profile.displayName, txt) }
                                                        )

                                                        MainNavTab.STORIES -> StoriesScreen(
                                                            currentUser = profile,
                                                            activeStories = activeStories,
                                                            myStories = myStories,
                                                            storyViewers = storyViewers,
                                                            directoryUsers = directoryUsers,
                                                            isUploading = isUploadingStoryOrPost,
                                                            showCreatorExternal = showCreateStoryExternal,
                                                            onDismissExternalCreator = { showCreateStoryExternal = false },
                                                            onOpenStory = { socialViewModel.openStoryAndTrackView(it, profile.displayName) },
                                                            onReactStory = { st, em -> socialViewModel.reactToStory(st, profile.displayName, em) },
                                                            onCreateStory = { cap, uri, grad, priv, allowed, hidden, hi, done ->
                                                                socialViewModel.createTextOrMediaStory(
                                                                    profile, cap, uri, grad, priv, allowed, hidden, hi, done
                                                                )
                                                            },
                                                            onArchiveOrHighlight = { id, arch, hi ->
                                                                socialViewModel.archiveOrHighlightStory(id, arch, hi)
                                                            }
                                                        )

                                                        MainNavTab.COMMUNITIES -> CommunitiesScreen(
                                                            currentUser = profile,
                                                            myConversations = myConversations,
                                                            publicConversations = publicConversations,
                                                            directoryUsers = directoryUsers,
                                                            onOpenConversation = { openChat(it) },
                                                            onJoinConversation = { chatViewModel.joinPublicConversation(it) },
                                                            onCreateSpace = { type, title, desc, isPub, members ->
                                                                scope.launch {
                                                                    chatViewModel.createGroupOrCommunityOrChannel(type, title, desc, isPub, members)
                                                                        .onSuccess { cid ->
                                                                            chatViewModel.openConversation(cid)
                                                                            secondary = SecondaryDestination.ChatDetail(cid)
                                                                        }
                                                                }
                                                            },
                                                            onAddMember = { cid, uid -> chatViewModel.addMember(cid, uid) },
                                                            onRemoveMember = { cid, uid -> chatViewModel.removeMember(cid, uid) },
                                                            onPromoteMember = { cid, uid -> chatViewModel.promoteMember(cid, uid) },
                                                            onDemoteMember = { cid, uid -> chatViewModel.demoteMember(cid, uid) }
                                                        )

                                                        MainNavTab.CALLS -> CallsScreen(
                                                            currentUser = profile,
                                                            callHistory = callHistory,
                                                            directoryUsers = directoryUsers,
                                                            showDialerExternal = showDialerExternal,
                                                            dialerVideoDefault = dialerVideoDefault,
                                                            onDismissExternalDialer = { showDialerExternal = false },
                                                            onStartCall = { uid, _, isVideo ->
                                                                initiateCall(listOf(uid), "direct_call", isVideo)
                                                            }
                                                        )

                                                        MainNavTab.PROFILE -> ProfileAndSettingsScreen(
                                                            profile = profile,
                                                            email = privateAccount?.email.orEmpty(),
                                                            isSuperAdmin = appViewModel.isSuperAdmin(),
                                                            themePrefs = localThemePrefs,
                                                            userSettings = userSettings,
                                                            sessions = sessions,
                                                            blockedUsers = blockedUsers,
                                                            followersCount = followers.size,
                                                            followingCount = following.size,
                                                            openThemeStudioExternal = openThemeStudioExternal,
                                                            onDismissExternalThemeStudio = { openThemeStudioExternal = false },
                                                            onSelectTheme = { appViewModel.updateTheme(themeId = it) },
                                                            onSelectAppearance = { appViewModel.updateTheme(appearanceMode = it) },
                                                            onSelectAccentHex = { appViewModel.updateTheme(accentColorHex = it) },
                                                            onToggleReducedMotion = { appViewModel.updateTheme(reducedMotion = it) },
                                                            onUpdateSettings = { appViewModel.updateUserSettings(it) },
                                                            onUpdateProfile = { name, bio, status, photo, vis, lastSeen, storyPriv, hex ->
                                                                appViewModel.updateProfile(name, bio, status, photo, vis, lastSeen, storyPriv, hex)
                                                            },
                                                            onUploadAvatar = { uri ->
                                                                chatViewModel.mediaService.uploadMedia(uri, compressImage = true)
                                                                    .map { it.secureUrl }
                                                            },
                                                            onRevokeSession = { appViewModel.revokeSession(it) },
                                                            onUnblockUser = { appViewModel.toggleBlock(it) },
                                                            onOpenSavedMessages = { secondary = SecondaryDestination.SavedMessages },
                                                            onOpenAiAssistant = { secondary = SecondaryDestination.AiAssistant },
                                                            onOpenStorageAndBackup = {
                                                                secondary = SecondaryDestination.StorageAndBackup
                                                                if (backupPreferences.driveConnected) {
                                                                    appViewModel.refreshDriveBackups()
                                                                }
                                                            },
                                                            onOpenAdminDashboard = {
                                                                adminViewModel.bindAdminDashboard()
                                                                secondary = SecondaryDestination.SuperAdmin
                                                            },
                                                            onClearCache = { appViewModel.clearLocalCache() },
                                                            onExportData = {
                                                                appViewModel.exportUserDataAndShare { intent ->
                                                                    context.startActivity(intent)
                                                                }
                                                            },
                                                            onDeleteAccount = { appViewModel.deleteAccount() },
                                                            onSignOut = { appViewModel.signOut() }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Floating bottom capsule nav bar on compact screens
                                    if (!useRail) {
                                        FloatingCapsuleNavBar(
                                            selectedTab = selectedTab,
                                            unreadChatsCount = myConversations.count {
                                                it.lastMessageSenderId.isNotBlank() && it.lastMessageSenderId != profile.userId
                                            },
                                            onSelectTab = { selectedTab = it },
                                            modifier = Modifier.align(Alignment.BottomCenter)
                                        )
                                    }

                                    // Signature 3-ring Radial Floating Action Menu
                                    RadialFabMenuOverlay(
                                        isExpanded = isRadialMenuOpen,
                                        reducedMotion = localThemePrefs.reducedMotion,
                                        bottomPaddingDp = if (useRail) 26 else 92,
                                        onToggle = { appViewModel.toggleRadialMenu() },
                                        onDismiss = { appViewModel.toggleRadialMenu(false) },
                                        onActionSelected = { action ->
                                            appViewModel.toggleRadialMenu(false)
                                            when (action) {
                                                RadialAction.NEW_CHAT -> {
                                                    selectedTab = MainNavTab.CHATS
                                                    showNewChatExternal = true
                                                }
                                                RadialAction.CREATE_GROUP -> {
                                                    selectedTab = MainNavTab.CHATS
                                                    showCreateGroupExternal = true
                                                }
                                                RadialAction.CREATE_COMMUNITY -> {
                                                    selectedTab = MainNavTab.CHATS
                                                    showCreateCommunityExternal = true
                                                }
                                                RadialAction.CREATE_STORY -> {
                                                    selectedTab = MainNavTab.STORIES
                                                    showCreateStoryExternal = true
                                                }
                                                RadialAction.VOICE_CALL -> {
                                                    selectedTab = MainNavTab.CALLS
                                                    dialerVideoDefault = false
                                                    showDialerExternal = true
                                                }
                                                RadialAction.VIDEO_CALL -> {
                                                    selectedTab = MainNavTab.CALLS
                                                    dialerVideoDefault = true
                                                    showDialerExternal = true
                                                }
                                                RadialAction.SHARE_IMAGE -> quickMediaPicker.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                                RadialAction.SEND_DOCUMENT -> quickDocPicker.launch(
                                                    arrayOf("application/pdf", "text/plain", "application/zip", "*/*")
                                                )
                                                RadialAction.AI_ASSISTANT -> secondary = SecondaryDestination.AiAssistant
                                                RadialAction.CHANGE_THEME -> {
                                                    selectedTab = MainNavTab.PROFILE
                                                    openThemeStudioExternal = true
                                                }
                                                RadialAction.SAVED_MESSAGES -> secondary = SecondaryDestination.SavedMessages
                                                RadialAction.QUICK_SETTINGS -> selectedTab = MainNavTab.PROFILE
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        is SecondaryDestination.ChatDetail -> {
                            val conv = activeConversation ?: myConversations.firstOrNull { it.id == sec.conversationId }
                            if (conv == null) {
                                PrivacyXBackground {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }
                            } else {
                                ChatDetailScreen(
                                    currentUser = profile,
                                    conversation = conv,
                                    messages = messages,
                                    allConversations = myConversations,
                                    members = allMembers,
                                    topics = topics,
                                    directoryUsers = directoryUsers,
                                    chatAppearance = chatAppearance,
                                    isMuted = myMemberState?.isMuted == true,
                                    slowModeRemainingSeconds = slowModeRemaining,
                                    draftText = draftText,
                                    uploadState = uploadState,
                                    recordingState = recordingState,
                                    playbackState = playbackState,
                                    smartReplies = smartReplies,
                                    aiHelperResult = aiChatHelperResult,
                                    aiConsentGranted = userSettings.aiConsentGranted,
                                    onBack = { secondary = SecondaryDestination.None },
                                    onDraftChange = { chatViewModel.updateDraft(conv.id, it) },
                                    onTyping = { chatViewModel.sendTypingIndicator(conv) },
                                    onLoadMore = { chatViewModel.loadMoreMessages(conv.id, sec.isPublicPreview) },
                                    onSendMessage = { text, replyTo, topicId, disMin, schedMin ->
                                        chatViewModel.sendTextOrMediaMessage(
                                            conversation = conv,
                                            profile = profile,
                                            text = text,
                                            replyTo = replyTo,
                                            topicId = topicId,
                                            disappearsInMinutes = disMin,
                                            scheduledDelayMinutes = schedMin
                                        )
                                    },
                                    onSendSticker = { sticker ->
                                        chatViewModel.sendTextOrMediaMessage(
                                            conversation = conv,
                                            profile = profile,
                                            text = sticker,
                                            mediaType = "sticker"
                                        )
                                    },
                                    onUploadMedia = { uri, cap, compress ->
                                        chatViewModel.uploadAndSendMedia(
                                            uri = uri,
                                            conversation = conv,
                                            profile = profile,
                                            caption = cap,
                                            compressImage = compress,
                                            maxUploadMb = platformSettings.maxUploadMb
                                        )
                                    },
                                    onCancelUpload = { chatViewModel.cancelUpload() },
                                    onRetryUpload = { chatViewModel.retryUpload(profile) },
                                    onStartRecording = { chatViewModel.startVoiceRecording() },
                                    onPauseRecording = { chatViewModel.togglePauseVoiceRecording() },
                                    onCancelRecording = { chatViewModel.cancelVoiceRecording() },
                                    onSendRecording = { chatViewModel.finishAndSendVoiceRecording(conv, profile) },
                                    onVoicePermissionDenied = {
                                        chatViewModel.postNotice("Microphone permission is required for voice messages.")
                                    },
                                    onTogglePlayVoice = { chatViewModel.toggleVoicePlayback(it) },
                                    onCycleVoiceSpeed = { chatViewModel.cycleVoiceSpeed() },
                                    onEditMessage = { msg, txt -> chatViewModel.editMessage(conv.id, msg.id, txt) },
                                    onDeleteMessage = { msg -> chatViewModel.deleteMessage(conv.id, msg.id) },
                                    onToggleReaction = { msg, em -> chatViewModel.toggleReaction(conv.id, msg.id, em) },
                                    onVotePoll = { msg, opt -> chatViewModel.votePoll(conv.id, msg.id, opt) },
                                    onTogglePin = { msg -> chatViewModel.togglePin(conv.id, msg) },
                                    onForwardMessage = { dest, msg -> chatViewModel.forwardMessage(dest, msg, profile) },
                                    onBookmarkMessage = { msg -> chatViewModel.bookmarkMessage(msg) },
                                    onCreatePoll = { q, opts -> chatViewModel.createPoll(conv, profile, q, opts) },
                                    onCreateTopic = { title, emoji -> chatViewModel.createTopic(conv, title, emoji) },
                                    onSummarizeChat = { chatViewModel.summarizeActiveChat(userSettings.aiConsentGranted) },
                                    onTranslateMessage = { txt -> chatViewModel.translateMessage(txt, "English", userSettings.aiConsentGranted) },
                                    onGenerateSmartReplies = { chatViewModel.generateSmartReplies(userSettings.aiConsentGranted) },
                                    onDismissAiHelper = { chatViewModel.clearAiHelper() },
                                    onStartVoiceCall = {
                                        val peers = conv.memberUids.filter { it != profile.userId }
                                        val title = if (conv.type == "direct") "" else conv.title
                                        initiateCall(peers, conv.id, false, title)
                                    },
                                    onStartVideoCall = {
                                        val peers = conv.memberUids.filter { it != profile.userId }
                                        val title = if (conv.type == "direct") "" else conv.title
                                        initiateCall(peers, conv.id, true, title)
                                    },
                                    onToggleMute = { chatViewModel.toggleMuteConversation(conv) },
                                    onLeaveConversation = {
                                        chatViewModel.leaveConversation(conv.id) {
                                            secondary = SecondaryDestination.None
                                        }
                                    },
                                    onReportConversation = { reason, details ->
                                        appViewModel.submitReport("conversation", conv.id, reason, details)
                                    },
                                    onSelectColorPreset = { presetId ->
                                        chatViewModel.selectChatColorPreset(conv, presetId)
                                    },
                                    onSelectWallpaperPreset = { presetId ->
                                        chatViewModel.selectBuiltInWallpaper(conv, presetId)
                                    },
                                    onPickCustomWallpaper = { uri ->
                                        chatViewModel.setCustomGalleryWallpaper(conv, uri)
                                    },
                                    onClearCustomWallpaper = {
                                        chatViewModel.selectBuiltInWallpaper(conv, "default")
                                    },
                                    onWallpaperDimChange = { dimAlpha ->
                                        chatViewModel.updateWallpaperDim(conv.id, dimAlpha)
                                    },
                                    onUploadSpaceAvatar = { uri ->
                                        chatViewModel.uploadAndSetConversationAvatar(conv.id, uri)
                                    },
                                    onUpdateSpaceSettings = { title, desc, isPub, slowSec, approval, adminsOnly ->
                                        chatViewModel.updateConversationSettings(
                                            conversationId = conv.id,
                                            title = title,
                                            description = desc,
                                            isPublic = isPub,
                                            slowModeSeconds = slowSec,
                                            memberApprovalRequired = approval,
                                            adminsOnlyMessaging = adminsOnly
                                        )
                                    },
                                    onRegenerateInviteCode = {
                                        chatViewModel.regenerateInviteCode(conv.id)
                                    },
                                    onAddMember = { uid -> chatViewModel.addMember(conv.id, uid) },
                                    onRemoveMember = { uid -> chatViewModel.removeMember(conv.id, uid) },
                                    onPromoteMember = { uid -> chatViewModel.promoteMember(conv.id, uid) },
                                    onDemoteMember = { uid -> chatViewModel.demoteMember(conv.id, uid) },
                                    localMediaMap = localMediaMap,
                                    pendingMessageIds = pendingMessageIds,
                                    knownBackupMediaIds = knownBackupMediaIds,
                                    isOffline = isOffline,
                                    downloadingMessageId = downloadingMessageId,
                                    onDownloadMedia = { msg -> chatViewModel.downloadMessageMedia(msg) }
                                )
                            }
                        }

                        SecondaryDestination.AiAssistant -> AiAssistantScreen(
                            sessions = aiSessions,
                            activeSessionId = activeAiSessionId,
                            messages = aiMessages,
                            streamingText = streamingText,
                            isStreaming = isStreaming,
                            errorMessage = aiErrorMessage,
                            aiConsentGranted = userSettings.aiConsentGranted,
                            reducedMotion = localThemePrefs.reducedMotion,
                            onBack = { secondary = SecondaryDestination.None },
                            onGrantConsent = {
                                appViewModel.updateUserSettings(userSettings.copy(aiConsentGranted = true))
                            },
                            onSelectSession = { aiViewModel.selectSession(it) },
                            onStartNewSession = { aiViewModel.startNewSession() },
                            onSendPrompt = { p, img ->
                                aiViewModel.sendPrompt(p, img, userSettings.aiConsentGranted)
                            },
                            onStopGeneration = { aiViewModel.cancelStreaming() }
                        )

                        SecondaryDestination.Notifications -> NotificationsScreen(
                            notifications = notifications,
                            onBack = { secondary = SecondaryDestination.None },
                            onMarkRead = { appViewModel.markNotificationRead(it) }
                        )

                        SecondaryDestination.SavedMessages -> SavedMessagesScreen(
                            savedMessages = savedMessages,
                            onBack = { secondary = SecondaryDestination.None },
                            onAddNote = { socialViewModel.saveQuickNote(it) },
                            onDeleteItem = { socialViewModel.deleteSavedMessage(it) }
                        )

                        SecondaryDestination.StorageAndBackup -> StorageAndBackupScreen(
                            storageStats = storageStats,
                            backupPrefs = backupPreferences,
                            driveBackups = driveBackups,
                            backupHistory = backupHistory,
                            isBackupBusy = isBackupBusy,
                            onBack = { secondary = SecondaryDestination.None },
                            onClearTemporaryCache = { appViewModel.clearLocalCache() },
                            onConnectDrive = { appViewModel.connectOrBackupGoogleDrive(backupAfterAuth = false) },
                            onDisconnectDrive = { appViewModel.disconnectDrive() },
                            onRefreshDriveBackups = { appViewModel.refreshDriveBackups() },
                            onBackupNowToDrive = { pw -> appViewModel.connectOrBackupGoogleDrive(pw, backupAfterAuth = true) },
                            onSelectSchedule = { appViewModel.setBackupSchedule(it) },
                            onToggleIncludeMedia = { appViewModel.setIncludeMediaInBackup(it) },
                            onRestoreDriveFile = { file, pw -> appViewModel.restoreFromDriveFile(file, pw) },
                            onExportLocalZip = { pw ->
                                pendingExportPassphrase = pw
                                val ext = if (pw.isNotBlank()) "pxbackup" else "zip"
                                exportBackupLauncher.launch("privacyx_backup_${System.currentTimeMillis()}.$ext")
                            },
                            onRestoreLocalZip = { pw ->
                                pendingRestorePassphrase = pw
                                restoreBackupLauncher.launch(arrayOf("*/*"))
                            }
                        )

                        SecondaryDestination.SuperAdmin -> SuperAdminDashboardScreen(
                            isAuthorizedSuperAdmin = appViewModel.isSuperAdmin(),
                            users = adminUsers,
                            conversations = adminConversations,
                            reports = adminReports,
                            logs = adminLogs,
                            platformSettings = adminPlatformSettings,
                            onBack = { secondary = SecondaryDestination.None },
                            onToggleSuspendUser = { adminViewModel.toggleUserSuspension(it) },
                            onDeleteConversation = { adminViewModel.deleteConversation(it) },
                            onUpdateReportStatus = { id, status -> adminViewModel.updateReportStatus(id, status) },
                            onPublishAnnouncement = { t, b, p -> adminViewModel.publishAnnouncement(t, b, p) },
                            onSavePlatformSettings = { maxMb, reg, maint ->
                                adminViewModel.savePlatformSettings(maxMb, reg, maint)
                            }
                        )
                    }

                    // Destination picker when user initiates Share Image / Send Document from Radial Menu
                    pendingQuickUploadUri?.let { uri ->
                        AlertDialog(
                            onDismissRequest = { pendingQuickUploadUri = null },
                            title = { Text("Send File To Chat") },
                            text = {
                                if (myConversations.isEmpty()) {
                                    Text("Create or start a conversation first.")
                                } else {
                                    LazyColumn(modifier = Modifier.height(220.dp)) {
                                        items(myConversations, key = { it.id }) { conv ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        pendingQuickUploadUri = null
                                                        openChat(conv)
                                                        chatViewModel.uploadAndSendMedia(
                                                            uri = uri,
                                                            conversation = conv,
                                                            profile = profile,
                                                            maxUploadMb = platformSettings.maxUploadMb
                                                        )
                                                    }
                                                    .padding(vertical = 10.dp)
                                            ) {
                                                Text(conv.title, style = MaterialTheme.typography.titleMedium)
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { pendingQuickUploadUri = null }) { Text("Cancel") }
                            }
                        )
                    }

                    // Live Incoming Call Banner
                    incomingCall?.let { call ->
                        IncomingCallBanner(
                            call = call,
                            onAccept = { callViewModel.acceptIncomingCall(call) },
                            onReject = { callViewModel.rejectIncomingCall(call) }
                        )
                    }

                    // Full-Screen Active WebRTC Call Overlay
                    activeCall?.let { call ->
                        ActiveCallOverlay(
                            call = call,
                            webrtcState = webrtcState,
                            elapsedSeconds = elapsedSeconds,
                            eglContext = callViewModel.eglContext,
                            onAttachSinks = { local, remote -> callViewModel.attachVideoSinks(local, remote) },
                            onToggleMute = { callViewModel.toggleMute() },
                            onToggleSpeaker = { callViewModel.toggleSpeaker() },
                            onToggleCamera = { callViewModel.toggleCamera() },
                            onSwitchCamera = { callViewModel.switchCamera() },
                            onEndCall = { callViewModel.endActiveCall() }
                        )
                    }
                    // Global SnackbarHost so notices and voice/upload errors show across all screens including ChatDetail
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 72.dp)
                    )
                }
            }
        }
    }
}
