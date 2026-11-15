package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CallSession
import com.example.data.repository.CallRepository
import com.example.data.repository.SocialRepository
import com.example.webrtc.WebRtcCallManager
import com.example.webrtc.WebRtcSessionState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.EglBase
import org.webrtc.VideoSink

class CallViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val callRepo = CallRepository(context)
    private val socialRepo = SocialRepository(context)
    val webRtcManager = WebRtcCallManager(context, callRepo)

    private val auth = Firebase.auth

    private val _callHistory = MutableStateFlow<List<CallSession>>(emptyList())
    val callHistory: StateFlow<List<CallSession>> = _callHistory.asStateFlow()

    private val _activeCall = MutableStateFlow<CallSession?>(null)
    val activeCall: StateFlow<CallSession?> = _activeCall.asStateFlow()

    private val _incomingCall = MutableStateFlow<CallSession?>(null)
    val incomingCall: StateFlow<CallSession?> = _incomingCall.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _callNotice = MutableStateFlow<String?>(null)
    val callNotice: StateFlow<String?> = _callNotice.asStateFlow()

    val webrtcState: StateFlow<WebRtcSessionState> = webRtcManager.state
    val eglContext: EglBase.Context get() = webRtcManager.eglBase.eglBaseContext

    private var historyJob: Job? = null
    private var activeCallJob: Job? = null
    private var timerJob: Job? = null

    fun bindCallHistory(uid: String) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            callRepo.observeMyCalls(uid).catch {}.collect { calls ->
                _callHistory.value = calls
                val currentActiveId = _activeCall.value?.id
                val incoming = calls.firstOrNull { call ->
                    call.status == "ringing" &&
                        call.callerId != uid &&
                        call.id != currentActiveId &&
                        (System.currentTimeMillis() - (call.createdAt?.toDate()?.time ?: 0L)) < 90_000L
                }
                _incomingCall.value = incoming
            }
        }
    }

    fun attachVideoSinks(local: VideoSink?, remote: VideoSink?) {
        webRtcManager.attachSinks(local, remote)
    }

    fun startOutgoingCall(
        callerName: String,
        targetUid: String,
        conversationId: String,
        isVideo: Boolean
    ) {
        startOutgoingCall(
            callerName = callerName,
            targetUids = if (targetUid.isNotBlank()) listOf(targetUid) else emptyList(),
            conversationId = conversationId,
            isVideo = isVideo,
            spaceTitle = ""
        )
    }

    fun startOutgoingCall(
        callerName: String,
        targetUids: List<String>,
        conversationId: String,
        isVideo: Boolean,
        spaceTitle: String = ""
    ) {
        val uid = auth.currentUser?.uid ?: return
        val tempParticipants = (listOf(uid) + targetUids.filter { it.isNotBlank() }).distinct().take(16)
        val callType = if (isVideo) "video" else "voice"
        val displayCallerLabel = if (spaceTitle.isNotBlank()) "$callerName • $spaceTitle" else callerName
        val placeholderCall = CallSession(
            id = "pending",
            callerId = uid,
            callerName = displayCallerLabel,
            conversationId = conversationId,
            participantIds = tempParticipants,
            callType = callType,
            status = "ringing"
        )
        _activeCall.value = placeholderCall
        _elapsedSeconds.value = 0L

        viewModelScope.launch {
            var createdCallId: String? = null
            // Create call doc first so ICE candidate subcollection has callId, then update offerSdp
            val createRes = callRepo.createOutgoingCall(
                callerName = displayCallerLabel,
                participantIds = tempParticipants,
                conversationId = conversationId,
                callType = callType,
                offerSdp = ""
            )
            createRes.onSuccess { callId ->
                createdCallId = callId
                targetUids.filter { it.isNotBlank() && it != uid }.distinct().take(15).forEach { peerUid ->
                    socialRepo.sendInAppNotification(
                        targetUserId = peerUid,
                        type = "call",
                        title = if (spaceTitle.isNotBlank()) "Incoming $callType call in $spaceTitle" else "Incoming $callType call",
                        body = "$callerName is calling on PrivacyX",
                        targetId = callId
                    )
                }
                observeActiveCallDoc(callId)
                webRtcManager.startCallSession(
                    callId = callId,
                    currentUserId = uid,
                    participantIds = tempParticipants,
                    isVideo = isVideo,
                    isCaller = true,
                    onOfferCreated = { offerSdp ->
                        viewModelScope.launch {
                            callRepo.updateCallStatus(callId, "ringing")
                            com.google.firebase.firestore.FirebaseFirestore.getInstance(
                                context.getString(com.example.R.string.firestore_database_id)
                            ).collection("calls").document(callId).update("offerSdp", offerSdp)
                        }
                    }
                )
            }.onFailure { err ->
                _activeCall.value = null
                _callNotice.value = err.localizedMessage ?: "Failed to initiate call"
            }
            if (createdCallId == null) {
                _activeCall.value = null
            }
        }
    }

    fun acceptIncomingCall(call: CallSession) {
        val uid = auth.currentUser?.uid ?: return
        _incomingCall.value = null
        _activeCall.value = call
        _elapsedSeconds.value = 0L
        observeActiveCallDoc(call.id)
        startDurationTimer()

        webRtcManager.startCallSession(
            callId = call.id,
            currentUserId = uid,
            participantIds = call.participantIds,
            isVideo = call.callType == "video",
            isCaller = false,
            remoteOfferSdp = call.offerSdp,
            onAnswerCreated = { answerSdp ->
                viewModelScope.launch {
                    callRepo.answerCall(call.id, answerSdp)
                }
            }
        )
    }

    fun rejectIncomingCall(call: CallSession) {
        _incomingCall.value = null
        viewModelScope.launch {
            callRepo.updateCallStatus(call.id, "rejected", 0L)
        }
    }

    fun endActiveCall() {
        val call = _activeCall.value
        val duration = _elapsedSeconds.value
        timerJob?.cancel()
        activeCallJob?.cancel()
        webRtcManager.endCallSession()
        _activeCall.value = null
        _elapsedSeconds.value = 0L
        if (call != null && call.id != "pending") {
            viewModelScope.launch {
                val finalStatus = if (duration > 0L) "ended" else "missed"
                callRepo.updateCallStatus(call.id, finalStatus, duration)
            }
        }
    }

    private fun observeActiveCallDoc(callId: String) {
        activeCallJob?.cancel()
        activeCallJob = viewModelScope.launch {
            callRepo.observeCall(callId).catch {}.collect { updated ->
                if (updated == null) return@collect
                _activeCall.value = updated
                if (updated.answerSdp.isNotBlank() && updated.callerId == auth.currentUser?.uid) {
                    webRtcManager.applyRemoteAnswer(updated.answerSdp)
                    if (timerJob == null || timerJob?.isActive != true) {
                        startDurationTimer()
                    }
                }
                if (updated.status in listOf("ended", "rejected", "missed")) {
                    timerJob?.cancel()
                    webRtcManager.endCallSession()
                    _activeCall.value = null
                }
            }
        }
    }

    private fun startDurationTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                _elapsedSeconds.value = _elapsedSeconds.value + 1L
            }
        }
    }

    fun toggleMute() = webRtcManager.toggleMute()
    fun toggleSpeaker() = webRtcManager.toggleSpeaker()
    fun toggleCamera() = webRtcManager.toggleCamera()
    fun switchCamera() = webRtcManager.switchCamera()

    fun clearNotice() {
        _callNotice.value = null
    }

    override fun onCleared() {
        webRtcManager.endCallSession()
        webRtcManager.eglBase.release()
        super.onCleared()
    }
}
