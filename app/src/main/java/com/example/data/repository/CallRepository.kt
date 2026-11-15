package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.CallSession
import com.example.data.model.IceCandidateDoc
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CallRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeMyCalls(userId: String): Flow<List<CallSession>> = flow {
        val query = db.collection("calls")
            .whereArrayContains("participantIds", userId)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        CallSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    if (auth.currentUser?.uid == userId) {
                        handleFirestoreError(error, OperationType.LIST, "calls")
                    }
                    throw error
                }
        )
    }

    fun observeCall(callId: String): Flow<CallSession?> = flow {
        val ref = db.collection("calls").document(callId)
        emitAll(
            ref.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            CallSession::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.GET, ref.path)
                    throw error
                }
        )
    }

    fun observeIceCandidates(callId: String): Flow<List<IceCandidateDoc>> = flow {
        val uid = requireUserId()
        val query = db.collection("calls").document(callId)
            .collection("candidates")
            .whereArrayContains("participantIds", uid)
        emitAll(
            query.snapshots()
                .map { snap -> snap.toObjects(IceCandidateDoc::class.java) }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "calls/$callId/candidates")
                    throw error
                }
        )
    }

    suspend fun createOutgoingCall(
        callerName: String,
        participantIds: List<String>,
        conversationId: String,
        callType: String,
        offerSdp: String
    ): Result<String> {
        val uid = requireUserId()
        val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val ref = db.collection("calls").document(callId)
        val participants = (listOf(uid) + participantIds).distinct().take(16)
        return try {
            ref.set(
                mapOf(
                    "callerId" to uid,
                    "callerName" to callerName.ifEmpty { "User" }.take(80),
                    "conversationId" to conversationId.take(128),
                    "participantIds" to participants,
                    "callType" to callType,
                    "status" to "ringing",
                    "offerSdp" to offerSdp.take(32000),
                    "answerSdp" to "",
                    "durationSeconds" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(callId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun answerCall(callId: String, answerSdp: String): Result<Unit> {
        val ref = db.collection("calls").document(callId)
        return try {
            ref.update(
                mapOf(
                    "status" to "active",
                    "answerSdp" to answerSdp.take(32000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun updateCallStatus(
        callId: String,
        status: String,
        durationSeconds: Long = 0L
    ): Result<Unit> {
        val ref = db.collection("calls").document(callId)
        return try {
            ref.update(
                mapOf(
                    "status" to status,
                    "durationSeconds" to durationSeconds,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun addIceCandidate(
        callId: String,
        participantIds: List<String>,
        sdpMid: String,
        sdpMLineIndex: Int,
        candidate: String
    ) {
        val uid = auth.currentUser?.uid ?: return
        val candId = "ice_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val ref = db.collection("calls").document(callId).collection("candidates").document(candId)
        try {
            ref.set(
                mapOf(
                    "senderId" to uid,
                    "participantIds" to participantIds.take(16),
                    "sdpMid" to sdpMid.take(64),
                    "sdpMLineIndex" to sdpMLineIndex.toLong(),
                    "candidate" to candidate.take(4000),
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
