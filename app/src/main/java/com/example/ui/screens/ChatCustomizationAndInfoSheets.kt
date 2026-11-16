package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.core.ChatAppearancePreferences
import com.example.data.local.LocalMediaEntity
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.UsernameReservation
import com.example.media.VoicePlaybackState
import com.example.ui.components.GlassCard
import com.example.ui.components.UserAvatar
import com.example.ui.theme.ChatColorPreset
import com.example.ui.theme.ChatWallpaperPreset
import com.example.ui.theme.LocalPrivacyXTokens
import java.io.File

@Composable
fun ChatAppearanceSheetDialog(
    appearance: ChatAppearancePreferences,
    onSelectColorPreset: (String) -> Unit,
    onSelectWallpaperPreset: (String) -> Unit,
    onPickCustomWallpaper: (Uri) -> Unit,
    onClearCustomWallpaper: () -> Unit,
    onDimChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = LocalPrivacyXTokens.current
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onPickCustomWallpaper(uri)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            elevated = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .heightIn(max = 640.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, contentDescription = "Theme", tint = tokens.accentPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Chat Colors & Wallpaper", style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("appearance_close_button")) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "Bubble Color Presets (${ChatColorPreset.entries.size} options)",
                    style = MaterialTheme.typography.labelLarge,
                    color = tokens.accentPrimary
                )
                Spacer(Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ChatColorPreset.entries, key = { it.id }) { preset ->
                        val selected = appearance.colorPresetId == preset.id
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(72.dp)
                                .clickable { onSelectColorPreset(preset.id) }
                                .testTag("color_preset_${preset.id}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Brush.linearGradient(preset.sentGradient))
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) Color.White else tokens.surfaceBorder,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = tokens.surfaceBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Wallpapers (${ChatWallpaperPreset.entries.size} built-in + custom)",
                        style = MaterialTheme.typography.labelLarge,
                        color = tokens.accentPrimary
                    )
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.testTag("pick_custom_wallpaper_button")
                    ) {
                        Icon(Icons.Rounded.Wallpaper, contentDescription = "Gallery Wallpaper", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Gallery")
                    }
                }

                if (appearance.customWallpaperUri.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = tokens.accentPrimary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, tokens.accentPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = appearance.customWallpaperUri,
                                contentDescription = "Current custom wallpaper",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Custom Gallery Wallpaper Active", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = onClearCustomWallpaper) { Text("Reset") }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(ChatWallpaperPreset.entries, key = { it.id }) { wall ->
                        val selected = appearance.customWallpaperUri.isBlank() && appearance.wallpaperPresetId == wall.id
                        val bgBrush = if (wall.gradientColors.isEmpty()) {
                            tokens.backgroundBrush
                        } else {
                            Brush.verticalGradient(wall.gradientColors.map { Color(it) })
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(78.dp)
                                .clickable { onSelectWallpaperPreset(wall.id) }
                                .testTag("wallpaper_preset_${wall.id}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 74.dp, height = 94.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(bgBrush)
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) tokens.accentPrimary else tokens.surfaceBorder,
                                        shape = RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Surface(shape = CircleShape, color = tokens.accentPrimary, modifier = Modifier.size(24.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.Check, contentDescription = "Active", tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = wall.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "Wallpaper Dimness: ${(appearance.wallpaperDimAlpha * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = appearance.wallpaperDimAlpha,
                    onValueChange = onDimChange,
                    valueRange = 0f..0.75f
                )
            }
        }
    }
}

