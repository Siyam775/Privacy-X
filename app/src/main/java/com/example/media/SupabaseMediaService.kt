package com.example.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.core.AppConstants
import com.example.data.local.LocalMediaEntity
import com.example.data.local.LocalMediaRepository
import com.example.data.model.ChatMessage
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class MediaValidationResult(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val category: String // "image" | "voice" | "document" | "video"
)

data class UploadedMediaResult(
    val mediaId: String,
    val secureUrl: String,
    val thumbnailUrl: String,
    val remotePath: String,
    val localFilePath: String,
    val localThumbPath: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val expiresAtMs: Long,
    val category: String
)

data class DownloadMediaResult(
    val localFilePath: String,
    val exportedDisplayPath: String,
    val bytesWritten: Long
)

class SupabaseNotConfiguredException(message: String) : IllegalStateException(message)
class VideoMessagingDisabledException(message: String) : IllegalArgumentException(message)

class SupabaseMediaService(private val context: Context) {
    private val localRepo = LocalMediaRepository(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val allowedMimePrefixes = listOf("image/", "audio/", "video/")
    private val allowedDocMimes = setOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "text/plain",
        "application/zip",
        "application/x-zip-compressed"
    )

    private val extensionMimeMap = mapOf(
        "m4a" to "audio/mp4",
        "mp4a" to "audio/mp4",
        "3gp" to "audio/3gpp",
        "3gpp" to "audio/3gpp",
        "amr" to "audio/amr",
        "aac" to "audio/aac",
        "mp3" to "audio/mpeg",
        "ogg" to "audio/ogg",
        "opus" to "audio/ogg",
        "wav" to "audio/wav",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "webp" to "image/webp",
        "gif" to "image/gif",
        "heic" to "image/heic",
        "mp4" to "video/mp4",
        "m4v" to "video/x-m4v",
        "mov" to "video/quicktime",
        "webm" to "video/webm",
        "mkv" to "video/x-matroska",
        "avi" to "video/x-msvideo",
        "pdf" to "application/pdf",
        "txt" to "text/plain",
        "zip" to "application/zip"
    )

    fun isConfigured(): Boolean =
        AppConstants.supabaseMediaBridgeUrl.isNotBlank() ||
            (AppConstants.supabaseUrl.isNotBlank() && AppConstants.supabaseAnonKey.isNotBlank())

    fun localMediaRootDir(): File = File(context.filesDir, "local_media").apply { mkdirs() }

