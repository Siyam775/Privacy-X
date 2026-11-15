package com.example.ui.screens

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Poll
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.media.VoiceRecordingState
import com.example.ui.components.GlassCard
import com.example.ui.theme.LocalPrivacyXTokens

@Composable
fun VoiceRecordingComposerBar(
    recordingState: VoiceRecordingState,
    onTogglePause: () -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    val tokens = LocalPrivacyXTokens.current
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = tokens.surfaceGlassElevated,
        border = BorderStroke(1.2.dp, tokens.accentPrimary),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Rounded.Delete, contentDescription = "Cancel Recording", tint = tokens.dangerRed)
            }
            Text(
                text = "${recordingState.durationMs / 1000L}s",
                style = MaterialTheme.typography.titleMedium,
                color = tokens.dangerRed
            )
            Spacer(Modifier.width(10.dp))
            Row(
                modifier = Modifier.weight(1f).height(26.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                recordingState.amplitudes.takeLast(24).forEach { amp ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height((6f + amp * 20f).dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(tokens.accentPrimary)
                    )
                }
            }
            IconButton(onClick = onTogglePause) {
                Icon(
                    imageVector = if (recordingState.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = "Pause/Resume Recording",
                    tint = tokens.accentPrimary
                )
            }
            Surface(
                shape = CircleShape,
                color = tokens.accentPrimary,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onSend)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send Voice Note", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun MessageActionPopoverDialog(
    message: ChatMessage,
    isMine: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onForward: () -> Unit,
    onCopy: () -> Unit,
    onPin: () -> Unit,
    onTranslate: () -> Unit,
    onBookmark: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val tokens = LocalPrivacyXTokens.current
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(elevated = true, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Quick Emoji Reactions Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("❤️", "👍", "😂", "😮", "😢", "🔥", "🎉", "🙏").forEach { emoji ->
                        val hasCount = message.reactions[emoji]?.isNotEmpty() == true
                        Surface(
                            shape = CircleShape,
                            color = if (hasCount) tokens.accentPrimary.copy(alpha = 0.22f) else tokens.surfaceGlass,
                            border = if (hasCount) BorderStroke(1.dp, tokens.accentPrimary) else null,
                            modifier = Modifier
                                .clickable { onReact(emoji) }
                                .testTag("react_emoji_$emoji")
                        ) {
                            Text(emoji, modifier = Modifier.padding(7.dp))
                        }
                    }
                }
                HorizontalDivider(color = tokens.surfaceBorder)
                PopoverActionRow(Icons.AutoMirrored.Rounded.Reply, "Reply", onClick = onReply)
                PopoverActionRow(Icons.AutoMirrored.Rounded.Forward, "Forward", onClick = onForward)
                PopoverActionRow(Icons.Rounded.ContentCopy, "Copy Text", onClick = onCopy)
                PopoverActionRow(Icons.Rounded.PushPin, if (message.isPinned) "Unpin" else "Pin Message", onClick = onPin)
                PopoverActionRow(Icons.Rounded.Translate, "Translate with AI", onClick = onTranslate)
                PopoverActionRow(Icons.Rounded.Bookmark, "Save to Bookmarks", onClick = onBookmark)
                if (isMine && !message.isDeleted) {
                    PopoverActionRow(Icons.Rounded.Edit, "Edit Message", onClick = onEdit)
                    HorizontalDivider(color = tokens.surfaceBorder)
                    PopoverActionRow(Icons.Rounded.Delete, "Delete Message", tint = tokens.dangerRed, onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun PopoverActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

@Composable
fun ChatAttachmentSheetDialog(
    compressImages: Boolean,
    onToggleCompress: (Boolean) -> Unit,
    onPickImageVideo: () -> Unit,
    onPickDocument: () -> Unit,
    onCreatePoll: () -> Unit,
    onSetDisappearing: (Long) -> Unit,
    onSetScheduled: (Long) -> Unit,
    onSendSticker: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(elevated = true, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Attach & Actions", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                PopoverActionRow(Icons.Rounded.Image, "Photo or Video (Photo Picker)", onClick = onPickImageVideo)
                PopoverActionRow(Icons.Rounded.Description, "Document (PDF, DOC, ZIP, TXT)", onClick = onPickDocument)
                PopoverActionRow(Icons.Rounded.Poll, "Create Poll", onClick = onCreatePoll)
                PopoverActionRow(Icons.Rounded.Timer, "Disappearing Message (5 min)", onClick = { onSetDisappearing(5L) })
                PopoverActionRow(Icons.Rounded.Schedule, "Schedule Message (+1 min)", onClick = { onSetScheduled(1L) })
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Compress images before upload", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = compressImages, onCheckedChange = onToggleCompress)
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Quick Stickers", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("🚀", "🛡️", "✨", "💜", "🔥", "🎉").forEach { sticker ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { onSendSticker(sticker) }
                        ) {
                            Text(sticker, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreatePollDialog(onDismiss: () -> Unit, onCreate: (String, List<String>) -> Unit) {
    val tokens = LocalPrivacyXTokens.current
    var question by remember { mutableStateOf("") }
    var opt1 by remember { mutableStateOf("") }
    var opt2 by remember { mutableStateOf("") }
    var opt3 by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Poll") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Question") })
                OutlinedTextField(value = opt1, onValueChange = { opt1 = it }, label = { Text("Option 1") })
                OutlinedTextField(value = opt2, onValueChange = { opt2 = it }, label = { Text("Option 2") })
                OutlinedTextField(value = opt3, onValueChange = { opt3 = it }, label = { Text("Option 3 (optional)") })
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(question, listOf(opt1, opt2, opt3)) },
                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary)
            ) { Text("Send Poll") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CreateTopicDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("💬") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Group Topic") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Topic Title") })
                OutlinedTextField(value = emoji, onValueChange = { emoji = it.take(4) }, label = { Text("Emoji Icon") })
            }
        },
        confirmButton = {
            Button(onClick = { onCreate(title, emoji) }, enabled = title.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ForwardConversationDialog(
    conversations: List<Conversation>,
    onDismiss: () -> Unit,
    onSelect: (Conversation) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forward To") },
        text = {
            LazyColumn(modifier = Modifier.height(220.dp)) {
                items(conversations, key = { it.id }) { conv ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(conv) }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(conv.title, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun FullScreenZoomableImageDialog(imageUrl: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                    }
                }
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Zoomable Image",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale)
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close Image", tint = Color.White)
            }
        }
    }
}

@Composable
fun FullScreenVideoDialog(videoUrl: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(Uri.parse(videoUrl))
                        setMediaController(MediaController(ctx).apply { setAnchorView(this@apply) })
                        start()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close Video", tint = Color.White)
            }
        }
    }
}

@Composable
fun ChatInfoDialog(
    conversation: Conversation,
    onToggleMute: () -> Unit,
    onLeave: () -> Unit,
    onReport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var reportReason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(conversation.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Type: ${conversation.type} • Members: ${conversation.memberUids.size}")
                if (conversation.description.isNotBlank()) {
                    Text(conversation.description, style = MaterialTheme.typography.bodySmall)
                }
                if (conversation.inviteCode.isNotBlank()) {
                    Text("Invite code: ${conversation.inviteCode}", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = onToggleMute) { Text("Toggle Mute Notifications") }
                if (conversation.type != "direct") {
                    TextButton(onClick = onLeave) { Text("Leave ${conversation.type}") }
                }
                OutlinedTextField(
                    value = reportReason,
                    onValueChange = { reportReason = it },
                    label = { Text("Report reason (optional)") },
                    singleLine = true
                )
                if (reportReason.isNotBlank()) {
                    Button(onClick = { onReport(reportReason) }) { Text("Submit Report") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
