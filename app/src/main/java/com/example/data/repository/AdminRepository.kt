package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.AppConstants
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.AbuseReport
import com.example.data.model.AdminLog
import com.example.data.model.Conversation
import com.example.data.model.PlatformAnnouncement
import com.example.data.model.PlatformSettings
import com.example.data.model.UserProfile
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
import java.util.UUID

class AdminRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireSuperAdmin(): String {
        val uid = auth.currentUser?.uid
        check(uid == AppConstants.SUPER_ADMIN_UID) {
            "Unauthorized: Super Admin access is restricted to the designated UID."
        }
        return uid!!
    }

    fun observeAllUsersForAdmin(): Flow<List<UserProfile>> = flow {
        requireSuperAdmin()
        val query = db.collection("users").limit(100)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "users")
                    throw error
                }
        )
    }

    fun observeAllConversationsForAdmin(): Flow<List<Conversation>> = flow {
        requireSuperAdmin()
        val query = db.collection("conversations").limit(100)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        Conversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "conversations")
                    throw error
                }
        )
    }

    fun observeReportsForAdmin(): Flow<List<AbuseReport>> = flow {
        requireSuperAdmin()
        val query = db.collection("reports")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        AbuseReport::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "reports")
                    throw error
                }
        )
    }

    fun observeAdminLogs(): Flow<List<AdminLog>> = flow {
        requireSuperAdmin()
        val query = db.collection("adminLogs")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        AdminLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "adminLogs")
                    throw error
                }
        )
    }

    fun observePlatformAnnouncements(): Flow<List<PlatformAnnouncement>> = flow {
        val query = db.collection("platformAnnouncements")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        PlatformAnnouncement::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "platformAnnouncements")
                    throw error
                }
        )
    }

    fun observePlatformSettings(): Flow<PlatformSettings> = flow {
        val docRef = db.collection("platformSettings").document("global")
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            PlatformSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: PlatformSettings()
                    } else PlatformSettings()
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.GET, docRef.path)
                    throw error
                }
        )
    }

    suspend fun setUserSuspended(targetUid: String, suspend: Boolean): Result<Unit> {
        val adminUid = requireSuperAdmin()
        val userRef = db.collection("users").document(targetUid)
        return try {
            userRef.update(
                mapOf(
                    "isSuspended" to suspend,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordAuditLog(
                actorId = adminUid,
                action = if (suspend) "USER_SUSPENDED" else "USER_RESTORED",
                targetId = targetUid,
                details = if (suspend) "Account suspended by Super Admin" else "Account suspension lifted"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteConversationAsAdmin(conversationId: String, title: String): Result<Unit> {
        val adminUid = requireSuperAdmin()
        val ref = db.collection("conversations").document(conversationId)
        return try {
            ref.delete().await()
            recordAuditLog(
                actorId = adminUid,
                action = "CONVERSATION_REMOVED",
                targetId = conversationId,
                details = "Removed conversation: $title"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun updateReportStatus(reportId: String, newStatus: String): Result<Unit> {
        val adminUid = requireSuperAdmin()
        val ref = db.collection("reports").document(reportId)
        return try {
            ref.update(
                mapOf(
                    "status" to newStatus,
                    "reviewedBy" to adminUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordAuditLog(
                actorId = adminUid,
                action = "REPORT_${newStatus.uppercase()}",
                targetId = reportId,
                details = "Report marked as $newStatus"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun publishAnnouncement(title: String, body: String, priority: String): Result<Unit> {
        val adminUid = requireSuperAdmin()
        val id = "ann_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val ref = db.collection("platformAnnouncements").document(id)
        return try {
            ref.set(
                mapOf(
                    "title" to title.trim().take(140),
                    "body" to body.trim().take(2000),
                    "priority" to priority,
                    "createdBy" to adminUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            recordAuditLog(
                actorId = adminUid,
                action = "ANNOUNCEMENT_PUBLISHED",
                targetId = id,
                details = "Published $priority announcement: ${title.take(80)}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun updatePlatformSettings(
        maxUploadMb: Long,
        allowNewRegistrations: Boolean,
        maintenanceMode: Boolean
    ): Result<Unit> {
        val adminUid = requireSuperAdmin()
        val ref = db.collection("platformSettings").document("global")
        return try {
            ref.set(
                mapOf(
                    "maxUploadMb" to maxUploadMb.coerceIn(1L, 100L),
                    "allowNewRegistrations" to allowNewRegistrations,
                    "maintenanceMode" to maintenanceMode,
                    "updatedBy" to adminUid,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
            recordAuditLog(
                actorId = adminUid,
                action = "SETTINGS_UPDATED",
                targetId = "global",
                details = "maxUploadMb=$maxUploadMb, reg=$allowNewRegistrations, maintenance=$maintenanceMode"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    private suspend fun recordAuditLog(
        actorId: String,
        action: String,
        targetId: String,
        details: String
    ) {
        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val ref = db.collection("adminLogs").document(logId)
        try {
            ref.set(
                mapOf(
                    "actorId" to actorId,
                    "action" to action.take(80),
                    "targetId" to targetId.take(128),
                    "details" to details.take(1000),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
        }
    }
}