    fun saveLocalWallpaperCopy(uri: Uri, conversationId: String): Result<String> = runCatching {
        val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val safeId = conversationId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val dest = File(dir, "wallpaper_${safeId}_${System.currentTimeMillis()}.jpg")
        dir.listFiles()?.filter { it.name.startsWith("wallpaper_${safeId}_") }?.forEach { it.delete() }
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        } ?: error("Unable to read selected wallpaper image.")
        Uri.fromFile(dest).toString()
    }

    fun validateUri(uri: Uri, maxUploadMb: Long = AppConstants.DEFAULT_MAX_UPLOAD_MB): Result<MediaValidationResult> {
        val resolver = context.contentResolver
        var name = "file_${System.currentTimeMillis()}"
        var size = 0L

        if (uri.scheme == "file") {
            val f = File(uri.path ?: "")
            if (f.exists()) {
                name = f.name
                size = f.length()
            }
        } else {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        }

        val extFromName = name.substringAfterLast('.', "").lowercase()
        val extFromUrl = MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase()
        val resolvedExt = extFromName.ifBlank { extFromUrl }

        val rawMime = resolver.getType(uri)
            ?: extensionMimeMap[resolvedExt]
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(resolvedExt)
            ?: "application/octet-stream"

        val maxBytes = maxUploadMb * 1024L * 1024L
        if (size > maxBytes) {
            return Result.failure(
                IllegalArgumentException("File exceeds ${maxUploadMb}MB limit (${size / (1024 * 1024)}MB).")
            )
        }

        val isAllowed = allowedMimePrefixes.any { rawMime.startsWith(it) } ||
            rawMime in allowedDocMimes
        if (!isAllowed) {
            return Result.failure(IllegalArgumentException("Unsupported MIME type: $rawMime"))
        }

        val category = when {
            rawMime.startsWith("image/") -> "image"
            rawMime.startsWith("audio/") -> "voice"
            rawMime.startsWith("video/") -> "video"
            else -> "document"
        }

        return Result.success(MediaValidationResult(name, rawMime, size, category))
    }

    suspend fun uploadMedia(
        uri: Uri,
        conversationId: String = "personal",
        compressImage: Boolean = true,
        maxUploadMb: Long = AppConstants.DEFAULT_MAX_UPLOAD_MB,
        durationMs: Long = 0L,
        onProgress: (Float) -> Unit = {}
    ): Result<UploadedMediaResult> = withContext(Dispatchers.IO) {
        val validation = validateUri(uri, maxUploadMb).getOrElse {
            return@withContext Result.failure(it)
        }

        val mediaId = "m_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val prepared = prepareOptimizedAndLocalFiles(
            uri = uri,
            mediaId = mediaId,
            conversationId = conversationId,
            validation = validation,
            compressImage = compressImage
        ).getOrElse { return@withContext Result.failure(it) }

        if (!isConfigured()) {
            // Keep clean fail-fast error rather than fabricating remote URLs
            prepared.cleanupTempIfNotPermanent()
            return@withContext Result.failure(
                SupabaseNotConfiguredException(
                    "Supabase Storage is not configured. Set SUPABASE_URL, SUPABASE_ANON_KEY, and SUPABASE_MEDIA_BRIDGE_URL in the AI Studio Secrets panel."
                )
            )
        }

        val bridgeUrl = AppConstants.supabaseMediaBridgeUrl
        val expiresAtMs = System.currentTimeMillis() + AppConstants.MEDIA_RETENTION_MILLIS

        val uploadRes = if (bridgeUrl.isNotBlank()) {
            uploadViaAuthBridge(
                bridgeUrl = bridgeUrl,
                mediaId = mediaId,
                conversationId = conversationId,
                prepared = prepared,
                onProgress = onProgress
            )
        } else {
            uploadViaSupabaseStorageRest(
                mediaId = mediaId,
                conversationId = conversationId,
                prepared = prepared,
                expiresAtMs = expiresAtMs,
                onProgress = onProgress
            )
        }

        uploadRes.onSuccess { res ->
            if (res.category != "image" && res.localFilePath.isNotBlank()) {
                File(res.localFilePath).delete()
            }
            localRepo.saveLocalMediaRecord(
                LocalMediaEntity(
                    mediaId = res.mediaId,
                    messageId = "",
                    conversationId = conversationId,
                    mediaType = res.category,
                    localFilePath = res.localFilePath.takeIf { res.category == "image" }.orEmpty(),
                    localThumbPath = res.localThumbPath,
                    remotePath = res.remotePath,
                    mimeType = res.mimeType,
                    fileName = res.fileName,
                    sizeBytes = res.sizeBytes,
                    durationMs = durationMs,
                    createdAtMs = System.currentTimeMillis(),
                    expiresAtMs = res.expiresAtMs,
                    cloudDeleted = false,
                    isRestoredFromBackup = false
                )
            )
        }

        uploadRes
    }

    private data class PreparedLocalFiles(
        val permanentFile: File,
        val thumbnailFile: File?,
        val finalFileName: String,
        val finalMimeType: String,
        val category: String
    ) {
        fun cleanupTempIfNotPermanent() {
            // Keep permanentFile safe only after confirmed upload; delete failed staging file
            runCatching { permanentFile.delete() }
            runCatching { thumbnailFile?.delete() }
        }
    }

    private fun prepareOptimizedAndLocalFiles(
        uri: Uri,
        mediaId: String,
        conversationId: String,
        validation: MediaValidationResult,
        compressImage: Boolean
    ): Result<PreparedLocalFiles> = runCatching {
        val safeConv = conversationId.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "personal" }
        val baseDir = if (validation.category == "image") localMediaRootDir() else File(context.cacheDir, "pending_media")
        val convDir = File(baseDir, safeConv).apply { mkdirs() }

        if (validation.category == "image") {
            val optimized = optimizeImageToWebP(uri, compressImage)
            if (optimized != null) {
                val (imageBytes, thumbBytes) = optimized
                val destFile = File(convDir, "$mediaId.webp")
                FileOutputStream(destFile).use { it.write(imageBytes) }
                val thumbFile = thumbBytes?.let { tb ->
                    File(convDir, "${mediaId}_thumb.webp").also { tf ->
                        FileOutputStream(tf).use { it.write(tb) }
                    }
                }
                val baseName = validation.fileName.substringBeforeLast('.').ifBlank { mediaId }
                return@runCatching PreparedLocalFiles(
                    permanentFile = destFile,
                    thumbnailFile = thumbFile,
                    finalFileName = "$baseName.webp",
                    finalMimeType = "image/webp",
                    category = "image"
                )
            }
        }

        val ext = validation.fileName.substringAfterLast('.', "").lowercase()
            .ifBlank { if (validation.category == "voice") "m4a" else "bin" }
        val destFile = File(convDir, "$mediaId.$ext")
        if (uri.scheme == "file") {
            File(uri.path!!).copyTo(destFile, overwrite = true)
        } else {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            } ?: error("Unable to read selected file.")
        }
        PreparedLocalFiles(
            permanentFile = destFile,
            thumbnailFile = null,
            finalFileName = validation.fileName,
            finalMimeType = validation.mimeType,
            category = validation.category
        )
    }

    /**
     * Converts image to WebP, applies Exif orientation, preserves aspect ratio <= 1600px,
     * and targets 100KB-400KB without excessive blur, plus a 320px WebP thumbnail.
     */
    internal fun optimizeImageToWebP(uri: Uri, compressImage: Boolean): Pair<ByteArray, ByteArray?>? {
        val rawBitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return null

        val rotatedBitmap = applyExifRotation(uri, rawBitmap)
        val maxDim = if (compressImage) 1600 else 2048
        val mainScaled = scalePreservingAspectRatio(rotatedBitmap, maxDim)

        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

        val mainBytes = if (compressImage) {
            var bestBytes: ByteArray? = null
            for (q in listOf(84, 78, 72)) {
                val baos = ByteArrayOutputStream()
                mainScaled.compress(format, q, baos)
                val bytes = baos.toByteArray()
                bestBytes = bytes
                if (bytes.size <= 400 * 1024) break
            }
            bestBytes ?: ByteArrayOutputStream().apply { mainScaled.compress(format, 75, this) }.toByteArray()
        } else {
            ByteArrayOutputStream().apply { mainScaled.compress(format, 92, this) }.toByteArray()
        }

        val thumbBitmap = scalePreservingAspectRatio(mainScaled, 320)
        val thumbBytes = ByteArrayOutputStream().apply {
            thumbBitmap.compress(format, 72, this)
        }.toByteArray()

        if (thumbBitmap !== mainScaled) thumbBitmap.recycle()
        if (mainScaled !== rotatedBitmap) mainScaled.recycle()
        if (rotatedBitmap !== rawBitmap) rotatedBitmap.recycle()
        rawBitmap.recycle()

        return mainBytes to thumbBytes
    }

    private fun scalePreservingAspectRatio(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest.toFloat()
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private suspend fun uploadViaAuthBridge(
        bridgeUrl: String,
        mediaId: String,
        conversationId: String,
        prepared: PreparedLocalFiles,
        onProgress: (Float) -> Unit
    ): Result<UploadedMediaResult> {
        val idToken = runCatching {
            val user = Firebase.auth.currentUser
            if (user != null) Tasks.await(user.getIdToken(false)).token else null
        }.getOrNull() ?: return Result.failure(IllegalStateException("Sign in required before uploading media."))

        val reqJson = JSONObject()
            .put("action", "sign_upload")
            .put("mediaId", mediaId)
            .put("conversationId", conversationId)
            .put("category", prepared.category)
            .put("mimeType", prepared.finalMimeType)
            .put("fileName", prepared.finalFileName)
            .put("sizeBytes", prepared.permanentFile.length())
            .put("hasThumbnail", prepared.thumbnailFile != null)

        val bridgeReq = Request.Builder()
            .url(bridgeUrl)
            .header("Authorization", "Bearer $idToken")
            .post(reqJson.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        val bridgeJson = executeCancellable(client.newCall(bridgeReq)) { JSONObject(it) }
            .getOrElse { return Result.failure(it) }

        val uploadUrl = bridgeJson.optString("uploadUrl")
        val thumbUploadUrl = bridgeJson.optString("thumbUploadUrl")
        val readUrl = bridgeJson.optString("readUrl")
        val thumbReadUrl = bridgeJson.optString("thumbReadUrl")
        val objectPath = bridgeJson.optString("objectPath")
        val expiresAtMs = bridgeJson.optLong(
            "expiresAtMs",
            System.currentTimeMillis() + AppConstants.MEDIA_RETENTION_MILLIS
        )

        if (uploadUrl.isBlank() || readUrl.isBlank()) {
            return Result.failure(IllegalStateException("Bridge returned incomplete Supabase signed URLs."))
        }

        // Upload main optimized object via signed URL
        val mainPutReq = Request.Builder()
            .url(uploadUrl)
            .put(
                ProgressFileRequestBody(
                    file = prepared.permanentFile,
                    contentType = prepared.finalMimeType.toMediaTypeOrNull(),
                    onProgress = onProgress
                )
            )
            .build()
        executeCancellable(client.newCall(mainPutReq)) { it }
            .getOrElse { return Result.failure(it) }

        // Upload lightweight thumbnail if generated
        if (prepared.thumbnailFile != null && thumbUploadUrl.isNotBlank()) {
            val thumbPutReq = Request.Builder()
                .url(thumbUploadUrl)
                .put(prepared.thumbnailFile.asRequestBody("image/webp".toMediaTypeOrNull()))
                .build()
            runCatching { executeCancellable(client.newCall(thumbPutReq)) { it } }
        }

        return Result.success(
            UploadedMediaResult(
                mediaId = mediaId,
                secureUrl = readUrl,
                thumbnailUrl = thumbReadUrl,
                remotePath = objectPath,
                localFilePath = prepared.permanentFile.absolutePath,
                localThumbPath = prepared.thumbnailFile?.absolutePath.orEmpty(),
                fileName = prepared.finalFileName,
                mimeType = prepared.finalMimeType,
                sizeBytes = prepared.permanentFile.length(),
                expiresAtMs = expiresAtMs,
                category = prepared.category
            )
        )
    }

    private suspend fun uploadViaSupabaseStorageRest(
        mediaId: String,
        conversationId: String,
        prepared: PreparedLocalFiles,
        expiresAtMs: Long,
        onProgress: (Float) -> Unit
    ): Result<UploadedMediaResult> {
        val uid = Firebase.auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User must be signed in."))
        val supabaseUrl = AppConstants.supabaseUrl
        val anonKey = AppConstants.supabaseAnonKey
        if (supabaseUrl.isBlank() || anonKey.isBlank()) {
            return Result.failure(SupabaseNotConfiguredException("Supabase URL/Anon Key missing."))
        }

        val safeConv = conversationId.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "personal" }
        val ext = prepared.finalFileName.substringAfterLast('.', "webp")
        val objectPath = "$uid/$safeConv/$mediaId.$ext"
        val endpoint = "$supabaseUrl/storage/v1/object/${AppConstants.SUPABASE_BUCKET}/$objectPath"

        val putReq = Request.Builder()
            .url(endpoint)
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .header("x-upsert", "true")
            .post(
                ProgressFileRequestBody(
                    file = prepared.permanentFile,
                    contentType = prepared.finalMimeType.toMediaTypeOrNull(),
                    onProgress = onProgress
                )
            )
            .build()

        executeCancellable(client.newCall(putReq)) { it }
            .getOrElse { return Result.failure(it) }

        var thumbUrl = ""
        if (prepared.thumbnailFile != null) {
            val thumbPath = "$uid/$safeConv/${mediaId}_thumb.webp"
            val thumbEndpoint = "$supabaseUrl/storage/v1/object/${AppConstants.SUPABASE_BUCKET}/$thumbPath"
            val thumbReq = Request.Builder()
                .url(thumbEndpoint)
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $anonKey")
                .header("x-upsert", "true")
                .post(prepared.thumbnailFile.asRequestBody("image/webp".toMediaTypeOrNull()))
                .build()
            if (executeCancellable(client.newCall(thumbReq)) { it }.isSuccess) {
                thumbUrl = thumbEndpoint
            }
        }

        return Result.success(
            UploadedMediaResult(
                mediaId = mediaId,
                secureUrl = endpoint,
                thumbnailUrl = thumbUrl,
                remotePath = objectPath,
                localFilePath = prepared.permanentFile.absolutePath,
                localThumbPath = prepared.thumbnailFile?.absolutePath.orEmpty(),
                fileName = prepared.finalFileName,
                mimeType = prepared.finalMimeType,
                sizeBytes = prepared.permanentFile.length(),
                expiresAtMs = expiresAtMs,
                category = prepared.category
            )
        )
    }

    /**
     * Downloads a message's media into BOTH persistent internal local storage (survives 7-day cloud wipe)
     * AND the user-visible Downloads/PrivacyX directory via scoped MediaStore (zero broad permissions).
     */
    suspend fun cachePhotoLocally(message: ChatMessage): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(message.mediaType == "image") { "Only photos are cached automatically." }
            val mediaId = message.mediaId.ifBlank { message.id }
            localRepo.findLocalMedia(mediaId, message.id)?.let { existing ->
                val file = File(existing.localFilePath)
                if (file.exists()) return@runCatching file.absolutePath
            }
            val expired = message.mediaExpired || message.mediaUrl.isBlank() ||
                (message.mediaExpiresAt > 0L && message.mediaExpiresAt <= System.currentTimeMillis())
            check(!expired) { "Photo has expired from cloud and has no local copy." }

            val safeConv = message.conversationId.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "saved" }
            val convDir = File(localMediaRootDir(), safeConv).apply { mkdirs() }
            val ext = message.fileName.substringAfterLast('.', "webp").lowercase().ifBlank { "webp" }
            val dest = File(convDir, "${mediaId}.$ext")
            val request = Request.Builder().url(message.mediaUrl).get().apply {
                if (AppConstants.supabaseAnonKey.isNotBlank() && message.mediaUrl.contains("supabase")) {
                    header("apikey", AppConstants.supabaseAnonKey)
                }
            }.build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Photo download failed (HTTP ${response.code})." }
                val body = response.body ?: error("Empty photo response.")
                body.byteStream().use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                }
            }
            localRepo.saveLocalMediaRecord(
                LocalMediaEntity(
                    mediaId = mediaId,
                    messageId = message.id,
                    conversationId = message.conversationId,
                    mediaType = "image",
                    localFilePath = dest.absolutePath,
                    localThumbPath = "",
                    remotePath = "",
                    mimeType = message.mimeType.ifBlank { "image/webp" },
                    fileName = message.fileName.ifBlank { dest.name },
                    sizeBytes = dest.length(),
                    createdAtMs = System.currentTimeMillis(),
                    expiresAtMs = message.mediaExpiresAt,
                    cloudDeleted = false,
                    downloadedToDevice = false
                )
            )
            dest.absolutePath
        }
    }

    suspend fun downloadMediaToDevice(
        message: ChatMessage,
        onProgress: (Float) -> Unit = {}
    ): Result<DownloadMediaResult> = withContext(Dispatchers.IO) {
        runCatching {
            val effectiveMediaId = message.mediaId.ifBlank { message.id }
            val existingLocal = localRepo.findLocalMedia(effectiveMediaId, message.id)
            val safeConv = message.conversationId.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "saved" }
            val ext = message.fileName.substringAfterLast('.', "").lowercase()
                .ifBlank { if (message.mediaType == "voice") "m4a" else if (message.mediaType == "image") "webp" else "bin" }

            val convDir = File(localMediaRootDir(), safeConv).apply { mkdirs() }
            val internalDest = File(convDir, "${effectiveMediaId}.$ext")

            if (existingLocal != null && File(existingLocal.localFilePath).exists()) {
                val src = File(existingLocal.localFilePath)
                if (src.absolutePath != internalDest.absolutePath) {
                    src.copyTo(internalDest, overwrite = true)
                }
                onProgress(0.6f)
            } else {
                val url = message.mediaUrl
                val expired = message.mediaExpired || url.isBlank() ||
                    (message.mediaExpiresAt > 0L && message.mediaExpiresAt <= System.currentTimeMillis())
                if (expired) {
                    error("Media expired from cloud after 7 days and no local copy was saved.")
                }
                val reqBuilder = Request.Builder().url(url).get()
                if (AppConstants.supabaseAnonKey.isNotBlank() && url.contains("supabase")) {
                    reqBuilder.header("apikey", AppConstants.supabaseAnonKey)
                }
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        error("Download failed (HTTP ${resp.code})")
                    }
                    val body = resp.body ?: error("Empty download response")
                    val total = body.contentLength().coerceAtLeast(1L)
                    body.byteStream().use { input ->
                        FileOutputStream(internalDest).use { output ->
                            val buf = ByteArray(8192)
                            var copied = 0L
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                copied += read
                                onProgress((copied.toFloat() / total.toFloat()).coerceIn(0f, 0.85f))
                            }
                        }
                    }
                }
            }

            val finalFileName = message.fileName.ifBlank { "privacyx_${effectiveMediaId}.$ext" }
            val mime = message.mimeType.ifBlank {
                extensionMimeMap[ext] ?: if (message.mediaType == "voice") "audio/mp4" else "image/webp"
            }

            val exportedPath = exportFileToDownloads(internalDest, finalFileName, mime)
            onProgress(1.0f)

            localRepo.saveLocalMediaRecord(
                LocalMediaEntity(
                    mediaId = effectiveMediaId,
                    messageId = message.id,
                    conversationId = message.conversationId,
                    mediaType = message.mediaType,
                    localFilePath = internalDest.absolutePath,
                    localThumbPath = existingLocal?.localThumbPath.orEmpty(),
                    remotePath = existingLocal?.remotePath.orEmpty(),
                    mimeType = mime,
                    fileName = finalFileName,
                    sizeBytes = internalDest.length(),
                    durationMs = message.durationMs,
                    createdAtMs = existingLocal?.createdAtMs ?: System.currentTimeMillis(),
                    expiresAtMs = message.mediaExpiresAt.takeIf { it > 0L }
                        ?: existingLocal?.expiresAtMs
                        ?: (System.currentTimeMillis() + AppConstants.MEDIA_RETENTION_MILLIS),
                    cloudDeleted = message.mediaExpired,
                    isRestoredFromBackup = existingLocal?.isRestoredFromBackup ?: false,
                    downloadedToDevice = true,
                    downloadedAtMs = System.currentTimeMillis()
                )
            )

            DownloadMediaResult(
                localFilePath = internalDest.absolutePath,
                exportedDisplayPath = exportedPath,
                bytesWritten = internalDest.length()
            )
        }
    }

    private fun exportFileToDownloads(source: File, fileName: String, mimeType: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/PrivacyX")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return "Downloads/PrivacyX/$fileName"
            }
        }
        val fallbackDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "PrivacyX").apply { mkdirs() }
        val dest = File(fallbackDir, fileName)
        source.copyTo(dest, overwrite = true)
        return dest.absolutePath
    }

    /**
     * Clears ONLY temporary cache (`cacheDir`). Permanent local media (`filesDir/local_media`) and wallpapers are preserved.
     */
    suspend fun clearTemporaryCacheOnly(): Long = withContext(Dispatchers.IO) {
        val bytes = context.cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        bytes
    }

    private suspend fun <T> executeCancellable(
        call: Call,
        parser: (String) -> T
    ): Result<T> = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { call.cancel() }
        try {
            call.execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    cont.resume(Result.failure(IllegalStateException("Storage HTTP ${response.code}: $raw")))
                } else {
                    cont.resume(Result.success(parser(raw)))
                }
            }
        } catch (e: Exception) {
            if (cont.isActive) cont.resume(Result.failure(e))
        }
    }

    private class ProgressFileRequestBody(
        private val file: File,
        private val contentType: okhttp3.MediaType?,
        private val onProgress: (Float) -> Unit
    ) : RequestBody() {
        override fun contentType(): okhttp3.MediaType? = contentType
        override fun contentLength(): Long = file.length()
        override fun writeTo(sink: BufferedSink) {
            val total = file.length().coerceAtLeast(1L)
            var uploaded = 0L
            file.source().use { source ->
                val buffer = okio.Buffer()
                var read: Long
                while (source.read(buffer, 8192L).also { read = it } != -1L) {
                    sink.write(buffer, read)
                    uploaded += read
                    onProgress((uploaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                }
            }
        }
    }
}
