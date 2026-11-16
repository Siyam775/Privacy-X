package com.example.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.core.AppConstants
import com.example.core.ThemePreferencesStore
import com.example.data.local.BackupHistoryEntity
import com.example.data.local.LocalMediaRepository
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

private val Context.backupDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "privacyx_backup_prefs"
)

enum class BackupSchedule(val id: String, val label: String, val intervalDays: Long) {
    OFF("off", "Off", 0L),
    DAILY("daily", "Daily", 1L),
    WEEKLY("weekly", "Weekly", 7L),
    MONTHLY("monthly", "Monthly", 30L);

    companion object {
        fun fromId(raw: String?): BackupSchedule =
            entries.firstOrNull { it.id == raw } ?: OFF
    }
}

data class BackupPreferences(
    val schedule: BackupSchedule = BackupSchedule.OFF,
    val includeMediaInBackup: Boolean = true,
    val driveConnected: Boolean = false,
    val lastBackupAtMs: Long = 0L,
    val nextScheduledBackupMs: Long = 0L,
    val lastBackupStatus: String = "No backup created yet",
    val knownBackupMediaIds: Set<String> = emptySet()
)

data class DriveBackupFile(
    val fileId: String,
    val name: String,
    val createdTimeIso: String,
    val sizeBytes: Long
)

sealed interface DriveAuthResolution {
    data class Ready(val accessToken: String) : DriveAuthResolution
    data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthResolution
}

