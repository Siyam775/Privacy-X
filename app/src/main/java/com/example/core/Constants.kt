package com.example.core

import com.example.BuildConfig

object AppConstants {
    // Strictly single manually provisioned Super Admin UID (never auto-created, no client escalation)
    const val SUPER_ADMIN_UID = "2bL4lJTTt9WMILpCclHeKNYOdz62"

    const val DEFAULT_MAX_UPLOAD_MB = 25L
    const val STORY_TTL_MILLIS = 24L * 60L * 60L * 1000L
    const val MEDIA_RETENTION_DAYS = 7L
    const val MEDIA_RETENTION_MILLIS = MEDIA_RETENTION_DAYS * 24L * 60L * 60L * 1000L
    const val SUPABASE_BUCKET = "chat-media"
    const val GOOGLE_DRIVE_BACKUP_FOLDER = "My Messaging App Backups"
    const val GOOGLE_DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val MESSAGE_PAGE_SIZE = 40L

    // Public Google STUN servers + optional configured TURN server via Secrets / .env
    val DEFAULT_ICE_SERVERS = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302"
    )

    internal fun cleanSecret(raw: String): String =
        if (raw.isBlank() || raw.startsWith("MY_")) "" else raw.trim()

    val turnServerUrl: String get() = cleanSecret(BuildConfig.TURN_SERVER_URL)
    val turnUsername: String get() = cleanSecret(BuildConfig.TURN_USERNAME)
    val turnCredential: String get() = cleanSecret(BuildConfig.TURN_CREDENTIAL)

    val supabaseUrl: String get() = cleanSecret(BuildConfig.SUPABASE_URL).trimEnd('/')
    val supabaseAnonKey: String get() = cleanSecret(BuildConfig.SUPABASE_ANON_KEY)
    val supabaseMediaBridgeUrl: String get() = cleanSecret(BuildConfig.SUPABASE_MEDIA_BRIDGE_URL).trimEnd('/')
}
