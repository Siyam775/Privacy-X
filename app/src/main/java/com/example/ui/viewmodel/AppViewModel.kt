package com.example.ui.viewmodel

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.backup.BackupPreferences
import com.example.backup.BackupSchedule
import com.example.backup.DriveAuthResolution
import com.example.backup.DriveBackupFile
import com.example.backup.GoogleDriveBackupManager
import com.example.core.AppConstants
import com.example.core.LocalThemePreferences
import com.example.core.NotificationHelper
import com.example.core.ThemePreferencesStore
import com.example.data.local.BackupHistoryEntity
import com.example.data.local.LocalMediaRepository
import com.example.data.local.StorageStatsSnapshot
import com.example.data.model.BlockEdge
import com.example.data.model.FollowEdge
import com.example.data.model.NotificationItem
import com.example.data.model.PlatformAnnouncement
import com.example.data.model.PlatformSettings
import com.example.data.model.UserPrivateAccount
import com.example.data.model.UserProfile
import com.example.data.model.UserSession
import com.example.data.model.UserSettings
import com.example.data.model.UsernameReservation
import com.example.data.repository.AdminRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.SocialRepository
import com.example.media.SupabaseMediaService
import com.example.ui.theme.AppThemeId
import com.example.ui.theme.AppearanceMode
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

sealed interface AuthGateState {
    data object Loading : AuthGateState
    data object SignedOut : AuthGateState
    data class NeedsOnboarding(val uid: String, val email: String, val suggestedName: String, val photoUrl: String) : AuthGateState
    data class Ready(val uid: String, val profile: UserProfile) : AuthGateState
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val authRepository = AuthRepository(context)
    private val socialRepository = SocialRepository(context)
    private val adminRepository = AdminRepository(context)
    private val localMediaRepo = LocalMediaRepository(context)
    private val mediaService = SupabaseMediaService(context)
    val backupManager = GoogleDriveBackupManager(context)
    val themeStore = ThemePreferencesStore(context)

    private val firebaseAuth = Firebase.auth

