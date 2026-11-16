package com.example.backup

import android.content.Context
import com.example.core.ThemePreferencesStore
import com.example.data.local.LocalMediaEntity
import com.example.data.local.LocalMediaRepository
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.ui.theme.AppThemeId
import com.example.ui.theme.AppearanceMode
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupManifest(
    val version: Int = 1,
    val timestampIso: String,
    val timestampMs: Long,
    val userId: String,
    val includeMedia: Boolean,
    val encrypted: Boolean,
    val conversationsCount: Int,
    val messagesCount: Int,
    val mediaCount: Int,
    val mediaIds: List<String> = emptyList()
)

data class BuiltBackupArchive(
    val bytes: ByteArray,
    val manifest: BackupManifest,
    val suggestedFileName: String
)

data class RestoreSummary(
    val conversationsRestored: Int,
    val messagesRestored: Int,
    val mediaFilesRestored: Int,
    val knownBackupMediaIds: Set<String>,
    val encrypted: Boolean
)

object BackupCryptoZip {
    private val MAGIC_HEADER = "PXBK1".toByteArray(Charsets.UTF_8) // 5 bytes
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val PBKDF2_ITERATIONS = 65_536

    fun isEncryptedPayload(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC_HEADER.size + SALT_LEN + IV_LEN + 16) return false
        for (i in MAGIC_HEADER.indices) {
            if (bytes[i] != MAGIC_HEADER[i]) return false
        }
        return true
    }

    suspend fun buildBackupArchive(
        context: Context,
        localRepo: LocalMediaRepository,
        themeStore: ThemePreferencesStore,
        userId: String,
        includeMedia: Boolean,
        passphrase: String = ""
    ): Result<BuiltBackupArchive> = withContext(Dispatchers.IO) {
        runCatching {
            val nowMs = System.currentTimeMillis()
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(nowMs))

            val conversations = localRepo.getCachedConversations()
            val messages = localRepo.getAllCachedMessages()
            val mediaItems = localRepo.getAllLocalMedia()
            val prefs = themeStore.preferencesFlow.first()

            val mediaEntryMap = linkedMapOf<String, File>()
            val mediaMetadataJson = JSONArray()
            val includedMediaIds = mutableListOf<String>()

            mediaItems.forEach { m ->
                val mainFile = m.localFilePath.takeIf { it.isNotBlank() }?.let(::File)
                val mainExists = mainFile != null && mainFile.exists()
                val zipEntryPath = if (includeMedia && mainExists) {
                    val ext = mainFile!!.extension.ifBlank {
                        if (m.mediaType == "voice") "m4a" else if (m.mediaType == "image") "webp" else "bin"
                    }
                    val rel = "media/${m.mediaId}.$ext"
                    mediaEntryMap[rel] = mainFile
                    rel
                } else {
                    ""
                }

                val thumbFile = m.localThumbPath.takeIf { it.isNotBlank() }?.let(::File)
                val thumbZipPath = if (includeMedia && thumbFile != null && thumbFile.exists()) {
                    val rel = "media/${m.mediaId}_thumb.webp"
                    mediaEntryMap[rel] = thumbFile
                    rel
                } else {
                    ""
                }

                if (zipEntryPath.isNotBlank()) {
                    includedMediaIds.add(m.mediaId)
                }

                mediaMetadataJson.put(
                    JSONObject()
                        .put("mediaId", m.mediaId)
                        .put("messageId", m.messageId)
                        .put("conversationId", m.conversationId)
                        .put("mediaType", m.mediaType)
                        .put("mimeType", m.mimeType)
                        .put("fileName", m.fileName)
                        .put("sizeBytes", m.sizeBytes)
                        .put("durationMs", m.durationMs)
                        .put("createdAtMs", m.createdAtMs)
                        .put("expiresAtMs", m.expiresAtMs)
                        .put("cloudDeleted", m.cloudDeleted)
                        .put("zipEntryPath", zipEntryPath)
                        .put("thumbZipPath", thumbZipPath)
                )
            }

            val conversationsJson = JSONArray().apply {
                conversations.forEach { c ->
                    put(
                        JSONObject()
                            .put("id", c.id)
                            .put("type", c.type)
                            .put("title", c.title)
                            .put("description", c.description)
                            .put("photoUrl", c.photoUrl)
                            .put("ownerId", c.ownerId)
                            .put("memberUids", JSONArray(c.memberUids))
                            .put("adminUids", JSONArray(c.adminUids))
                            .put("lastMessageText", c.lastMessageText)
                            .put("lastMessageSenderId", c.lastMessageSenderId)
                            .put("updatedAtMs", c.lastMessageAt?.toDate()?.time ?: c.updatedAt?.toDate()?.time ?: nowMs)
                    )
                }
            }

            val messagesJson = JSONArray().apply {
                messages.forEach { m ->
                    put(
                        JSONObject()
                            .put("id", m.id)
                            .put("conversationId", m.conversationId)
                            .put("senderId", m.senderId)
                            .put("senderName", m.senderName)
                            .put("text", m.text)
                            .put("mediaId", m.mediaId)
                            .put("mediaUrl", m.mediaUrl)
                            .put("mediaThumbnailUrl", m.mediaThumbnailUrl)
                            .put("mediaType", m.mediaType)
                            .put("fileName", m.fileName)
                            .put("fileSizeBytes", m.fileSizeBytes)
                            .put("mimeType", m.mimeType)
                            .put("durationMs", m.durationMs)
                            .put("mediaExpiresAt", m.mediaExpiresAt)
                            .put("mediaExpired", m.mediaExpired)
                            .put("replyToId", m.replyToId)
                            .put("replyToPreview", m.replyToPreview)
                            .put("isPinned", m.isPinned)
                            .put("isDeleted", m.isDeleted)
                            .put("createdAtMs", m.createdAtMs)
                    )
                }
            }

            val settingsJson = JSONObject()
                .put("themeId", prefs.themeId.id)
                .put("appearanceMode", prefs.appearanceMode.id)
                .put("accentColorHex", prefs.accentColorHex)
                .put("reducedMotion", prefs.reducedMotion)

            val encrypted = passphrase.isNotBlank()
            val manifest = BackupManifest(
                version = 1,
                timestampIso = iso,
                timestampMs = nowMs,
                userId = userId,
                includeMedia = includeMedia,
                encrypted = encrypted,
                conversationsCount = conversations.size,
                messagesCount = messages.size,
                mediaCount = mediaEntryMap.size,
                mediaIds = includedMediaIds
            )
            val manifestJson = JSONObject()
                .put("version", manifest.version)
                .put("timestampIso", manifest.timestampIso)
                .put("timestampMs", manifest.timestampMs)
                .put("userId", manifest.userId)
                .put("includeMedia", manifest.includeMedia)
                .put("encrypted", manifest.encrypted)
                .put("conversationsCount", manifest.conversationsCount)
                .put("messagesCount", manifest.messagesCount)
                .put("mediaCount", manifest.mediaCount)
                .put("mediaIds", JSONArray(manifest.mediaIds))

            val zipStream = ByteArrayOutputStream()
            ZipOutputStream(zipStream).use { zos ->
                writeZipTextEntry(zos, "manifest.json", manifestJson.toString(2))
                writeZipTextEntry(zos, "conversations.json", conversationsJson.toString())
                writeZipTextEntry(zos, "messages.json", messagesJson.toString())
                writeZipTextEntry(zos, "settings.json", settingsJson.toString(2))
                writeZipTextEntry(zos, "media_metadata.json", mediaMetadataJson.toString())

                mediaEntryMap.forEach { (entryName, file) ->
                    if (file.exists()) {
                        zos.putNextEntry(ZipEntry(entryName))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }

            val rawZipBytes = zipStream.toByteArray()
            val finalBytes = if (encrypted) {
                encryptBytes(rawZipBytes, passphrase)
            } else {
                rawZipBytes
            }

            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nowMs))
            val ext = if (encrypted) "pxbackup" else "zip"
            BuiltBackupArchive(
                bytes = finalBytes,
                manifest = manifest,
                suggestedFileName = "privacyx_backup_${stamp}.$ext"
            )
        }
    }

    suspend fun restoreFromBackupBytes(
        context: Context,
        rawBytes: ByteArray,
        passphrase: String,
        localRepo: LocalMediaRepository,
        themeStore: ThemePreferencesStore
    ): Result<RestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val isEncrypted = isEncryptedPayload(rawBytes)
            val zipBytes = if (isEncrypted) {
                if (passphrase.isBlank()) {
                    error("This backup is encrypted. Enter your backup passphrase to restore.")
                }
                decryptBytes(rawBytes, passphrase)
            } else {
                rawBytes
            }

            val jsonEntries = mutableMapOf<String, String>()
            val stagingDir = File(context.cacheDir, "restore_staging_${System.currentTimeMillis()}").apply {
                mkdirs()
            }
            val extractedFiles = mutableMapOf<String, File>()

            try {
                ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            // Guard against Zip Slip
                            val normalizedName = entry.name.replace("\\", "/").trimStart('/')
                            if (!normalizedName.contains("..")) {
                                if (normalizedName.endsWith(".json")) {
                                    jsonEntries[normalizedName] = zis.readBytes().toString(Charsets.UTF_8)
                                } else if (normalizedName.startsWith("media/")) {
                                    val safeFileName = normalizedName.substringAfterLast('/')
                                    if (safeFileName.isNotBlank()) {
                                        val tmp = File(stagingDir, safeFileName)
                                        FileOutputStream(tmp).use { out -> zis.copyTo(out) }
                                        extractedFiles[normalizedName] = tmp
                                    }
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }

                // 1. Restore settings
                jsonEntries["settings.json"]?.let { raw ->
                    val s = JSONObject(raw)
                    themeStore.saveTheme(
                        themeId = AppThemeId.fromId(s.optString("themeId")),
                        appearanceMode = AppearanceMode.fromId(s.optString("appearanceMode")),
                        accentColorHex = s.optString("accentColorHex", ""),
                        reducedMotion = s.optBoolean("reducedMotion", false)
                    )
                }

                // 2. Restore conversations (deduplicated by Room primary key)
                val restoredConversations = mutableListOf<Conversation>()
                jsonEntries["conversations.json"]?.let { raw ->
                    val arr = JSONArray(raw)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val cid = o.optString("id")
                        if (cid.isNotBlank()) {
                            val members = jsonArrayToList(o.optJSONArray("memberUids"))
                            val admins = jsonArrayToList(o.optJSONArray("adminUids"))
                            val updatedMs = o.optLong("updatedAtMs", System.currentTimeMillis())
                            restoredConversations.add(
                                Conversation(
                                    id = cid,
                                    type = o.optString("type", "direct"),
                                    title = o.optString("title", "Chat"),
                                    description = o.optString("description", ""),
                                    photoUrl = o.optString("photoUrl", ""),
                                    ownerId = o.optString("ownerId", ""),
                                    memberUids = members,
                                    adminUids = admins,
                                    lastMessageText = o.optString("lastMessageText", ""),
                                    lastMessageSenderId = o.optString("lastMessageSenderId", ""),
                                    lastMessageAt = Timestamp(Date(updatedMs)),
                                    updatedAt = Timestamp(Date(updatedMs))
                                )
                            )
                        }
                    }
                }
                if (restoredConversations.isNotEmpty()) {
                    localRepo.cacheConversations(restoredConversations)
                }

                // 3. Restore messages (deduplicated by Room primary key)
                val restoredMessages = mutableListOf<ChatMessage>()
                jsonEntries["messages.json"]?.let { raw ->
                    val arr = JSONArray(raw)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val mid = o.optString("id")
                        val cid = o.optString("conversationId")
                        if (mid.isNotBlank() && cid.isNotBlank()) {
                            val createdMs = o.optLong("createdAtMs", System.currentTimeMillis())
                            restoredMessages.add(
                                ChatMessage(
                                    id = mid,
                                    conversationId = cid,
                                    senderId = o.optString("senderId"),
                                    senderName = o.optString("senderName", "User"),
                                    text = o.optString("text"),
                                    mediaId = o.optString("mediaId"),
                                    mediaUrl = o.optString("mediaUrl"),
                                    mediaThumbnailUrl = o.optString("mediaThumbnailUrl"),
                                    mediaType = o.optString("mediaType", "text"),
                                    fileName = o.optString("fileName"),
                                    fileSizeBytes = o.optLong("fileSizeBytes", 0L),
                                    mimeType = o.optString("mimeType"),
                                    durationMs = o.optLong("durationMs", 0L),
                                    mediaExpiresAt = o.optLong("mediaExpiresAt", 0L),
                                    mediaExpired = o.optBoolean("mediaExpired", false),
                                    replyToId = o.optString("replyToId"),
                                    replyToPreview = o.optString("replyToPreview"),
                                    isPinned = o.optBoolean("isPinned", false),
                                    isDeleted = o.optBoolean("isDeleted", false),
                                    createdAt = Timestamp(Date(createdMs)),
                                    updatedAt = Timestamp(Date(createdMs))
                                )
                            )
                        }
                    }
                }
                if (restoredMessages.isNotEmpty()) {
                    localRepo.cacheMessages(restoredMessages)
                }

                // 4. Restore media files DIRECTLY to filesDir/local_media (zero Supabase re-upload!)
                var mediaCount = 0
                val knownMediaIds = mutableSetOf<String>()
                val localRoot = File(context.filesDir, "local_media").apply { mkdirs() }
                val existingLocalMap = localRepo.getAllLocalMedia().associateBy { it.mediaId }

                jsonEntries["media_metadata.json"]?.let { raw ->
                    val arr = JSONArray(raw)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val mediaId = o.optString("mediaId")
                        if (mediaId.isBlank()) continue
                        knownMediaIds.add(mediaId)

                        val convId = o.optString("conversationId", "restored")
                            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                            .ifBlank { "restored" }
                        val convDir = File(localRoot, convId).apply { mkdirs() }

                        val zipEntryPath = o.optString("zipEntryPath")
                        val stagedMain = extractedFiles[zipEntryPath]
                        val existing = existingLocalMap[mediaId]

                        val targetMainPath = if (stagedMain != null && stagedMain.exists()) {
                            val destFile = File(convDir, stagedMain.name)
                            if (!destFile.exists() || destFile.length() == 0L) {
                                stagedMain.copyTo(destFile, overwrite = true)
                            }
                            mediaCount++
                            destFile.absolutePath
                        } else {
                            existing?.localFilePath.orEmpty()
                        }

                        val thumbZipPath = o.optString("thumbZipPath")
                        val stagedThumb = extractedFiles[thumbZipPath]
                        val targetThumbPath = if (stagedThumb != null && stagedThumb.exists()) {
                            val thumbDest = File(convDir, stagedThumb.name)
                            if (!thumbDest.exists()) {
                                stagedThumb.copyTo(thumbDest, overwrite = true)
                            }
                            thumbDest.absolutePath
                        } else {
                            existing?.localThumbPath.orEmpty()
                        }

                        if (targetMainPath.isNotBlank() && File(targetMainPath).exists()) {
                            val expiresAtMs = o.optLong("expiresAtMs", 0L)
                            val isCloudExpired = o.optBoolean("cloudDeleted", false) ||
                                (expiresAtMs > 0L && expiresAtMs <= System.currentTimeMillis())

                            localRepo.saveLocalMediaRecord(
                                LocalMediaEntity(
                                    mediaId = mediaId,
                                    messageId = o.optString("messageId").ifBlank { existing?.messageId.orEmpty() },
                                    conversationId = o.optString("conversationId", convId),
                                    mediaType = o.optString("mediaType", "image"),
                                    localFilePath = targetMainPath,
                                    localThumbPath = targetThumbPath,
                                    remotePath = existing?.remotePath.orEmpty(),
                                    mimeType = o.optString("mimeType", "image/webp"),
                                    fileName = o.optString("fileName", File(targetMainPath).name),
                                    sizeBytes = File(targetMainPath).length(),
                                    durationMs = o.optLong("durationMs", 0L),
                                    createdAtMs = o.optLong("createdAtMs", System.currentTimeMillis()),
                                    expiresAtMs = expiresAtMs,
                                    cloudDeleted = isCloudExpired,
                                    isRestoredFromBackup = true,
                                    downloadedToDevice = existing?.downloadedToDevice ?: true,
                                    downloadedAtMs = existing?.downloadedAtMs ?: System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }

                RestoreSummary(
                    conversationsRestored = restoredConversations.size,
                    messagesRestored = restoredMessages.size,
                    mediaFilesRestored = mediaCount,
                    knownBackupMediaIds = knownMediaIds,
                    encrypted = isEncrypted
                )
            } finally {
                stagingDir.deleteRecursively()
            }
        }
    }

    private fun writeZipTextEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun jsonArrayToList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val v = arr.optString(i)
                if (v.isNotBlank()) add(v)
            }
        }
    }

    internal fun encryptBytes(plain: ByteArray, passphrase: String): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also(random::nextBytes)
        val iv = ByteArray(IV_LEN).also(random::nextBytes)
        val key = deriveAesKey(passphrase, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plain)

        return ByteArrayOutputStream().use { out ->
            out.write(MAGIC_HEADER)
            out.write(salt)
            out.write(iv)
            out.write(ciphertext)
            out.toByteArray()
        }
    }

    internal fun decryptBytes(encrypted: ByteArray, passphrase: String): ByteArray {
        if (!isEncryptedPayload(encrypted)) {
            error("Invalid encrypted backup format.")
        }
        val offsetSalt = MAGIC_HEADER.size
        val offsetIv = offsetSalt + SALT_LEN
        val offsetCipher = offsetIv + IV_LEN

        val salt = encrypted.copyOfRange(offsetSalt, offsetIv)
        val iv = encrypted.copyOfRange(offsetIv, offsetCipher)
        val ciphertext = encrypted.copyOfRange(offsetCipher, encrypted.size)
        val key = deriveAesKey(passphrase, salt)

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext)
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("Incorrect backup password.")
        }
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val secret = factory.generateSecret(spec)
        return SecretKeySpec(secret.encoded, "AES")
    }
}