class GoogleDriveBackupManager(private val context: Context) {
    private val localRepo = LocalMediaRepository(context)
    private val themeStore = ThemePreferencesStore(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private object Keys {
        val SCHEDULE = stringPreferencesKey("backup_schedule")
        val INCLUDE_MEDIA = booleanPreferencesKey("include_media")
        val DRIVE_CONNECTED = booleanPreferencesKey("drive_connected")
        val LAST_BACKUP_MS = longPreferencesKey("last_backup_ms")
        val NEXT_SCHEDULED_MS = longPreferencesKey("next_scheduled_ms")
        val LAST_STATUS = stringPreferencesKey("last_backup_status")
        val KNOWN_MEDIA_IDS = stringSetPreferencesKey("known_backup_media_ids")
    }

    val preferencesFlow: Flow<BackupPreferences> = context.backupDataStore.data.map { prefs ->
        BackupPreferences(
            schedule = BackupSchedule.fromId(prefs[Keys.SCHEDULE]),
            includeMediaInBackup = prefs[Keys.INCLUDE_MEDIA] ?: true,
            driveConnected = prefs[Keys.DRIVE_CONNECTED] ?: false,
            lastBackupAtMs = prefs[Keys.LAST_BACKUP_MS] ?: 0L,
            nextScheduledBackupMs = prefs[Keys.NEXT_SCHEDULED_MS] ?: 0L,
            lastBackupStatus = prefs[Keys.LAST_STATUS] ?: "No backup created yet",
            knownBackupMediaIds = prefs[Keys.KNOWN_MEDIA_IDS] ?: emptySet()
        )
    }

    suspend fun setIncludeMedia(include: Boolean) {
        context.backupDataStore.edit { prefs ->
            prefs[Keys.INCLUDE_MEDIA] = include
        }
    }

    suspend fun setDriveConnected(connected: Boolean, status: String? = null) {
        context.backupDataStore.edit { prefs ->
            prefs[Keys.DRIVE_CONNECTED] = connected
            if (status != null) prefs[Keys.LAST_STATUS] = status
        }
    }

    suspend fun setSchedule(schedule: BackupSchedule) {
        val nextMs = if (schedule == BackupSchedule.OFF) {
            0L
        } else {
            System.currentTimeMillis() + TimeUnit.DAYS.toMillis(schedule.intervalDays)
        }
        context.backupDataStore.edit { prefs ->
            prefs[Keys.SCHEDULE] = schedule.id
            prefs[Keys.NEXT_SCHEDULED_MS] = nextMs
        }

        val wm = WorkManager.getInstance(context)
        if (schedule == BackupSchedule.OFF) {
            wm.cancelUniqueWork(UNIQUE_WORK_NAME)
        } else {
            val request = PeriodicWorkRequestBuilder<ScheduledBackupWorker>(
                schedule.intervalDays,
                TimeUnit.DAYS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            wm.enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    suspend fun requestDriveAuthorization(): Result<DriveAuthResolution> = runCatching {
        val authRequest = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(AppConstants.GOOGLE_DRIVE_FILE_SCOPE)))
            .build()
        val result = Identity.getAuthorizationClient(context)
            .authorize(authRequest)
            .await()

        val pi = result.pendingIntent
        if (result.hasResolution() && pi != null) {
            DriveAuthResolution.NeedsConsent(pi)
        } else {
            val token = result.accessToken
                ?: error("Google Drive did not grant an access token.")
            setDriveConnected(true, "Google Drive connected")
            DriveAuthResolution.Ready(token)
        }
    }

    suspend fun extractTokenFromConsentResult(data: Intent?): Result<String> = runCatching {
        val authResult = Identity.getAuthorizationClient(context)
            .getAuthorizationResultFromIntent(data)
        val token = authResult.accessToken
            ?: error("Drive authorization was not completed.")
        setDriveConnected(true, "Google Drive connected")
        token
    }

    suspend fun trySilentToken(): String? {
        val res = requestDriveAuthorization().getOrNull()
        return (res as? DriveAuthResolution.Ready)?.accessToken
    }

    suspend fun performDriveBackup(
        accessToken: String,
        userId: String,
        passphrase: String = ""
    ): Result<BackupHistoryEntity> = withContext(Dispatchers.IO) {
        val prefs = preferencesFlow.first()
        val archiveRes = BackupCryptoZip.buildBackupArchive(
            context = context,
            localRepo = localRepo,
            themeStore = themeStore,
            userId = userId,
            includeMedia = prefs.includeMediaInBackup,
            passphrase = passphrase
        )
        val archive = archiveRes.getOrElse { return@withContext Result.failure(it) }

        val folderId = findOrCreateBackupFolder(accessToken).getOrElse {
            return@withContext Result.failure(it)
        }

        val driveFileId = uploadMultipartToDrive(
            accessToken = accessToken,
            folderId = folderId,
            fileName = archive.suggestedFileName,
            bytes = archive.bytes
        ).getOrElse { return@withContext Result.failure(it) }

        val now = System.currentTimeMillis()
        val historyItem = BackupHistoryEntity(
            backupId = "bk_${UUID.randomUUID().toString().replace("-", "").take(14)}",
            provider = "google_drive",
            fileName = archive.suggestedFileName,
            driveFileId = driveFileId,
            createdAtMs = now,
            sizeBytes = archive.bytes.size.toLong(),
            includedMedia = archive.manifest.includeMedia,
            includedMediaCount = archive.manifest.mediaCount,
            isEncrypted = archive.manifest.encrypted,
            status = "completed",
            summary = "${archive.manifest.conversationsCount} chats, ${archive.manifest.messagesCount} msgs, ${archive.manifest.mediaCount} media"
        )
        localRepo.addBackupHistory(historyItem)

        val nextDue = if (prefs.schedule == BackupSchedule.OFF) 0L
        else now + TimeUnit.DAYS.toMillis(prefs.schedule.intervalDays)

        context.backupDataStore.edit { edit ->
            edit[Keys.DRIVE_CONNECTED] = true
            edit[Keys.LAST_BACKUP_MS] = now
            edit[Keys.NEXT_SCHEDULED_MS] = nextDue
            edit[Keys.LAST_STATUS] = "Last backup succeeded (${historyItem.summary})"
            val mergedIds = (edit[Keys.KNOWN_MEDIA_IDS] ?: emptySet()) + archive.manifest.mediaIds
            edit[Keys.KNOWN_MEDIA_IDS] = mergedIds
        }

        Result.success(historyItem)
    }

    suspend fun exportLocalBackupToUri(
        destUri: Uri,
        userId: String,
        passphrase: String = ""
    ): Result<BackupHistoryEntity> = withContext(Dispatchers.IO) {
        val prefs = preferencesFlow.first()
        val archive = BackupCryptoZip.buildBackupArchive(
            context = context,
            localRepo = localRepo,
            themeStore = themeStore,
            userId = userId,
            includeMedia = prefs.includeMediaInBackup,
            passphrase = passphrase
        ).getOrElse { return@withContext Result.failure(it) }

        runCatching {
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                out.write(archive.bytes)
            } ?: error("Unable to open destination file for local backup.")

            val now = System.currentTimeMillis()
            val historyItem = BackupHistoryEntity(
                backupId = "bk_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                provider = "local_zip",
                fileName = archive.suggestedFileName,
                driveFileId = "",
                createdAtMs = now,
                sizeBytes = archive.bytes.size.toLong(),
                includedMedia = archive.manifest.includeMedia,
                includedMediaCount = archive.manifest.mediaCount,
                isEncrypted = archive.manifest.encrypted,
                status = "completed",
                summary = "${archive.manifest.conversationsCount} chats, ${archive.manifest.messagesCount} msgs, ${archive.manifest.mediaCount} media"
            )
            localRepo.addBackupHistory(historyItem)
            context.backupDataStore.edit { edit ->
                edit[Keys.LAST_BACKUP_MS] = now
                edit[Keys.LAST_STATUS] = "Exported local backup (${historyItem.summary})"
                edit[Keys.KNOWN_MEDIA_IDS] = (edit[Keys.KNOWN_MEDIA_IDS] ?: emptySet()) + archive.manifest.mediaIds
            }
            historyItem
        }
    }

    suspend fun listDriveBackups(accessToken: String): Result<List<DriveBackupFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val folderId = findOrCreateBackupFolder(accessToken).getOrThrow()
            val q = Uri.encode("'$folderId' in parents and trashed = false")
            val url = "https://www.googleapis.com/drive/v3/files?q=$q&orderBy=createdTime%20desc&fields=files(id,name,createdTime,size)"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("Drive list failed (HTTP ${resp.code}): $raw")
                val arr = JSONObject(raw).optJSONArray("files") ?: return@runCatching emptyList()
                buildList {
                    for (i in 0 until arr.length()) {
                        val f = arr.getJSONObject(i)
                        add(
                            DriveBackupFile(
                                fileId = f.optString("id"),
                                name = f.optString("name"),
                                createdTimeIso = f.optString("createdTime"),
                                sizeBytes = f.optString("size", "0").toLongOrNull() ?: 0L
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun downloadDriveBackupBytes(accessToken: String, fileId: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Drive download failed (HTTP ${resp.code})")
                resp.body?.bytes() ?: error("Empty backup file from Google Drive")
            }
        }
    }

    suspend fun restoreFromBackupBytes(
        rawBytes: ByteArray,
        passphrase: String,
        provider: String,
        sourceName: String
    ): Result<RestoreSummary> {
        val res = BackupCryptoZip.restoreFromBackupBytes(
            context = context,
            rawBytes = rawBytes,
            passphrase = passphrase,
            localRepo = localRepo,
            themeStore = themeStore
        )
        res.onSuccess { summary ->
            localRepo.addBackupHistory(
                BackupHistoryEntity(
                    backupId = "rs_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                    provider = provider,
                    fileName = sourceName,
                    driveFileId = "",
                    createdAtMs = System.currentTimeMillis(),
                    sizeBytes = rawBytes.size.toLong(),
                    includedMedia = summary.mediaFilesRestored > 0,
                    includedMediaCount = summary.mediaFilesRestored,
                    isEncrypted = summary.encrypted,
                    status = "restored",
                    summary = "Restored ${summary.conversationsRestored} chats, ${summary.messagesRestored} msgs, ${summary.mediaFilesRestored} local media files"
                )
            )
            context.backupDataStore.edit { edit ->
                edit[Keys.KNOWN_MEDIA_IDS] = (edit[Keys.KNOWN_MEDIA_IDS] ?: emptySet()) + summary.knownBackupMediaIds
                edit[Keys.LAST_STATUS] = "Restored ${summary.mediaFilesRestored} media files directly to local storage"
            }
        }
        return res
    }

    suspend fun runDueBackupIfNeeded(userId: String): Boolean {
        val prefs = preferencesFlow.first()
        if (prefs.schedule == BackupSchedule.OFF || !prefs.driveConnected) return false
        if (prefs.nextScheduledBackupMs <= 0L || System.currentTimeMillis() < prefs.nextScheduledBackupMs) return false
        val token = trySilentToken() ?: return false
        return performDriveBackup(token, userId).isSuccess
    }

    private fun findOrCreateBackupFolder(accessToken: String): Result<String> = runCatching {
        val folderName = AppConstants.GOOGLE_DRIVE_BACKUP_FOLDER
        val q = Uri.encode("mimeType = 'application/vnd.google-apps.folder' and name = '$folderName' and trashed = false")
        val searchReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id,name)")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        httpClient.newCall(searchReq).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Drive folder lookup failed (HTTP ${resp.code}): $raw")
            val arr = JSONObject(raw).optJSONArray("files")
            if (arr != null && arr.length() > 0) {
                return@runCatching arr.getJSONObject(0).getString("id")
            }
        }

        val createMeta = JSONObject()
            .put("name", folderName)
            .put("mimeType", "application/vnd.google-apps.folder")
            .toString()
        val createReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?fields=id")
            .header("Authorization", "Bearer $accessToken")
            .post(createMeta.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        httpClient.newCall(createReq).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Drive folder creation failed (HTTP ${resp.code}): $raw")
            JSONObject(raw).getString("id")
        }
    }

    private fun uploadMultipartToDrive(
        accessToken: String,
        folderId: String,
        fileName: String,
        bytes: ByteArray
    ): Result<String> = runCatching {
        val metadata = JSONObject()
            .put("name", fileName)
            .put("parents", org.json.JSONArray(listOf(folderId)))
            .toString()

        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addPart(bytes.toRequestBody("application/zip".toMediaType()))
            .build()

        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
            .header("Authorization", "Bearer $accessToken")
            .post(body)
            .build()
        httpClient.newCall(req).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Drive backup upload failed (HTTP ${resp.code}): $raw")
            JSONObject(raw).getString("id")
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "privacyx_scheduled_drive_backup"
    }
}
