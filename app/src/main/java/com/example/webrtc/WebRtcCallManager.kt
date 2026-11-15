package com.example.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.core.AppConstants
import com.example.data.repository.CallRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

data class WebRtcSessionState(
    val connectionState: String = "Idle",
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isCameraEnabled: Boolean = true,
    val isFrontCamera: Boolean = true,
    val hasRemoteVideo: Boolean = false,
    val errorMessage: String? = null
)

class WebRtcCallManager(
    private val context: Context,
    private val callRepository: CallRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    val eglBase: EglBase = EglBase.create()
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var remoteSink: VideoSink? = null
    private var localSink: VideoSink? = null
    private var candidatesJob: Job? = null
    private val addedCandidateKeys = mutableSetOf<String>()

    private val _state = MutableStateFlow(WebRtcSessionState())
    val state: StateFlow<WebRtcSessionState> = _state.asStateFlow()

    private fun ensureFactory() {
        if (factory != null) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
    }

    fun attachSinks(local: VideoSink?, remote: VideoSink?) {
        localSink = local
        remoteSink = remote
        local?.let { localVideoTrack?.addSink(it) }
    }

    fun startCallSession(
        callId: String,
        currentUserId: String,
        participantIds: List<String>,
        isVideo: Boolean,
        isCaller: Boolean,
        remoteOfferSdp: String? = null,
        onOfferCreated: (String) -> Unit = {},
        onAnswerCreated: (String) -> Unit = {}
    ) {
        try {
            ensureFactory()
            val iceServers = buildIceServers()
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }

            val pc = factory!!.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                    _state.value = _state.value.copy(
                        connectionState = newState?.name ?: "Unknown"
                    )
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {}
                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
                override fun onIceCandidate(candidate: IceCandidate?) {
                    if (candidate != null) {
                        scope.launch(Dispatchers.IO) {
                            callRepository.addIceCandidate(
                                callId = callId,
                                participantIds = participantIds,
                                sdpMid = candidate.sdpMid ?: "0",
                                sdpMLineIndex = candidate.sdpMLineIndex,
                                candidate = candidate.sdp
                            )
                        }
                    }
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: MediaStream?) {
                    val video = stream?.videoTracks?.firstOrNull()
                    if (video != null) {
                        remoteSink?.let { video.addSink(it) }
                        _state.value = _state.value.copy(hasRemoteVideo = true)
                    }
                }

                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(channel: DataChannel?) {}
                override fun onRenegotiationNeeded() {}
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    val track = receiver?.track()
                    if (track is VideoTrack) {
                        remoteSink?.let { track.addSink(it) }
                        _state.value = _state.value.copy(hasRemoteVideo = true)
                    }
                }
            }) ?: throw IllegalStateException("Failed to initialize PeerConnection")

            peerConnection = pc
            addLocalMediaTracks(pc, isVideo)
            observeRemoteCandidates(callId, currentUserId)

            val mediaConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(
                    MediaConstraints.KeyValuePair(
                        "OfferToReceiveVideo",
                        if (isVideo) "true" else "false"
                    )
                )
            }

            if (isCaller) {
                _state.value = _state.value.copy(connectionState = "Connecting...")
                pc.createOffer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(desc: SessionDescription?) {
                        if (desc == null) return
                        pc.setLocalDescription(SimpleSdpObserver(), desc)
                        onOfferCreated(desc.description)
                    }

                    override fun onCreateFailure(error: String?) {
                        _state.value = _state.value.copy(errorMessage = error ?: "Offer creation failed")
                    }
                }, mediaConstraints)
            } else if (!remoteOfferSdp.isNullOrBlank()) {
                _state.value = _state.value.copy(connectionState = "Answering...")
                val offer = SessionDescription(SessionDescription.Type.OFFER, remoteOfferSdp)
                pc.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        pc.createAnswer(object : SimpleSdpObserver() {
                            override fun onCreateSuccess(answer: SessionDescription?) {
                                if (answer == null) return
                                pc.setLocalDescription(SimpleSdpObserver(), answer)
                                onAnswerCreated(answer.description)
                            }

                            override fun onCreateFailure(error: String?) {
                                _state.value = _state.value.copy(errorMessage = error ?: "Answer failed")
                            }
                        }, mediaConstraints)
                    }
                }, offer)
            }
        } catch (e: Exception) {
            Log.e(TAG, "WebRTC initialization failed", e)
            _state.value = _state.value.copy(
                connectionState = "Failed",
                errorMessage = e.localizedMessage ?: "WebRTC failed to start"
            )
        }
    }

    fun applyRemoteAnswer(answerSdp: String) {
        val pc = peerConnection ?: return
        if (answerSdp.isBlank()) return
        if (pc.remoteDescription != null) return
        pc.setRemoteDescription(
            SimpleSdpObserver(),
            SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        )
    }

    private fun observeRemoteCandidates(callId: String, currentUserId: String) {
        candidatesJob?.cancel()
        candidatesJob = scope.launch {
            callRepository.observeIceCandidates(callId).collect { list ->
                list.filter { it.senderId != currentUserId }.forEach { doc ->
                    val key = "${doc.id}_${doc.candidate}"
                    if (addedCandidateKeys.add(key)) {
                        peerConnection?.addIceCandidate(
                            IceCandidate(doc.sdpMid, doc.sdpMLineIndex.toInt(), doc.candidate)
                        )
                    }
                }
            }
        }
    }

    private fun addLocalMediaTracks(pc: PeerConnection, isVideo: Boolean) {
        val f = factory ?: return
        localAudioSource = f.createAudioSource(MediaConstraints())
        localAudioTrack = f.createAudioTrack("PX_AUDIO_TRACK", localAudioSource)
        pc.addTrack(localAudioTrack, listOf("PX_STREAM"))

        if (isVideo) {
            val capturer = createCameraCapturer(preferFront = true)
            if (capturer != null) {
                videoCapturer = capturer
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
                localVideoSource = f.createVideoSource(capturer.isScreencast)
                capturer.initialize(surfaceTextureHelper, context, localVideoSource!!.capturerObserver)
                capturer.startCapture(720, 1280, 30)
                localVideoTrack = f.createVideoTrack("PX_VIDEO_TRACK", localVideoSource)
                localSink?.let { localVideoTrack?.addSink(it) }
                pc.addTrack(localVideoTrack, listOf("PX_STREAM"))
            }
        }
    }

    private fun createCameraCapturer(preferFront: Boolean): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val devices = enumerator.deviceNames
        val target = devices.firstOrNull {
            if (preferFront) enumerator.isFrontFacing(it) else enumerator.isBackFacing(it)
        } ?: devices.firstOrNull()
        return target?.let { enumerator.createCapturer(it, null) }
    }

    private fun buildIceServers(): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()
        AppConstants.DEFAULT_ICE_SERVERS.forEach { stun ->
            servers.add(PeerConnection.IceServer.builder(stun).createIceServer())
        }
        val turnUrl = AppConstants.turnServerUrl.trim()
        if (turnUrl.isNotBlank()) {
            servers.add(
                PeerConnection.IceServer.builder(turnUrl)
                    .setUsername(AppConstants.turnUsername)
                    .setPassword(AppConstants.turnCredential)
                    .createIceServer()
            )
        }
        return servers
    }

    fun toggleMute() {
        val next = !_state.value.isMuted
        localAudioTrack?.setEnabled(!next)
        _state.value = _state.value.copy(isMuted = next)
    }

    fun toggleSpeaker() {
        val next = !_state.value.isSpeakerOn
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        @Suppress("DEPRECATION")
        audioManager?.isSpeakerphoneOn = next
        _state.value = _state.value.copy(isSpeakerOn = next)
    }

    fun toggleCamera() {
        val next = !_state.value.isCameraEnabled
        localVideoTrack?.setEnabled(next)
        _state.value = _state.value.copy(isCameraEnabled = next)
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                _state.value = _state.value.copy(isFrontCamera = isFrontCamera)
            }

            override fun onCameraSwitchError(errorDescription: String?) {}
        })
    }

    fun endCallSession() {
        candidatesJob?.cancel()
        candidatesJob = null
        addedCandidateKeys.clear()
        try {
            videoCapturer?.stopCapture()
        } catch (_: Exception) {
        }
        try {
            videoCapturer?.dispose()
        } catch (_: Exception) {
        }
        videoCapturer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        localVideoTrack?.dispose()
        localVideoTrack = null
        localVideoSource?.dispose()
        localVideoSource = null
        localAudioTrack?.dispose()
        localAudioTrack = null
        localAudioSource?.dispose()
        localAudioSource = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        _state.value = WebRtcSessionState()
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(p0: String?) {}
        override fun onSetFailure(p0: String?) {}
    }

    private companion object {
        const val TAG = "WebRtcCallManager"
    }
}
