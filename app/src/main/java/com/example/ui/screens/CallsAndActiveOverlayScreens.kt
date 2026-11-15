package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.model.CallSession
import com.example.data.model.UserProfile
import com.example.data.model.UsernameReservation
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GlassCard
import com.example.ui.components.UserAvatar
import com.example.ui.theme.LocalPrivacyXTokens
import com.example.webrtc.WebRtcSessionState
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSink

@Composable
fun CallsScreen(
    currentUser: UserProfile,
    callHistory: List<CallSession>,
    directoryUsers: List<UsernameReservation>,
    showDialerExternal: Boolean,
    dialerVideoDefault: Boolean,
    onDismissExternalDialer: () -> Unit,
    onStartCall: (targetUid: String, targetName: String, isVideo: Boolean) -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalPrivacyXTokens.current
    var showDialer by remember { mutableStateOf(false) }
    var pendingCallRequest by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val req = pendingCallRequest
        pendingCallRequest = null
        val audioOk = grants[Manifest.permission.RECORD_AUDIO] == true
        val videoOk = req?.third != true || grants[Manifest.permission.CAMERA] == true
        if (req != null && audioOk && videoOk) {
            onStartCall(req.first, req.second, req.third)
        }
    }

    fun launchWithPermissions(uid: String, name: String, isVideo: Boolean) {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (isVideo) needed.add(Manifest.permission.CAMERA)
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            onStartCall(uid, name, isVideo)
        } else {
            pendingCallRequest = Triple(uid, name, isVideo)
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Calls", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "WebRTC real-time voice & video sessions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showDialer = true },
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary),
                    modifier = Modifier.testTag("start_new_call_button")
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = "New Call", modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Call")
                }
            }
        }

        if (callHistory.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.Call,
                    title = "No call history",
                    subtitle = "Tap New Call or call directly from any direct chat."
                )
            }
        } else {
            items(callHistory, key = { it.id }) { call ->
                val isCaller = call.callerId == currentUser.userId
                val peerUid = call.participantIds.firstOrNull { it != currentUser.userId }.orEmpty()
                val label = if (isCaller) "Outgoing ${call.callType} call" else "Incoming from ${call.callerName}"
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(name = call.callerName, photoUrl = null, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Status: ${call.status} • ${call.durationSeconds}s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (peerUid.isNotBlank()) {
                            IconButton(onClick = { launchWithPermissions(peerUid, call.callerName, false) }) {
                                Icon(Icons.Rounded.Call, contentDescription = "Redial Voice", tint = tokens.accentPrimary)
                            }
                            IconButton(onClick = { launchWithPermissions(peerUid, call.callerName, true) }) {
                                Icon(Icons.Rounded.Videocam, contentDescription = "Redial Video", tint = tokens.accentPrimary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialer || showDialerExternal) {
        AlertDialog(
            onDismissRequest = {
                showDialer = false
                onDismissExternalDialer()
            },
            title = { Text(if (dialerVideoDefault) "Start Video Call" else "Start Call") },
            text = {
                LazyColumn(modifier = Modifier.height(240.dp)) {
                    items(directoryUsers, key = { it.username }) { u ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(name = u.displayName, photoUrl = u.photoUrl, size = 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(u.displayName, style = MaterialTheme.typography.titleMedium)
                                Text("@${u.username}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = {
                                showDialer = false
                                onDismissExternalDialer()
                                launchWithPermissions(u.userId, u.displayName, false)
                            }) {
                                Icon(Icons.Rounded.Call, contentDescription = "Voice Call", tint = tokens.accentPrimary)
                            }
                            IconButton(onClick = {
                                showDialer = false
                                onDismissExternalDialer()
                                launchWithPermissions(u.userId, u.displayName, true)
                            }) {
                                Icon(Icons.Rounded.Videocam, contentDescription = "Video Call", tint = tokens.accentPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialer = false
                    onDismissExternalDialer()
                }) { Text("Close") }
            }
        )
    }
}

@Composable
fun IncomingCallBanner(
    call: CallSession,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalPrivacyXTokens.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val audioOk = grants[Manifest.permission.RECORD_AUDIO] == true
        val videoOk = call.callType != "video" || grants[Manifest.permission.CAMERA] == true
        if (audioOk && videoOk) onAccept()
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(12.dp),
        elevated = true
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(name = call.callerName, photoUrl = null, size = 44.dp, isOnline = true)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("${call.callerName} is calling", style = MaterialTheme.typography.titleMedium)
                Text("Incoming ${call.callType} call", style = MaterialTheme.typography.bodySmall, color = tokens.accentPrimary)
            }
            IconButton(
                onClick = onReject,
                modifier = Modifier.background(tokens.dangerRed.copy(alpha = 0.16f), CircleShape)
            ) {
                Icon(Icons.Rounded.CallEnd, contentDescription = "Reject", tint = tokens.dangerRed)
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
                    if (call.callType == "video") needed.add(Manifest.permission.CAMERA)
                    val missing = needed.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isEmpty()) onAccept() else permissionLauncher.launch(missing.toTypedArray())
                },
                modifier = Modifier.background(tokens.onlineGreen.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(Icons.Rounded.Call, contentDescription = "Accept", tint = tokens.onlineGreen)
            }
        }
    }
}

@Composable
fun ActiveCallOverlay(
    call: CallSession,
    webrtcState: WebRtcSessionState,
    elapsedSeconds: Long,
    eglContext: EglBase.Context,
    onAttachSinks: (local: VideoSink?, remote: VideoSink?) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onEndCall: () -> Unit
) {
    BackHandler(onBack = onEndCall)
    val tokens = LocalPrivacyXTokens.current
    val mins = elapsedSeconds / 60L
    val secs = elapsedSeconds % 60L
    val timerText = String.format("%02d:%02d", mins, secs)

    var localRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var remoteRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    LaunchedEffect(localRenderer, remoteRenderer) {
        onAttachSinks(localRenderer, remoteRenderer)
    }

    DisposableEffect(Unit) {
        onDispose {
            onAttachSinks(null, null)
            localRenderer?.release()
            remoteRenderer?.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A142D), Color(0xFF0D0A18))))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (call.callType == "video") {
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        init(eglContext, null)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                        setEnableHardwareScaler(true)
                        remoteRenderer = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (webrtcState.isCameraEnabled) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            init(eglContext, null)
                            setZOrderMediaOverlay(true)
                            setMirror(true)
                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            localRenderer = this
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(width = 110.dp, height = 156.dp)
                        .clip(RoundedCornerShape(18.dp))
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 42.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (call.callType != "video") {
                UserAvatar(name = call.callerName, photoUrl = null, size = 92.dp, isOnline = true)
                Spacer(Modifier.height(16.dp))
            }
            Text(
                text = call.callerName.ifBlank { "PrivacyX Call" },
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (elapsedSeconds > 0) timerText else webrtcState.connectionState,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
            if (!webrtcState.errorMessage.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(webrtcState.errorMessage, style = MaterialTheme.typography.bodySmall, color = tokens.dangerRed)
            }
        }

        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color.White.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 28.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallControlButton(
                    icon = if (webrtcState.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    label = "Mute",
                    active = webrtcState.isMuted,
                    onClick = onToggleMute
                )
                CallControlButton(
                    icon = Icons.Rounded.VolumeUp,
                    label = "Speaker",
                    active = webrtcState.isSpeakerOn,
                    onClick = onToggleSpeaker
                )
                if (call.callType == "video") {
                    CallControlButton(
                        icon = if (webrtcState.isCameraEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                        label = "Camera",
                        active = !webrtcState.isCameraEnabled,
                        onClick = onToggleCamera
                    )
                    CallControlButton(
                        icon = Icons.Rounded.Cameraswitch,
                        label = "Flip",
                        active = false,
                        onClick = onSwitchCamera
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = tokens.dangerRed,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onEndCall)
                        .testTag("end_call_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.CallEnd, contentDescription = "End Call", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (active) Color.White else Color.White.copy(alpha = 0.18f),
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color(0xFF1A142D) else Color.White
            )
        }
    }
}
