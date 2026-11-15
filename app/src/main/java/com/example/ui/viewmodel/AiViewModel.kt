package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AiConversation
import com.example.data.model.AiMessage
import com.example.data.repository.AiAssistantRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class AiViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val aiRepo = AiAssistantRepository(context)
    private val auth = Firebase.auth

    private val _sessions = MutableStateFlow<List<AiConversation>>(emptyList())
    val sessions: StateFlow<List<AiConversation>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _messages = MutableStateFlow<List<AiMessage>>(emptyList())
    val messages: StateFlow<List<AiMessage>> = _messages.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var sessionsJob: Job? = null
    private var messagesJob: Job? = null
    private var generationJob: Job? = null

    fun bindAiSessions(uid: String) {
        sessionsJob?.cancel()
        sessionsJob = viewModelScope.launch {
            aiRepo.observeAiSessions(uid).catch {}.collect { list ->
                _sessions.value = list
                if (_activeSessionId.value == null && list.isNotEmpty()) {
                    selectSession(list.first().id)
                }
            }
        }
    }

    fun selectSession(sessionId: String) {
        val uid = auth.currentUser?.uid ?: return
        _activeSessionId.value = sessionId
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            aiRepo.observeAiMessages(uid, sessionId)
                .catch { _errorMessage.value = it.localizedMessage }
                .collect { _messages.value = it }
        }
    }

    fun startNewSession() {
        viewModelScope.launch {
            val id = aiRepo.createAiSession("PrivacyX AI Chat")
            selectSession(id)
        }
    }

    fun sendPrompt(
        prompt: String,
        attachedImage: Bitmap? = null,
        consentGranted: Boolean
    ) {
        if (!consentGranted) {
            _errorMessage.value = "Please grant AI Privacy Consent before using Firebase AI Logic."
            return
        }
        val clean = prompt.trim()
        if (clean.isEmpty() && attachedImage == null) return

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _errorMessage.value = null
            _isStreaming.value = true
            _streamingText.value = ""

            val sessionId = _activeSessionId.value ?: aiRepo.createAiSession(clean.take(40)).also {
                selectSession(it)
            }
            val historySnapshot = _messages.value
            val userTextToSave = if (attachedImage != null) "[Image attached] $clean" else clean
            aiRepo.saveAiTurn(sessionId, "user", userTextToSave)

            try {
                var finalAnswer = ""
                aiRepo.streamPrompt(
                    prompt = clean.ifEmpty { "Analyze and explain this image clearly." },
                    history = historySnapshot,
                    attachedImage = attachedImage
                ).collect { partial ->
                    finalAnswer = partial
                    _streamingText.value = partial
                }
                if (finalAnswer.isNotBlank()) {
                    aiRepo.saveAiTurn(sessionId, "model", finalAnswer)
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "AI generation failed."
            } finally {
                _streamingText.value = ""
                _isStreaming.value = false
            }
        }
    }

    fun cancelStreaming() {
        generationJob?.cancel()
        _isStreaming.value = false
        _streamingText.value = ""
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