    val localThemePrefs: StateFlow<LocalThemePreferences> = themeStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, LocalThemePreferences())

    val storageStats: StateFlow<StorageStatsSnapshot> = localMediaRepo.storageStatsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, StorageStatsSnapshot())

    val backupPreferences: StateFlow<BackupPreferences> = backupManager.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, BackupPreferences())

    val backupHistory: StateFlow<List<BackupHistoryEntity>> = localMediaRepo.backupHistoryFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _driveBackups = MutableStateFlow<List<DriveBackupFile>>(emptyList())
    val driveBackups: StateFlow<List<DriveBackupFile>> = _driveBackups.asStateFlow()

    private val _isBackupBusy = MutableStateFlow(false)
    val isBackupBusy: StateFlow<Boolean> = _isBackupBusy.asStateFlow()

    private val _pendingDriveConsent = MutableStateFlow<PendingIntent?>(null)
    val pendingDriveConsent: StateFlow<PendingIntent?> = _pendingDriveConsent.asStateFlow()

    private var pendingDriveOp: PendingDriveOp? = null
    private enum class PendingDriveActionType { CONNECT_ONLY, BACKUP_NOW, LIST_FILES }
    private data class PendingDriveOp(val type: PendingDriveActionType, val passphrase: String = "")

    private val _authGate = MutableStateFlow<AuthGateState>(AuthGateState.Loading)
    val authGate: StateFlow<AuthGateState> = _authGate.asStateFlow()

    private val _userSettings = MutableStateFlow(UserSettings())
    val userSettings: StateFlow<UserSettings> = _userSettings.asStateFlow()

    private val _privateAccount = MutableStateFlow<UserPrivateAccount?>(null)
    val privateAccount: StateFlow<UserPrivateAccount?> = _privateAccount.asStateFlow()

    private val _sessions = MutableStateFlow<List<UserSession>>(emptyList())
    val sessions: StateFlow<List<UserSession>> = _sessions.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    private val _following = MutableStateFlow<List<FollowEdge>>(emptyList())
    val following: StateFlow<List<FollowEdge>> = _following.asStateFlow()

    private val _followers = MutableStateFlow<List<FollowEdge>>(emptyList())
    val followers: StateFlow<List<FollowEdge>> = _followers.asStateFlow()

    private val _blockedUsers = MutableStateFlow<List<BlockEdge>>(emptyList())
    val blockedUsers: StateFlow<List<BlockEdge>> = _blockedUsers.asStateFlow()

    private val _announcements = MutableStateFlow<List<PlatformAnnouncement>>(emptyList())
    val announcements: StateFlow<List<PlatformAnnouncement>> = _announcements.asStateFlow()

    private val _platformSettings = MutableStateFlow(PlatformSettings())
    val platformSettings: StateFlow<PlatformSettings> = _platformSettings.asStateFlow()

    private val _directoryUsers = MutableStateFlow<List<UsernameReservation>>(emptyList())
    val directoryUsers: StateFlow<List<UsernameReservation>> = _directoryUsers.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isRadialMenuOpen = MutableStateFlow(false)
    val isRadialMenuOpen: StateFlow<Boolean> = _isRadialMenuOpen.asStateFlow()

    private var userStreamsJob: Job? = null
    private var lastNotifiedIds = mutableSetOf<String>()

    private val authListener = FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        if (user == null) {
            userStreamsJob?.cancel()
            _authGate.value = AuthGateState.SignedOut
            _notifications.value = emptyList()
            _sessions.value = emptyList()
            _following.value = emptyList()
            _followers.value = emptyList()
            _blockedUsers.value = emptyList()
        } else {
            bindUserStreams(
                uid = user.uid,
                email = user.email.orEmpty(),
                suggestedName = user.displayName.orEmpty(),
                photoUrl = user.photoUrl?.toString().orEmpty()
            )
        }
    }

    init {
        firebaseAuth.addAuthStateListener(authListener)
        val current = firebaseAuth.currentUser
        if (current == null) {
            _authGate.value = AuthGateState.SignedOut
        } else {
            bindUserStreams(
                uid = current.uid,
                email = current.email.orEmpty(),
                suggestedName = current.displayName.orEmpty(),
                photoUrl = current.photoUrl?.toString().orEmpty()
            )
        }
    }

    private fun bindUserStreams(uid: String, email: String, suggestedName: String, photoUrl: String) {
        userStreamsJob?.cancel()
        userStreamsJob = viewModelScope.launch {
            launch {
                authRepository.observeUserProfile(uid)
                    .catch { _statusBanner.value = it.localizedMessage }
                    .collect { profile ->
                        if (profile == null) {
                            _authGate.value = AuthGateState.NeedsOnboarding(uid, email, suggestedName, photoUrl)
                        } else {
                            _authGate.value = AuthGateState.Ready(uid, profile)
                            authRepository.registerCurrentSession(uid)
                        }
                    }
            }
            launch {
                authRepository.observePrivateAccount(uid).catch {}.collect { _privateAccount.value = it }
            }
            launch {
                authRepository.observeUserSettings(uid).catch {}.collect { settings ->
                    _userSettings.value = settings
                    themeStore.saveTheme(
                        themeId = AppThemeId.fromId(settings.themeId),
                        appearanceMode = AppearanceMode.fromId(settings.appearanceMode),
                        accentColorHex = settings.accentColorHex,
                        reducedMotion = settings.reducedMotion
                    )
                }
            }
            launch {
                authRepository.observeSessions(uid).catch {}.collect { _sessions.value = it }
            }
            launch {
                socialRepository.observeNotifications(uid).catch {}.collect { list ->
                    val firstLoad = lastNotifiedIds.isEmpty()
                    if (!firstLoad) {
                        val settings = _userSettings.value
                        if (settings.notificationsEnabled && !settings.quietHoursEnabled) {
                            list.filter { !it.isRead && lastNotifiedIds.add(it.id) }.forEach { item ->
                                NotificationHelper.showLocalNotification(
                                    context = context,
                                    notificationId = item.id.hashCode(),
                                    title = item.title,
                                    body = item.body,
                                    hidePreview = !settings.showNotificationPreview
                                )
                            }
                        }
                    } else {
                        list.forEach { lastNotifiedIds.add(it.id) }
                    }
                    _notifications.value = list
                }
            }
            launch { socialRepository.observeFollowing(uid).catch {}.collect { _following.value = it } }
            launch { socialRepository.observeFollowers(uid).catch {}.collect { _followers.value = it } }
            launch { socialRepository.observeBlockedUsers(uid).catch {}.collect { _blockedUsers.value = it } }
            launch { adminRepository.observePlatformAnnouncements().catch {}.collect { _announcements.value = it } }
            launch { adminRepository.observePlatformSettings().catch {}.collect { _platformSettings.value = it } }
            launch { refreshDirectoryUsers("") }
            launch { runCatching { backupManager.runDueBackupIfNeeded(uid) } }
        }
    }

    fun isSuperAdmin(): Boolean {
        val uid = firebaseAuth.currentUser?.uid
        return uid == AppConstants.SUPER_ADMIN_UID
    }

    fun toggleRadialMenu(open: Boolean? = null) {
        _isRadialMenuOpen.value = open ?: !_isRadialMenuOpen.value
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun postBanner(msg: String) {
        _statusBanner.value = msg
    }

    fun setOnlinePresence(online: Boolean) {
        viewModelScope.launch {
            authRepository.updatePresence(online)
        }
    }

    suspend fun signInWithGoogle(activityContext: Context): Result<Unit> {
        _isBusy.value = true
        return try {
            val webClientId = activityContext.getString(R.string.default_web_client_id)
            val credentialManager = CredentialManager.create(activityContext)

            val response = try {
                val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
                credentialManager.getCredential(
                    request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build(),
                    context = activityContext
                )
            } catch (_: NoCredentialException) {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .build()
                credentialManager.getCredential(
                    request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build(),
                    context = activityContext
                )
            }

            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
                firebaseAuth.signInWithCredential(firebaseCred).await()
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Unexpected credential type received."))
            }
        } catch (e: GetCredentialCancellationException) {
            android.util.Log.w("PrivacyXAuth", "Google Sign-In credential flow cancelled", e)
            _statusBanner.value = "Google Sign-In was cancelled. Please select an account to continue."
            Result.failure(IllegalStateException("Google Sign-In cancelled."))
        } catch (e: Exception) {
            android.util.Log.e("PrivacyXAuth", "Google Sign-In failed", e)
            _statusBanner.value = e.localizedMessage ?: "Google Sign-In failed"
            Result.failure(e)
        } finally {
            _isBusy.value = false
        }
    }

    suspend fun checkUsernameAvailable(username: String): Boolean =
        authRepository.isUsernameAvailable(username)

    fun claimUsername(displayName: String, username: String, bio: String) {
        val user = firebaseAuth.currentUser ?: return
        viewModelScope.launch {
            _isBusy.value = true
            val res = authRepository.claimUsernameAndCreateProfile(
                displayName = displayName,
                rawUsername = username,
                bio = bio,
                photoUrl = user.photoUrl?.toString().orEmpty(),
                email = user.email.orEmpty()
            )
            _isBusy.value = false
            res.onFailure { _statusBanner.value = it.localizedMessage ?: "Unable to claim username" }
        }
    }

    fun updateProfile(
        displayName: String,
        bio: String,
        statusText: String,
        photoUrl: String,
        profileVisibility: String,
        lastSeenPrivacy: String,
        storyPrivacy: String,
        customAccentHex: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = authRepository.updateProfile(
                displayName, bio, statusText, photoUrl,
                profileVisibility, lastSeenPrivacy, storyPrivacy, customAccentHex
            )
            _isBusy.value = false
            if (res.isSuccess) _statusBanner.value = "Profile updated"
            else _statusBanner.value = res.exceptionOrNull()?.localizedMessage ?: "Update failed"
        }
    }

    fun updateTheme(
        themeId: AppThemeId? = null,
        appearanceMode: AppearanceMode? = null,
        accentColorHex: String? = null,
        reducedMotion: Boolean? = null
    ) {
        val current = _userSettings.value
        val nextTheme = themeId?.id ?: current.themeId
        val nextMode = appearanceMode?.id ?: current.appearanceMode
        val nextAccent = accentColorHex ?: current.accentColorHex
        val nextMotion = reducedMotion ?: current.reducedMotion
        val updated = current.copy(
            themeId = nextTheme,
            appearanceMode = nextMode,
            accentColorHex = nextAccent,
            reducedMotion = nextMotion
        )
        _userSettings.value = updated
        viewModelScope.launch {
            themeStore.saveTheme(
                themeId = AppThemeId.fromId(nextTheme),
                appearanceMode = AppearanceMode.fromId(nextMode),
                accentColorHex = nextAccent,
                reducedMotion = nextMotion
            )
            if (firebaseAuth.currentUser != null) {
                authRepository.updateUserSettings(updated)
            }
        }
    }

    fun updateUserSettings(updated: UserSettings) {
        _userSettings.value = updated
        viewModelScope.launch {
            themeStore.saveTheme(
                themeId = AppThemeId.fromId(updated.themeId),
                appearanceMode = AppearanceMode.fromId(updated.appearanceMode),
                accentColorHex = updated.accentColorHex,
                reducedMotion = updated.reducedMotion
            )
            authRepository.updateUserSettings(updated)
        }
    }

    fun refreshDirectoryUsers(query: String) {
        viewModelScope.launch {
            _directoryUsers.value = authRepository.searchUsers(query)
        }
    }

    suspend fun loadUserProfile(userId: String): UserProfile? =
        authRepository.getUserProfile(userId)

    fun toggleFollow(targetUserId: String) {
        val currentlyFollowing = _following.value.any { it.followedId == targetUserId }
        viewModelScope.launch {
            socialRepository.toggleFollow(targetUserId, currentlyFollowing)
        }
    }

    fun toggleBlock(targetUserId: String) {
        val currentlyBlocked = _blockedUsers.value.any { it.blockedId == targetUserId }
        viewModelScope.launch {
            val res = socialRepository.toggleBlockUser(targetUserId, currentlyBlocked)
            if (res.isSuccess) {
                _statusBanner.value = if (currentlyBlocked) "User unblocked" else "User blocked"
            }
        }
    }

    fun submitReport(targetType: String, targetId: String, reason: String, details: String) {
        viewModelScope.launch {
            val res = socialRepository.submitReport(targetType, targetId, reason, details)
            _statusBanner.value = if (res.isSuccess) {
                "Report submitted for Super Admin review"
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to submit report"
            }
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch { socialRepository.markNotificationRead(id) }
    }

    fun revokeSession(sessionId: String) {
        viewModelScope.launch {
            authRepository.revokeSession(sessionId)
            _statusBanner.value = "Session revoked"
        }
    }

    fun clearLocalCache() {
        viewModelScope.launch {
            val bytes = mediaService.clearTemporaryCacheOnly()
            _statusBanner.value = "Cleared ${bytes / 1024} KB of temporary cache (saved local media preserved)"
        }
    }

    fun setBackupSchedule(schedule: BackupSchedule) {
        viewModelScope.launch {
            backupManager.setSchedule(schedule)
            _statusBanner.value = "Backup schedule set to ${schedule.label}"
        }
    }

    fun setIncludeMediaInBackup(include: Boolean) {
        viewModelScope.launch {
            backupManager.setIncludeMedia(include)
            _statusBanner.value = if (include) {
                "Images & voice messages will be included in backups"
            } else {
                "Media excluded from future backups"
            }
        }
    }

    fun connectOrBackupGoogleDrive(passphrase: String = "", backupAfterAuth: Boolean = false) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isBackupBusy.value = true
            when (val res = backupManager.requestDriveAuthorization().getOrNull()) {
                is DriveAuthResolution.Ready -> {
                    if (backupAfterAuth) {
                        executeDriveBackupWithToken(res.accessToken, uid, passphrase)
                    } else {
                        _statusBanner.value = "Google Drive connected"
                        loadDriveBackupsWithToken(res.accessToken)
                    }
                    _isBackupBusy.value = false
                }
                is DriveAuthResolution.NeedsConsent -> {
                    pendingDriveOp = PendingDriveOp(
                        type = if (backupAfterAuth) PendingDriveActionType.BACKUP_NOW else PendingDriveActionType.LIST_FILES,
                        passphrase = passphrase
                    )
                    _pendingDriveConsent.value = res.pendingIntent
                    _isBackupBusy.value = false
                }
                null -> {
                    _isBackupBusy.value = false
                    _statusBanner.value = "Unable to authorize Google Drive. Check OAuth configuration."
                }
            }
        }
    }

    fun clearPendingDriveConsent() {
        _pendingDriveConsent.value = null
    }

    fun onDriveConsentIntentResult(data: Intent?) {
        _pendingDriveConsent.value = null
        val op = pendingDriveOp
        pendingDriveOp = null
        val uid = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isBackupBusy.value = true
            val tokenRes = backupManager.extractTokenFromConsentResult(data)
            tokenRes.onSuccess { token ->
                when (op?.type) {
                    PendingDriveActionType.BACKUP_NOW -> executeDriveBackupWithToken(token, uid, op.passphrase)
                    PendingDriveActionType.LIST_FILES -> loadDriveBackupsWithToken(token)
                    PendingDriveActionType.CONNECT_ONLY, null -> _statusBanner.value = "Google Drive connected"
                }
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Google Drive consent cancelled"
            }
            _isBackupBusy.value = false
        }
    }

    fun disconnectDrive() {
        viewModelScope.launch {
            backupManager.setDriveConnected(false, "Google Drive disconnected")
            backupManager.setSchedule(BackupSchedule.OFF)
            _driveBackups.value = emptyList()
            _statusBanner.value = "Google Drive disconnected"
        }
    }

    fun refreshDriveBackups() {
        viewModelScope.launch {
            _isBackupBusy.value = true
            when (val res = backupManager.requestDriveAuthorization().getOrNull()) {
                is DriveAuthResolution.Ready -> loadDriveBackupsWithToken(res.accessToken)
                is DriveAuthResolution.NeedsConsent -> {
                    pendingDriveOp = PendingDriveOp(PendingDriveActionType.LIST_FILES)
                    _pendingDriveConsent.value = res.pendingIntent
                }
                null -> _statusBanner.value = "Connect Google Drive to view backups."
            }
            _isBackupBusy.value = false
        }
    }

    fun restoreFromDriveFile(file: DriveBackupFile, passphrase: String = "") {
        viewModelScope.launch {
            _isBackupBusy.value = true
            val token = backupManager.trySilentToken()
            if (token == null) {
                _isBackupBusy.value = false
                connectOrBackupGoogleDrive(backupAfterAuth = false)
                return@launch
            }
            val bytesRes = backupManager.downloadDriveBackupBytes(token, file.fileId)
            bytesRes.onSuccess { raw ->
                val restoreRes = backupManager.restoreFromBackupBytes(
                    rawBytes = raw,
                    passphrase = passphrase,
                    provider = "google_drive",
                    sourceName = file.name
                )
                restoreRes.onSuccess { summary ->
                    _statusBanner.value = "Restored ${summary.conversationsRestored} chats & ${summary.mediaFilesRestored} local media files"
                }.onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Restore failed"
                }
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Failed to download backup from Drive"
            }
            _isBackupBusy.value = false
        }
    }

    fun exportLocalBackupToUri(uri: Uri, passphrase: String = "") {
        val uid = firebaseAuth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isBackupBusy.value = true
            val res = backupManager.exportLocalBackupToUri(uri, uid, passphrase)
            _isBackupBusy.value = false
            res.onSuccess { item ->
                _statusBanner.value = "Local backup exported (${item.summary})"
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Local export failed"
            }
        }
    }

    fun restoreFromLocalBackupUri(uri: Uri, passphrase: String = "") {
        viewModelScope.launch {
            _isBackupBusy.value = true
            val rawRes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Unable to read selected backup file.")
                }
            }
            rawRes.onSuccess { bytes ->
                val restoreRes = backupManager.restoreFromBackupBytes(
                    rawBytes = bytes,
                    passphrase = passphrase,
                    provider = "local_zip",
                    sourceName = uri.lastPathSegment ?: "local_backup.zip"
                )
                restoreRes.onSuccess { summary ->
                    _statusBanner.value = "Restored ${summary.conversationsRestored} chats & ${summary.mediaFilesRestored} local media files"
                }.onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "Restore failed"
                }
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Could not open backup file"
            }
            _isBackupBusy.value = false
        }
    }

    private suspend fun executeDriveBackupWithToken(accessToken: String, uid: String, passphrase: String) {
        val res = backupManager.performDriveBackup(accessToken, uid, passphrase)
        res.onSuccess { item ->
            _statusBanner.value = "Google Drive backup complete (${item.summary})"
            loadDriveBackupsWithToken(accessToken)
        }.onFailure { err ->
            _statusBanner.value = err.localizedMessage ?: "Drive backup failed"
        }
    }

    private suspend fun loadDriveBackupsWithToken(accessToken: String) {
        backupManager.listDriveBackups(accessToken).onSuccess { list ->
            _driveBackups.value = list
        }
    }

    fun exportUserDataAndShare(onShareIntent: (Intent) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = authRepository.exportUserDataJson()
            _isBusy.value = false
            res.onSuccess { json ->
                val outFile = File(context.cacheDir, "privacyx_export_${System.currentTimeMillis()}.json")
                outFile.writeText(json)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                onShareIntent(Intent.createChooser(share, "Export PrivacyX Data"))
            }.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Export failed"
            }
        }
    }

    fun deleteAccount() {
        viewModelScope.launch {
            _isBusy.value = true
            val res = authRepository.deleteAccount()
            _isBusy.value = false
            res.onFailure {
                _statusBanner.value = it.localizedMessage ?: "Re-authenticate with Google before deleting account."
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.updatePresence(false)
            firebaseAuth.signOut()
        }
    }

    override fun onCleared() {
        firebaseAuth.removeAuthStateListener(authListener)
        super.onCleared()
    }
}
