package com.example.ui.components

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppThemeId
import com.example.ui.theme.LocalPrivacyXTokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class RadialAction(
    val label: String,
    val icon: ImageVector,
    val ring: Int,
    val angleDeg: Double,
    val testTag: String
) {
    // Inner Ring (3 core quick actions)
    NEW_CHAT("New Chat", Icons.Rounded.ChatBubble, 1, 185.0, "radial_new_chat"),
    VOICE_CALL("Voice Call", Icons.Rounded.Call, 1, 225.0, "radial_voice_call"),
    AI_ASSISTANT("AI Assist", Icons.Rounded.AutoAwesome, 1, 265.0, "radial_ai_assistant"),

    // Middle Ring (5 creation & media actions)
    CREATE_GROUP("Group", Icons.Rounded.GroupAdd, 2, 182.0, "radial_create_group"),
    CREATE_COMMUNITY("Community", Icons.Rounded.Hub, 2, 204.0, "radial_create_community"),
    CREATE_STORY("Story", Icons.Rounded.AutoStories, 2, 226.0, "radial_create_story"),
    SHARE_IMAGE("Image", Icons.Rounded.Image, 2, 248.0, "radial_share_image"),
    SEND_DOCUMENT("Document", Icons.Rounded.Description, 2, 270.0, "radial_send_document"),

    // Outer Ring (4 calls & utilities)
    VIDEO_CALL("Video Call", Icons.Rounded.Videocam, 3, 188.0, "radial_video_call"),
    CHANGE_THEME("Theme", Icons.Rounded.Palette, 3, 212.0, "radial_change_theme"),
    SAVED_MESSAGES("Saved", Icons.Rounded.Bookmark, 3, 236.0, "radial_saved_messages"),
    QUICK_SETTINGS("Settings", Icons.Rounded.Settings, 3, 260.0, "radial_quick_settings")
}

@Composable
fun RadialFabMenuOverlay(
    isExpanded: Boolean,
    reducedMotion: Boolean,
    bottomPaddingDp: Int = 92,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    onActionSelected: (RadialAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPrivacyXTokens.current

    if (isExpanded) {
        BackHandler(onBack = onDismiss)
    }

    val fabIconRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = if (reducedMotion) tween(0) else spring(stiffness = Spring.StiffnessMediumLow),
        label = "fabRotation"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val scaleFactor = (maxWidth.value / 400f).coerceIn(0.84f, 1.15f)

        // Blurred / dimmed tap-blocking scrim when expanded
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(if (reducedMotion) tween(0) else tween(200)),
            exit = fadeOut(if (reducedMotion) tween(0) else tween(160))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(tokens.radialOverlayColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                    .testTag("radial_menu_scrim")
            )
        }

        // Anchor box at bottom-right above floating nav capsule
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 20.dp, bottom = bottomPaddingDp.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isExpanded) {
                RadialAction.entries.forEachIndexed { index, action ->
                    val baseRadiusDp = when (action.ring) {
                        1 -> 98f
                        2 -> 182f
                        else -> 256f
                    } * scaleFactor

                    val progress by animateFloatAsState(
                        targetValue = if (isExpanded) 1f else 0f,
                        animationSpec = if (reducedMotion) {
                            tween(0)
                        } else {
                            spring(
                                dampingRatio = 0.72f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        },
                        label = "radialItem_$index"
                    )

                    val rad = action.angleDeg * PI / 180.0
                    val offsetXDp = (baseRadiusDp * cos(rad) * progress).dp
                    val offsetYDp = (baseRadiusDp * sin(rad) * progress).dp

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = offsetXDp.roundToPx(),
                                    y = offsetYDp.roundToPx()
                                )
                            }
                            .scale(progress.coerceAtLeast(0.01f))
                            .alpha(progress.coerceIn(0f, 1f))
                    ) {
                        Surface(
                            modifier = Modifier
                                .size(48.dp)
                                .minimumInteractiveComponentSize()
                                .shadow(
                                    elevation = if (tokens.usesGlass) 12.dp else 2.dp,
                                    shape = CircleShape,
                                    ambientColor = tokens.accentPrimary.copy(alpha = 0.25f)
                                )
                                .clip(CircleShape)
                                .clickable {
                                    onActionSelected(action)
                                }
                                .testTag(action.testTag)
                                .semantics { contentDescription = action.label },
                            shape = CircleShape,
                            color = tokens.surfaceGlassElevated,
                            border = BorderStroke(tokens.borderWidth, tokens.surfaceBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = action.label,
                                    tint = tokens.accentPrimary,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = tokens.surfaceGlassElevated.copy(alpha = 0.94f),
                            border = BorderStroke(0.6.dp, tokens.surfaceBorder)
                        ) {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.2.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Signature Purple Gradient Circular FAB
            val fabTextTint = if (tokens.themeId == AppThemeId.CLASSIC_BW && tokens.isDark) {
                Color.Black
            } else {
                Color.White
            }
            Surface(
                modifier = Modifier
                    .size(58.dp)
                    .minimumInteractiveComponentSize()
                    .shadow(
                        elevation = if (tokens.usesGlass) 18.dp else 4.dp,
                        shape = CircleShape,
                        ambientColor = tokens.accentPrimary.copy(alpha = 0.45f),
                        spotColor = tokens.accentPrimary.copy(alpha = 0.50f)
                    )
                    .clip(CircleShape)
                    .clickable(onClick = onToggle)
                    .testTag("radial_fab_button"),
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(1.5.dp, tokens.surfaceBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(tokens.radialFabGradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.Close else Icons.Rounded.ChatBubble,
                        contentDescription = if (isExpanded) "Close radial menu" else "Open quick actions menu",
                        tint = fabTextTint,
                        modifier = Modifier
                            .size(25.dp)
                            .rotate(fabIconRotation)
                    )
                }
            }
        }
    }
}
