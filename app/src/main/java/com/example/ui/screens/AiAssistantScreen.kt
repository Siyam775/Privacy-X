package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AiConversation
import com.example.data.model.AiMessage
import com.example.ui.components.AnimatedAiOrb
import com.example.ui.components.GlassCard
import com.example.ui.components.PrivacyXBackground
import com.example.ui.theme.LocalPrivacyXTokens

@Composable
fun AiAssistantScreen(
    sessions: List<AiConversation>,
    activeSessionId: String?,
    messages: List<AiMessage>,
    streamingText: String,
    isStreaming: Boolean,
    errorMessage: String?,
    aiConsentGranted: Boolean,
    reducedMotion: Boolean,
    onBack: () -> Unit,
    onGrantConsent: () -> Unit,
    onSelectSession: (String) -> Unit,
    onStartNewSession: () -> Unit,
    onSendPrompt: (prompt: String, image: Bitmap?) -> Unit,
    onStopGeneration: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val tokens = LocalPrivacyXTokens.current
    var promptInput by remember { mutableStateOf("") }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                @Suppress("DEPRECATION")
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                attachedBitmap = bmp
            }
        }
    }

    PrivacyXBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Header
            GlassCard(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                elevated = true
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("ai_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PrivacyX AI Assistant", style = MaterialTheme.typography.titleMedium)
                        Text("Powered by Firebase AI Logic (Gemini)", style = MaterialTheme.typography.labelSmall, color = tokens.accentPrimary)
                    }
                    IconButton(onClick = onStartNewSession, modifier = Modifier.testTag("ai_new_session_button")) {
                        Icon(Icons.Rounded.Add, contentDescription = "New AI Session", tint = tokens.accentPrimary)
                    }
                }
            }

            // Session Switcher Row
            if (sessions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sessions.take(8).forEach { s ->
                        val active = s.id == activeSessionId
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (active) tokens.accentPrimary else tokens.surfaceGlassElevated,
                            border = BorderStroke(0.8.dp, tokens.surfaceBorder),
                            modifier = Modifier.clickable { onSelectSession(s.id) }
                        ) {
                            Text(
                                text = s.title.take(18),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (!aiConsentGranted) {
                Box(modifier = Modifier.weight(1f).padding(20.dp), contentAlignment = Alignment.Center) {
                    GlassCard(elevated = true) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AnimatedAiOrb(isListeningOrStreaming = false, reducedMotion = reducedMotion, size = 120.dp)
                            Spacer(Modifier.height(14.dp))
                            Text("Enable PrivacyX AI Assistant", style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "When enabled, prompts and explicitly attached images are processed via Firebase AI Logic with App Check protection. Your preference can be toggled anytime in Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = onGrantConsent,
                                colors = ButtonDefaults.buttonColors(containerColor = tokens.accentPrimary),
                                modifier = Modifier.fillMaxWidth().testTag("ai_grant_consent_button")
                            ) {
                                Text("Accept & Start AI Chat")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (messages.isEmpty() && !isStreaming) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AnimatedAiOrb(isListeningOrStreaming = true, reducedMotion = reducedMotion, size = 160.dp)
                                Spacer(Modifier.height(12.dp))
                                Text("How can I assist you today?", style = MaterialTheme.typography.headlineMedium)
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(
                                        "Rewrite professionally",
                                        "Fix grammar",
                                        "Translate to Spanish",
                                        "Draft privacy checklist"
                                    ).forEach { chip ->
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = tokens.surfaceGlassElevated,
                                            border = BorderStroke(1.dp, tokens.surfaceBorder),
                                            modifier = Modifier.clickable { promptInput = chip }
                                        ) {
                                            Text(chip, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(messages, key = { it.id }) { msg ->
                        val isUser = msg.role == "user"
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 320.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .then(
                                        if (isUser) Modifier.background(Brush.linearGradient(tokens.sentBubbleGradient))
                                        else Modifier.background(tokens.receivedBubbleColor)
                                    )
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isUser) tokens.sentBubbleText else tokens.receivedBubbleText
                                )
                            }
                        }
                    }

                    if (isStreaming) {
                        item {
                            GlassCard(modifier = Modifier.widthIn(max = 320.dp)) {
                                Text(
                                    text = streamingText.ifBlank { "Thinking..." },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }

                    if (!errorMessage.isNullOrBlank()) {
                        item {
                            Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = tokens.dangerRed)
                        }
                    }
                }

                // Image attachment preview
                attachedBitmap?.let { bmp ->
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Attached Image",
                            modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Image ready for multimodal AI", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { attachedBitmap = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove Image")
                        }
                    }
                }

                // Composer
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = tokens.surfaceGlassElevated,
                    border = BorderStroke(tokens.borderWidth, tokens.surfaceBorder),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Rounded.Image, contentDescription = "Attach Photo for AI Vision", tint = tokens.accentPrimary)
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            if (promptInput.isEmpty()) {
                                Text("Ask Gemini anything...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            BasicTextField(
                                value = promptInput,
                                onValueChange = { promptInput = it },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(tokens.accentPrimary),
                                modifier = Modifier.fillMaxWidth().testTag("ai_prompt_input")
                            )
                        }
                        if (isStreaming) {
                            IconButton(onClick = onStopGeneration) {
                                Icon(Icons.Rounded.Stop, contentDescription = "Stop", tint = tokens.dangerRed)
                            }
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = tokens.accentPrimary,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        val p = promptInput
                                        val img = attachedBitmap
                                        promptInput = ""
                                        attachedBitmap = null
                                        onSendPrompt(p, img)
                                    }
                                    .testTag("ai_send_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send AI Prompt", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