@Composable
fun ChatSharedMediaAndPinnedSheetDialog(
    messages: List<ChatMessage>,
    playbackState: VoicePlaybackState,
    localMediaMap: Map<String, LocalMediaEntity> = emptyMap(),
    knownBackupMediaIds: Set<String> = emptySet(),
    onJumpToMessage: (ChatMessage) -> Unit,
    onTogglePin: (ChatMessage) -> Unit,
    onTogglePlayVoice: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenDocument: (String) -> Unit,
    onDownloadMedia: (ChatMessage) -> Unit = {},
    onDismiss: () -> Unit,
    initialTab: String = "Media"
) {
    val tokens = LocalPrivacyXTokens.current
    var activeTab by remember { mutableStateOf(initialTab) }
    val urlRegex = remember { Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE) }

    fun resolvePlayableOrViewablePath(msg: ChatMessage): String {
        val local = localMediaMap[msg.mediaId.ifBlank { msg.id }] ?: localMediaMap[msg.id]
        val localFile = local?.localFilePath?.takeIf { it.isNotBlank() }?.let(::File)
        if (localFile != null && localFile.exists()) return Uri.fromFile(localFile).toString()
        val cloudExpired = msg.mediaExpired || msg.mediaUrl.isBlank() ||
            (msg.mediaExpiresAt > 0L && msg.mediaExpiresAt <= System.currentTimeMillis())
        return if (cloudExpired) "" else msg.mediaUrl
    }

    val mediaMessages = remember(messages, localMediaMap) {
        messages.filter { !it.isDeleted && it.mediaType == "image" }
    }
    val voiceMessages = remember(messages, localMediaMap) {
        messages.filter { !it.isDeleted && it.mediaType == "voice" }
    }
    val fileMessages = remember(messages, localMediaMap) {
        messages.filter { !it.isDeleted && it.mediaType == "document" }
    }
    val pinnedMessages = remember(messages) {
        messages.filter { !it.isDeleted && it.isPinned }
    }
    val linkEntries = remember(messages) {
        messages.filter { !it.isDeleted && it.text.isNotBlank() }.flatMap { msg ->
            urlRegex.findAll(msg.text).map { it.value to msg }.toList()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            elevated = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 28.dp)
                .heightIn(min = 340.dp, max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Shared Media & Pins", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "Media" to mediaMessages.size,
                        "Voice" to voiceMessages.size,
                        "Files" to fileMessages.size,
                        "Links" to linkEntries.size,
                        "Pinned" to pinnedMessages.size
                    ).forEach { (tab, count) ->
                        val selected = activeTab == tab
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) tokens.accentPrimary else tokens.surfaceGlass,
                            border = BorderStroke(0.8.dp, tokens.surfaceBorder),
                            modifier = Modifier
                                .clickable { activeTab = tab }
                                .testTag("shared_tab_$tab")
                        ) {
                            Text(
                                text = "$tab ($count)",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false).heightIn(max = 430.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (activeTab) {
                        "Media" -> {
                            if (mediaMessages.isEmpty()) {
                                item { Text("No shared photos yet.", style = MaterialTheme.typography.bodySmall) }
                            } else {
                                items(mediaMessages, key = { it.id }) { msg ->
                                    val resolved = resolvePlayableOrViewablePath(msg)
                                    val inBackup = msg.mediaId in knownBackupMediaIds
                                    GlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { if (resolved.isNotBlank()) onOpenImage(resolved) }
                                    ) {
                                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            if (resolved.isNotBlank()) {
                                                AsyncImage(
                                                    model = resolved,
                                                    contentDescription = "Media",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                                                )
                                            } else {
                                                Surface(shape = RoundedCornerShape(10.dp), color = tokens.surfaceGlass, modifier = Modifier.size(56.dp)) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(Icons.Rounded.Image, contentDescription = "Unavailable", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(msg.senderName, style = MaterialTheme.typography.titleSmall)
                                                Text(
                                                    text = when {
                                                        resolved.isBlank() && inBackup -> "Available in Backup"
                                                        resolved.isBlank() -> "Media unavailable (7-day expired)"
                                                        else -> msg.text.ifBlank { "PHOTO" }
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (resolved.isNotBlank()) {
                                                IconButton(onClick = { onDownloadMedia(msg) }) {
                                                    Icon(Icons.Rounded.Download, contentDescription = "Save locally", tint = tokens.accentPrimary)
                                                }
                                            }
                                            TextButton(onClick = { onJumpToMessage(msg) }) { Text("Jump") }
                                        }
                                    }
                                }
                            }
                        }
                        "Voice" -> {
                            if (voiceMessages.isEmpty()) {
                                item { Text("No voice messages yet.", style = MaterialTheme.typography.bodySmall) }
                            } else {
                                items(voiceMessages, key = { it.id }) { msg ->
                                    val resolved = resolvePlayableOrViewablePath(msg)
                                    val inBackup = msg.mediaId in knownBackupMediaIds
                                    val isPlaying = resolved.isNotBlank() && playbackState.activeUrl == resolved && playbackState.isPlaying
                                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { if (resolved.isNotBlank()) onTogglePlayVoice(resolved) },
                                                enabled = resolved.isNotBlank()
                                            ) {
                                                Icon(
                                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                                    contentDescription = "Play Voice",
                                                    tint = if (resolved.isNotBlank()) tokens.accentPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(msg.senderName, style = MaterialTheme.typography.titleSmall)
                                                Text(
                                                    text = when {
                                                        resolved.isBlank() && inBackup -> "Available in Backup"
                                                        resolved.isBlank() -> "Voice note unavailable (7-day expired)"
                                                        else -> "${(msg.durationMs / 1000L).coerceAtLeast(1)}s voice note"
                                                    },
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            if (resolved.isNotBlank()) {
                                                IconButton(onClick = { onDownloadMedia(msg) }) {
                                                    Icon(Icons.Rounded.Download, contentDescription = "Save locally", tint = tokens.accentPrimary)
                                                }
                                            }
                                            TextButton(onClick = { onJumpToMessage(msg) }) { Text("Jump") }
                                        }
                                    }
                                }
                            }
                        }
                        "Files" -> {
                            if (fileMessages.isEmpty()) {
                                item { Text("No shared documents yet.", style = MaterialTheme.typography.bodySmall) }
                            } else {
                                items(fileMessages, key = { it.id }) { msg ->
                                    val resolved = resolvePlayableOrViewablePath(msg)
                                    GlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { if (resolved.isNotBlank()) onOpenDocument(resolved) }
                                    ) {
                                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.Description, contentDescription = "Document", tint = tokens.accentPrimary)
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(msg.fileName.ifBlank { "Document" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    text = if (resolved.isBlank()) "File unavailable (7-day expired)" else "From ${msg.senderName} • ${(msg.fileSizeBytes / 1024L).coerceAtLeast(1)} KB",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            if (resolved.isNotBlank()) {
                                                IconButton(onClick = { onDownloadMedia(msg) }) {
                                                    Icon(Icons.Rounded.Download, contentDescription = "Save locally", tint = tokens.accentPrimary)
                                                }
                                            }
                                            TextButton(onClick = { onJumpToMessage(msg) }) { Text("Jump") }
                                        }
                                    }
                                }
                            }
                        }
                        "Links" -> {
                            if (linkEntries.isEmpty()) {
                                item { Text("No links shared yet.", style = MaterialTheme.typography.bodySmall) }
                            } else {
                                items(linkEntries) { (url, msg) ->
                                    GlassCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        onClick = { onOpenDocument(url) }
                                    ) {
                                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.Link, contentDescription = "Link", tint = tokens.accentPrimary)
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(url, style = MaterialTheme.typography.bodySmall, color = tokens.accentPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text("Shared by ${msg.senderName}", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            if (pinnedMessages.isEmpty()) {
                                item { Text("No pinned messages yet. Tap any message to pin it.", style = MaterialTheme.typography.bodySmall) }
                            } else {
                                items(pinnedMessages, key = { it.id }) { msg ->
                                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.PushPin, contentDescription = "Pinned", tint = tokens.accentPrimary)
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(msg.senderName, style = MaterialTheme.typography.labelMedium, color = tokens.accentPrimary)
                                                Text(msg.text.ifBlank { msg.mediaType }, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                            TextButton(onClick = { onJumpToMessage(msg) }) { Text("Jump") }
                                            TextButton(onClick = { onTogglePin(msg) }) { Text("Unpin") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpaceInfoAndManagementSheetDialog(
    conversation: Conversation,
    currentUserId: String,
    isMuted: Boolean,
    directoryUsers: List<UsernameReservation>,
    onUploadAvatar: (Uri) -> Unit,
    onUpdateSettings: (
        title: String,
        description: String,
        isPublic: Boolean,
        slowModeSeconds: Long,
        memberApprovalRequired: Boolean,
        adminsOnlyMessaging: Boolean
    ) -> Unit,
    onRegenerateInviteCode: () -> Unit,
    onAddMember: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onPromoteMember: (String) -> Unit,
    onDemoteMember: (String) -> Unit,
    onToggleMute: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenSharedMedia: () -> Unit,
    onStartVoiceCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onLeave: () -> Unit,
    onReport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tokens = LocalPrivacyXTokens.current
    val isAdmin = currentUserId in conversation.adminUids || conversation.ownerId == currentUserId

    var editTitle by remember(conversation.title) { mutableStateOf(conversation.title) }
    var editDesc by remember(conversation.description) { mutableStateOf(conversation.description) }
    var editIsPublic by remember(conversation.isPublic) { mutableStateOf(conversation.isPublic) }
    var editSlowMode by remember(conversation.slowModeSeconds) { mutableStateOf(conversation.slowModeSeconds) }
    var editApproval by remember(conversation.memberApprovalRequired) { mutableStateOf(conversation.memberApprovalRequired) }
    var editAdminsOnly by remember(conversation.adminsOnlyMessaging) { mutableStateOf(conversation.adminsOnlyMessaging) }
    var addSearchQuery by remember { mutableStateOf("") }
    var reportReason by remember { mutableStateOf("") }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onUploadAvatar(uri)
    }

    val userMap = remember(directoryUsers) { directoryUsers.associateBy { it.userId } }
    val currentMembers = conversation.memberUids
    val nonMembers = remember(directoryUsers, conversation.memberUids, addSearchQuery) {
        directoryUsers.filter { u ->
            u.userId !in conversation.memberUids &&
                (addSearchQuery.isBlank() ||
                    u.displayName.contains(addSearchQuery, ignoreCase = true) ||
                    u.username.contains(addSearchQuery, ignoreCase = true))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GlassCard(
            elevated = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .heightIn(max = 680.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp).testTag("space_info_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(
                            name = conversation.title,
                            photoUrl = conversation.photoUrl,
                            size = 60.dp,
                            gradientSeed = conversation.id.hashCode()
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(conversation.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${conversation.type.replaceFirstChar { it.uppercase() }} • ${conversation.memberUids.size} members",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isAdmin && conversation.type != "direct") {
                                TextButton(
                                    onClick = {
                                        avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                    modifier = Modifier.testTag("change_space_photo_button")
                                ) {
                                    Icon(Icons.Rounded.Image, contentDescription = "Change photo", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Change Photo")
                                }
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close Info")
                        }
                    }
                }

                // Quick Actions Bar
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (conversation.type != "channel") {
                            QuickInfoPill(Icons.Rounded.Call, "Voice") {
                                onDismiss()
                                onStartVoiceCall()
                            }
                            QuickInfoPill(Icons.Rounded.Videocam, "Video") {
                                onDismiss()
                                onStartVideoCall()
                            }
                        }
                        QuickInfoPill(Icons.Rounded.Search, "Search") {
                            onDismiss()
                            onOpenSearch()
                        }
                        QuickInfoPill(Icons.Rounded.Palette, "Theme") {
                            onDismiss()
                            onOpenAppearance()
                        }
                        QuickInfoPill(Icons.Rounded.Image, "Media") {
                            onDismiss()
                            onOpenSharedMedia()
                        }
                        QuickInfoPill(
                            if (isMuted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                            if (isMuted) "Unmute" else "Mute",
                            onClick = onToggleMute
                        )
                    }
                }

                // Group / Community / Channel Customization (Admin Controls)
                if (conversation.type != "direct" && isAdmin) {
                    item {
                        HorizontalDivider(color = tokens.surfaceBorder)
                        Spacer(Modifier.height(6.dp))
                        Text("Customize ${conversation.type.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.titleMedium, color = tokens.accentPrimary)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text("Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("space_edit_title")
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editDesc,
                            onValueChange = { editDesc = it },
                            label = { Text("Description / Rules") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth().testTag("space_edit_desc")
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Slow Mode", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0L to "Off", 5L to "5s", 15L to "15s", 30L to "30s", 60L to "1m", 300L to "5m").forEach { (sec, label) ->
                                val selected = editSlowMode == sec
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (selected) tokens.accentPrimary else tokens.surfaceGlass,
                                    border = BorderStroke(0.8.dp, tokens.surfaceBorder),
                                    modifier = Modifier.clickable { editSlowMode = sec }
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Public Discovery", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = editIsPublic, onCheckedChange = { editIsPublic = it })
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Admins-Only Messaging", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = editAdminsOnly, onCheckedChange = { editAdminsOnly = it })
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Require Member Approval", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = editApproval, onCheckedChange = { editApproval = it })
                        }

                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                onUpdateSettings(editTitle, editDesc, editIsPublic, editSlowMode, editApproval, editAdminsOnly)
                            },
                            enabled = editTitle.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary),
                            modifier = Modifier.fillMaxWidth().testTag("space_save_settings_button")
                        ) {
                            Text("Save Space Settings")
                        }
                    }
                }

                // Invite Code Card
                if (conversation.inviteCode.isNotBlank()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = tokens.surfaceGlass,
                            border = BorderStroke(0.8.dp, tokens.surfaceBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Invite Code", style = MaterialTheme.typography.labelSmall)
                                    Text(conversation.inviteCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.accentPrimary)
                                }
                                IconButton(onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("PrivacyX Invite", conversation.inviteCode))
                                }) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Code")
                                }
                                if (isAdmin) {
                                    IconButton(onClick = onRegenerateInviteCode) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = "Reset Invite Code")
                                    }
                                }
                            }
                        }
                    }
                }

                // Members List (Promote / Demote / Kick)
                if (conversation.type != "direct") {
                    item {
                        HorizontalDivider(color = tokens.surfaceBorder)
                        Spacer(Modifier.height(4.dp))
                        Text("Members (${currentMembers.size})", style = MaterialTheme.typography.titleMedium)
                    }
                    items(currentMembers, key = { "member_$it" }) { uid ->
                        val u = userMap[uid]
                        val isMemberOwner = uid == conversation.ownerId
                        val isMemberAdmin = uid in conversation.adminUids
                        val labelName = u?.displayName ?: if (uid == currentUserId) "You" else "User ${uid.take(6)}"
                        val sub = buildString {
                            if (u != null) append("@${u.username} ")
                            when {
                                isMemberOwner -> append("• Owner")
                                isMemberAdmin -> append("• Admin")
                                else -> append("• Member")
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(name = labelName, photoUrl = u?.photoUrl, size = 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(labelName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(sub, style = MaterialTheme.typography.labelSmall, color = if (isMemberAdmin) tokens.accentPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (isAdmin && !isMemberOwner && uid != currentUserId) {
                                IconButton(
                                    onClick = {
                                        if (isMemberAdmin) onDemoteMember(uid) else onPromoteMember(uid)
                                    },
                                    modifier = Modifier.testTag("toggle_admin_$uid")
                                ) {
                                    Icon(
                                        Icons.Rounded.AdminPanelSettings,
                                        contentDescription = if (isMemberAdmin) "Demote Admin" else "Promote Admin",
                                        tint = if (isMemberAdmin) tokens.warningAmber else tokens.accentPrimary
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveMember(uid) },
                                    modifier = Modifier.testTag("kick_member_$uid")
                                ) {
                                    Icon(Icons.Rounded.PersonRemove, contentDescription = "Kick Member", tint = tokens.dangerRed)
                                }
                            }
                        }
                    }

                    // Add Members Section (Admin)
                    if (isAdmin) {
                        item {
                            Spacer(Modifier.height(6.dp))
                            Text("Add Members", style = MaterialTheme.typography.titleSmall, color = tokens.accentPrimary)
                            OutlinedTextField(
                                value = addSearchQuery,
                                onValueChange = { addSearchQuery = it },
                                placeholder = { Text("Search users to add...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            )
                        }
                        items(nonMembers.take(8), key = { "add_${it.userId}" }) { u ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatar(name = u.displayName, photoUrl = u.photoUrl, size = 36.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(u.displayName, style = MaterialTheme.typography.bodyMedium)
                                    Text("@${u.username}", style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(
                                    onClick = { onAddMember(u.userId) },
                                    modifier = Modifier.testTag("add_member_${u.userId}")
                                ) {
                                    Icon(Icons.Rounded.PersonAdd, contentDescription = "Add Member", tint = tokens.onlineGreen)
                                }
                            }
                        }
                    }
                }

                // Leave & Report Footer
                item {
                    HorizontalDivider(color = tokens.surfaceBorder)
                    Spacer(Modifier.height(6.dp))
                    if (conversation.type != "direct" && conversation.ownerId != currentUserId) {
                        OutlinedButton(
                            onClick = onLeave,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.dangerRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Leave ${conversation.type.replaceFirstChar { it.uppercase() }}")
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        label = { Text("Report reason (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (reportReason.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = { onReport(reportReason) },
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.dangerRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Submit Report")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickInfoPill(icon: ImageVector, label: String, onClick: () -> Unit) {
    val tokens = LocalPrivacyXTokens.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = tokens.accentPrimary.copy(alpha = 0.14f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = tokens.accentPrimary, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
