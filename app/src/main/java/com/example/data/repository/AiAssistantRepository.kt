package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.AiConversation
import com.example.data.model.AiMessage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID

class AiAssistantRepository(private val db: FirebaseFirestore) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private val model by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeAiSessions(userId: String): Flow<List<AiConversation>> = flow {
        val query = db.collection("users").document(userId)
            .collection("aiConversations")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(30)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        AiConversation::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, "users/$userId/aiConversations")
                    throw error
                }
        )
    }

    fun observeAiMessages(userId: String, sessionId: String): Flow<List<AiMessage>> = flow {
        val path = "users/$userId/aiConversations/$sessionId/messages"
        val query = db.collection("users").document(userId)
            .collection("aiConversations").document(sessionId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(100)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(
                        AiMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    if (error is CancellationException && error.cause == null) throw error
                    handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createAiSession(title: String = "New AI Chat"): String {
        val uid = requireUserId()
        val sessionId = "ai_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val ref = db.collection("users").document(uid)
            .collection("aiConversations").document(sessionId)
        try {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "title" to title.trim().ifEmpty { "AI Assistant" }.take(120),
                    "lastPrompt" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
        }
        return sessionId
    }

    suspend fun saveAiTurn(sessionId: String, role: String, text: String) {
        val uid = requireUserId()
        val sessionRef = db.collection("users").document(uid)
            .collection("aiConversations").document(sessionId)
        val msgId = "aim_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val msgRef = sessionRef.collection("messages").document(msgId)
        try {
            val batch = db.batch()
            batch.set(
                msgRef,
                mapOf(
                    "userId" to uid,
                    "role" to role,
                    "text" to text.take(20000),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            val sessionUpdates = mutableMapOf<String, Any>(
                "userId" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (role == "user") {
                sessionUpdates["title"] = text.take(48).ifEmpty { "AI Assistant" }
                sessionUpdates["lastPrompt"] = text.take(500)
            }
            batch.set(sessionRef, sessionUpdates, SetOptions.merge())
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, msgRef.path)
        }
    }

    fun streamPrompt(
        prompt: String,
        history: List<AiMessage> = emptyList(),
        attachedImage: Bitmap? = null
    ): Flow<String> = flow {
        val sb = StringBuilder()
        val requestContent = content {
            if (history.isNotEmpty()) {
                val contextText = history.takeLast(8).joinToString("\n") {
                    "${if (it.role == "user") "User" else "Assistant"}: ${it.text}"
                }
                text("Conversation context:\n$contextText\n\nCurrent request:")
            }
            if (attachedImage != null) {
                image(attachedImage)
            }
            text(prompt)
        }
        try {
            withTimeout(60_000L) {
                model.generateContentStream(requestContent).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        sb.append(chunkText)
                        emit(sb.toString())
                    }
                }
            }
            if (sb.isBlank()) {
                throw IllegalStateException("AI model returned an empty response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI streaming timed out", e)
            throw IllegalStateException("AI request timed out after 60 seconds. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed", e)
            throw e
        }
    }

    suspend fun generateQuickText(prompt: String): Result<String> {
        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val text = response.text?.trim()
            if (text.isNullOrEmpty()) {
                Result.failure(IllegalStateException("AI model returned an empty response."))
            } else {
                Result.success(text)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI request timed out", e)
            Result.failure(IllegalStateException("AI request timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI call failed", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "AiAssistantRepo"
    }
}
