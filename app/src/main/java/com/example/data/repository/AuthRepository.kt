package com.example.data.repository

import android.content.Context
import android.os.Build
import com.example.R
import com.example.core.AppConstants
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.UserPrivateAccount
import com.example.data.model.UserProfile
import com.example.data.model.UserSession
import com.example.data.model.UserSettings
import com.example.data.model.UsernameReservation
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class AuthRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            UserProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observePrivateAccount(userId: String): Flow<UserPrivateAccount?> = flow {
        val docRef = db.collection("users").document(userId).collection("private").document("account")
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            UserPrivateAccount::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeUserSettings(userId: String): Flow<UserSettings> = flow {
        val docRef = db.collection("users").document(userId)
            .collection("userSettings").document("preferences")
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            UserSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: UserSettings(userId = userId)
                    } else {
                        UserSettings(userId = userId)
                    }
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(error, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeSessions(userId: String): Flow<List<UserSession>> = flow {
        val colRef = db.collection("users").document(userId).collection("sessions")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
        emitAll(
            colRef.snapshots()
                .map { snap ->
                    snap.toObjects(
                        UserSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(error, OperationType.LIST, "users/$userId/sessions")
                    }
                    throw error
                }
        )
    }

    suspend fun getUserProfile(userId: String): UserProfile? {
        val path = "users/$userId"
        return try {
            val snap = db.document(path).get().await()
            if (snap.exists()) {
                snap.toObject(
                    UserProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            null
        }
    }

    suspend fun isUsernameAvailable(rawUsername: String): Boolean {
        val normalized = normalizeUsername(rawUsername)
        if (!isValidUsernameFormat(normalized)) return false
        val docRef = db.collection("usernames").document(normalized)
        return try {
            !docRef.get().await().exists()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            false
        }
    }

    suspend fun claimUsernameAndCreateProfile(
        displayName: String,
        rawUsername: String,
        bio: String,
        photoUrl: String,
        email: String
    ): Result<Unit> {
        val uid = requireUserId()
        val username = normalizeUsername(rawUsername)
        if (!isValidUsernameFormat(username)) {
            return Result.failure(
                IllegalArgumentException("Username must be 3-30 lowercase letters, numbers, or underscores.")
            )
        }
        val safeDisplayName = displayName.trim().ifEmpty { username }.take(60)
        val userRef = db.collection("users").document(uid)
        val usernameRef = db.collection("usernames").document(username)
        val privateRef = userRef.collection("private").document("account")
        val settingsRef = userRef.collection("userSettings").document("preferences")

        return try {
            db.runTransaction { tx ->
                val existingUsername = tx.get(usernameRef)
                if (existingUsername.exists() && existingUsername.getString("userId") != uid) {
                    throw IllegalStateException("Username @$username is already taken.")
                }

                val existingProfile = tx.get(userRef)
                val oldUsername = existingProfile.getString("username")

                tx.set(
                    usernameRef,
                    mapOf(
                        "username" to username,
                        "userId" to uid,
                        "displayName" to safeDisplayName,
                        "photoUrl" to photoUrl.take(1000),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                if (!oldUsername.isNullOrBlank() && oldUsername != username) {
                    tx.delete(db.collection("usernames").document(oldUsername))
                }

                val profileData = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "username" to username,
                    "displayName" to safeDisplayName,
                    "photoUrl" to photoUrl.take(1000),
                    "bio" to bio.trim().take(300),
                    "statusText" to "Available",
                    "isOnline" to true,
                    "lastSeen" to FieldValue.serverTimestamp(),
                    "isSuspended" to false,
                    "profileVisibility" to "everyone",
                    "lastSeenPrivacy" to "everyone",
                    "storyPrivacy" to "public",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!existingProfile.exists()) {
                    profileData["createdAt"] = FieldValue.serverTimestamp()
                    tx.set(userRef, profileData)
                } else {
                    tx.set(userRef, profileData, SetOptions.merge())
                }

                tx.set(
                    privateRef,
                    mapOf(
                        "userId" to uid,
                        "email" to email.take(256),
                        "closeFriendsIds" to emptyList<String>(),
                        "hiddenStoryUserIds" to emptyList<String>(),
                        "savedPostIds" to emptyList<String>(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )

                tx.set(
                    settingsRef,
                    mapOf(
                        "userId" to uid,
                        "themeId" to "material_liquid_glass",
                        "appearanceMode" to "system",
                        "accentColorHex" to "",
                        "reducedMotion" to false,
                        "notificationsEnabled" to true,
                        "quietHoursEnabled" to false,
                        "soundEnabled" to true,
                        "vibrationEnabled" to true,
                        "showNotificationPreview" to true,
                        "autoDownloadWifi" to true,
                        "autoDownloadCellular" to false,
                        "aiConsentGranted" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }.await()

            registerCurrentSession(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        displayName: String,
        bio: String,
        statusText: String,
        photoUrl: String,
        profileVisibility: String,
        lastSeenPrivacy: String,
        storyPrivacy: String,
        customAccentHex: String
    ): Result<Unit> {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        return try {
            val profile = userRef.get().await().toObject(UserProfile::class.java)
            val updates = mapOf(
                "displayName" to displayName.trim().ifEmpty { profile?.username ?: "User" }.take(60),
                "bio" to bio.trim().take(300),
                "statusText" to statusText.trim().ifEmpty { "Available" }.take(100),
                "photoUrl" to photoUrl.trim().take(1000),
                "profileVisibility" to profileVisibility,
                "lastSeenPrivacy" to lastSeenPrivacy,
                "storyPrivacy" to storyPrivacy,
                "customAccentHex" to customAccentHex.take(16),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            userRef.update(updates).await()
            if (!profile?.username.isNullOrBlank()) {
                db.collection("usernames").document(profile!!.username).update(
                    mapOf(
                        "displayName" to (updates["displayName"] as String),
                        "photoUrl" to (updates["photoUrl"] as String)
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun updatePresence(isOnline: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(uid)
        try {
            val snap = userRef.get().await()
            if (snap.exists()) {
                userRef.update(
                    mapOf(
                        "isOnline" to isOnline,
                        "lastSeen" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
        }
    }

    suspend fun updateUserSettings(settings: UserSettings): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("userSettings").document("preferences")
        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "themeId" to settings.themeId,
                    "appearanceMode" to settings.appearanceMode,
                    "accentColorHex" to settings.accentColorHex,
                    "reducedMotion" to settings.reducedMotion,
                    "notificationsEnabled" to settings.notificationsEnabled,
                    "quietHoursEnabled" to settings.quietHoursEnabled,
                    "soundEnabled" to settings.soundEnabled,
                    "vibrationEnabled" to settings.vibrationEnabled,
                    "showNotificationPreview" to settings.showNotificationPreview,
                    "autoDownloadWifi" to settings.autoDownloadWifi,
                    "autoDownloadCellular" to settings.autoDownloadCellular,
                    "aiConsentGranted" to settings.aiConsentGranted,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun registerCurrentSession(uid: String = requireUserId()) {
        val sessionId = "${Build.MODEL}_${Build.DEVICE}"
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            .take(64)
            .ifEmpty { "android_session" }
        val docRef = db.collection("users").document(uid).collection("sessions").document(sessionId)
        try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(100),
                    "platform" to "Android ${Build.VERSION.RELEASE}",
                    "isActive" to true,
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
        }
    }

    suspend fun revokeSession(sessionId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("sessions").document(sessionId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun searchUsers(queryText: String): List<UsernameReservation> {
        val normalized = queryText.trim().lowercase().removePrefix("@")
        return try {
            val snap = db.collection("usernames")
                .orderBy("username")
                .limit(50)
                .get()
                .await()
            val all = snap.toObjects(UsernameReservation::class.java)
            val currentUid = auth.currentUser?.uid
            all.filter {
                it.userId != currentUid && (
                    normalized.isEmpty() ||
                        it.username.contains(normalized, ignoreCase = true) ||
                        it.displayName.contains(queryText.trim(), ignoreCase = true)
                    )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "usernames")
            emptyList()
        }
    }

    suspend fun exportUserDataJson(): Result<String> {
        val uid = requireUserId()
        return try {
            val profile = db.collection("users").document(uid).get().await().data ?: emptyMap<String, Any>()
            val priv = db.collection("users").document(uid).collection("private").document("account").get().await().data ?: emptyMap<String, Any>()
            val settings = db.collection("users").document(uid).collection("userSettings").document("preferences").get().await().data ?: emptyMap<String, Any>()
            val saved = db.collection("users").document(uid).collection("savedMessages").get().await().documents.map { it.data }

            val root = JSONObject().apply {
                put("exportedAt", System.currentTimeMillis())
                put("userId", uid)
                put("profile", JSONObject(profile.mapValues { it.value?.toString() ?: "" }))
                put("privateAccount", JSONObject(priv.mapValues { it.value?.toString() ?: "" }))
                put("settings", JSONObject(settings.mapValues { it.value?.toString() ?: "" }))
                put("savedMessages", JSONArray(saved.map { doc -> JSONObject(doc?.mapValues { it.value?.toString() ?: "" } ?: emptyMap<String, String>()) }))
            }
            Result.success(root.toString(2))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(): Result<Unit> {
        val uid = requireUserId()
        return try {
            val userRef = db.collection("users").document(uid)
            val profile = userRef.get().await().toObject(UserProfile::class.java)
            if (!profile?.username.isNullOrBlank()) {
                db.collection("usernames").document(profile!!.username).delete().await()
            }
            userRef.collection("private").document("account").delete().await()
            userRef.collection("userSettings").document("preferences").delete().await()
            userRef.delete().await()
            auth.currentUser?.delete()?.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            Result.failure(e)
        }
    }

    companion object {
        fun normalizeUsername(raw: String): String =
            raw.trim().lowercase().removePrefix("@").replace(Regex("[^a-z0-9_]"), "")

        fun isValidUsernameFormat(username: String): Boolean =
            username.length in 3..30 && username.matches(Regex("^[a-z0-9_]+$"))

        fun isSuperAdmin(uid: String?): Boolean =
            uid == AppConstants.SUPER_ADMIN_UID
    }
}
