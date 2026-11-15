package com.example.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.core.AppConstants
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

data class MediaValidationResult(
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val category: String // "image" | "video" | "voice" | "document"
)

data class UploadedMediaResult(
    val secureUrl: String,
    val publicId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val category: String
)

class CloudinaryNotConfiguredException(message: String) : IllegalStateException(message)

class CloudinaryMediaService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val allowedMimePrefixes = listOf("image/", "video/", "audio/")
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
        "mp4" to "video/mp4",
        "mov" to "video/quicktime",
        "webm" to "video/webm",
        "mkv" to "video/x-matroska",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "webp" to "image/webp",
        "gif" to "image/gif",
        "heic" to "image/heic",
        "pdf" to "application/pdf",
        "txt" to "text/plain",
        "zip" to "application/zip"
    )

    fun isConfigured(): Boolean =
        AppConstants.cloudinaryCloudName.isNotBlank() &&
            (AppConstants.cloudinarySigningEndpoint.isNotBlank() ||
                AppConstants.cloudinaryUploadPreset.isNotBlank())

    fun saveLocalWallpaperCopy(uri: Uri, conversationId: String): Result<String> = runCatching {
        val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
        val safeId = conversationId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val dest = File(dir, "wallpaper_${safeId}_${System.currentTimeMillis()}.jpg")
        // Remove previous wallpaper files for this conversation to prevent orphan buildup
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
            return Result.failure(
                IllegalArgumentException("Unsupported MIME type: $rawMime")
            )
        }

        val category = when {
            rawMime.startsWith("image/") -> "image"
            rawMime.startsWith("video/") -> "video"
            rawMime.startsWith("audio/") -> "voice"
            else -> "document"
        }

        return Result.success(MediaValidationResult(name, rawMime, size, category))
    }

    suspend fun uploadMedia(
        uri: Uri,
        compressImage: Boolean = true,
        maxUploadMb: Long = AppConstants.DEFAULT_MAX_UPLOAD_MB,
        onProgress: (Float) -> Unit = {}
    ): Result<UploadedMediaResult> = withContext(Dispatchers.IO) {
        val validation = validateUri(uri, maxUploadMb).getOrElse {
            return@withContext Result.failure(it)
        }

        if (!isConfigured()) {
            return@withContext Result.failure(
                CloudinaryNotConfiguredException(
                    "Cloudinary credentials are not configured. Set CLOUDINARY_CLOUD_NAME and CLOUDINARY_SIGNING_ENDPOINT (or CLOUDINARY_UPLOAD_PRESET) in the AI Studio Secrets panel."
                )
            )
        }

        val uploadFile = prepareUploadFile(uri, validation, compressImage).getOrElse {
            return@withContext Result.failure(it)
        }

        try {
            val cloudName = AppConstants.cloudinaryCloudName.trim()
            val endpoint = "https://api.cloudinary.com/v1_1/$cloudName/auto/upload"

            val formBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)

            val signingEndpoint = AppConstants.cloudinarySigningEndpoint.trim()
            if (signingEndpoint.isNotBlank()) {
                val signaturePayload = fetchUploadSignature(signingEndpoint, validation.category)
                    .getOrElse { return@withContext Result.failure(it) }
                formBuilder.addFormDataPart("api_key", signaturePayload.apiKey)
                formBuilder.addFormDataPart("timestamp", signaturePayload.timestamp)
                formBuilder.addFormDataPart("signature", signaturePayload.signature)
                formBuilder.addFormDataPart("folder", signaturePayload.folder)
            } else {
                formBuilder.addFormDataPart("upload_preset", AppConstants.cloudinaryUploadPreset.trim())
            }

            val progressBody = ProgressFileRequestBody(
                file = uploadFile,
                contentType = validation.mimeType.toMediaTypeOrNull(),
                onProgress = onProgress
            )
            formBuilder.addFormDataPart("file", validation.fileName, progressBody)

            val request = Request.Builder()
                .url(endpoint)
                .post(formBuilder.build())
                .build()

            val call = client.newCall(request)
            executeCancellable(call) { responseBody ->
                val json = JSONObject(responseBody)
                val secureUrl = json.optString("secure_url")
                val publicId = json.optString("public_id")
                if (secureUrl.isBlank()) {
                    throw IllegalStateException("Cloudinary response missing secure_url.")
                }
                UploadedMediaResult(
                    secureUrl = secureUrl,
                    publicId = publicId,
                    fileName = validation.fileName,
                    mimeType = validation.mimeType,
                    sizeBytes = uploadFile.length(),
                    category = validation.category
                )
            }
        } finally {
            uploadFile.delete()
        }
    }

    private data class SignedParams(
        val signature: String,
        val timestamp: String,
        val apiKey: String,
        val folder: String
    )

    private fun fetchUploadSignature(signingUrl: String, folderCategory: String): Result<SignedParams> {
        return try {
            val body = JSONObject().put("folder", "privacyx/$folderCategory")
                .toString()
                .toRequestBody("application/json".toMediaTypeOrNull())
            val reqBuilder = Request.Builder().url(signingUrl).post(body)
            val idToken = runCatching {
                val user = Firebase.auth.currentUser
                if (user != null) com.google.android.gms.tasks.Tasks.await(user.getIdToken(false)).token else null
            }.getOrNull()
            if (!idToken.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $idToken")
            }
            val req = reqBuilder.build()
            client.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return Result.failure(IllegalStateException("Signing endpoint failed: HTTP ${resp.code}"))
                }
                val json = JSONObject(raw)
                Result.success(
                    SignedParams(
                        signature = json.getString("signature"),
                        timestamp = json.getString("timestamp"),
                        apiKey = json.getString("apiKey"),
                        folder = json.optString("folder", "privacyx/$folderCategory")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun prepareUploadFile(
        uri: Uri,
        validation: MediaValidationResult,
        compressImage: Boolean
    ): Result<File> {
        return try {
            val tempFile = File.createTempFile("px_upload_", "_${validation.fileName}", context.cacheDir)
            if (validation.category == "image" && compressImage) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                    if (bitmap != null) {
                        val maxDim = 1600
                        val scale = minOf(
                            1f,
                            maxDim.toFloat() / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
                        )
                        val target = if (scale < 1f) {
                            Bitmap.createScaledBitmap(
                                bitmap,
                                (bitmap.width * scale).toInt().coerceAtLeast(1),
                                (bitmap.height * scale).toInt().coerceAtLeast(1),
                                true
                            )
                        } else bitmap
                        val out = ByteArrayOutputStream()
                        target.compress(Bitmap.CompressFormat.JPEG, 82, out)
                        FileOutputStream(tempFile).use { fos -> fos.write(out.toByteArray()) }
                        if (target !== bitmap) target.recycle()
                        bitmap.recycle()
                        return Result.success(tempFile)
                    }
                }
            }

            if (uri.scheme == "file") {
                File(uri.path!!).copyTo(tempFile, overwrite = true)
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                } ?: return Result.failure(IllegalStateException("Unable to read selected file."))
            }
            Result.success(tempFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
                    cont.resume(Result.failure(IllegalStateException("Upload failed (HTTP ${response.code}): $raw")))
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
