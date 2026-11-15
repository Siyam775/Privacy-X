package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.theme.AppThemeId
import com.example.ui.theme.LocalPrivacyXTokens
import com.example.ui.theme.buildThemeTokens

enum class MainNavTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
) {
    CHATS("chats", "Chats", Icons.Rounded.ChatBubble, "nav_tab_chats"),
    DISCOVER("discover", "Discover", Icons.Rounded.Explore, "nav_tab_discover"),
    STORIES("stories", "Stories", Icons.Rounded.AutoStories, "nav_tab_stories"),
    COMMUNITIES("communities", "Spaces", Icons.Rounded.Groups, "nav_tab_communities"),
    CALLS("calls", "Calls", Icons.Rounded.Call, "nav_tab_calls"),
    PROFILE("profile", "Profile", Icons.Rounded.Person, "nav_tab_profile")
}

@Composable
fun PrivacyXBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val tokens = LocalPrivacyXTokens.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.backgroundBrush)
    ) {
        if (tokens.usesGlass) {
            val orbPrimary = tokens.accentPrimary.copy(alpha = if (tokens.isDark) 0.24f else 0.14f)
            val orbPink = tokens.accentPink.copy(alpha = if (tokens.isDark) 0.18f else 0.12f)
            val orbBlue = tokens.accentSecondary.copy(alpha = if (tokens.isDark) 0.16f else 0.10f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(orbPrimary, Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.12f),
                        radius = size.width * 0.72f
                    ),
                    radius = size.width * 0.72f,
                    center = Offset(size.width * 0.15f, size.height * 0.12f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(orbPink, Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.34f),
                        radius = size.width * 0.65f
                    ),
                    radius = size.width * 0.65f,
                    center = Offset(size.width * 0.88f, size.height * 0.34f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(orbBlue, Color.Transparent),
                        center = Offset(size.width * 0.35f, size.height * 0.88f),
                        radius = size.width * 0.78f
                    ),
                    radius = size.width * 0.78f,
                    center = Offset(size.width * 0.35f, size.height * 0.88f)
                )
            }
        }
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp? = null,
    elevated: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalPrivacyXTokens.current
    val radius = cornerRadius ?: tokens.cardCornerRadius
    val shape = RoundedCornerShape(radius)
    val bgColor = if (elevated) tokens.surfaceGlassElevated else tokens.surfaceGlass
    val shadowElevation = if (tokens.usesGlass) 10.dp else 0.dp

    Surface(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = tokens.accentPrimary.copy(alpha = 0.14f),
                spotColor = tokens.accentPrimary.copy(alpha = 0.16f)
            )
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = shape,
        color = bgColor,
        border = BorderStroke(tokens.borderWidth, tokens.surfaceBorder)
    ) {
        Column(content = content)
    }
}

@Composable
fun GlassPillSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    testTag: String = "search_input"
) {
    val tokens = LocalPrivacyXTokens.current
    val shape = RoundedCornerShape(999.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (tokens.usesGlass) 6.dp else 0.dp,
                shape = shape,
                ambientColor = tokens.accentPrimary.copy(alpha = 0.10f)
            ),
        shape = shape,
        color = tokens.surfaceGlassElevated,
        border = BorderStroke(tokens.borderWidth, tokens.surfaceBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(tokens.accentPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(testTag)
                )
            }
        }
    }
}

@Composable
fun UserAvatar(
    name: String,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    isOnline: Boolean = false,
    isCircular: Boolean = true,
    gradientSeed: Int = 0
) {
    val tokens = LocalPrivacyXTokens.current
    val shape = if (isCircular) CircleShape else RoundedCornerShape(size * 0.3f)
    val initial = name.trim().firstOrNull()?.uppercase() ?: "P"
    val gradients = listOf(
        listOf(tokens.accentPrimary, tokens.accentSecondary),
        listOf(Color(0xFF0EA5E9), Color(0xFF6366F1)),
        listOf(Color(0xFF14B8A6), Color(0xFF64748B)),
        listOf(tokens.accentPink, tokens.accentPrimary)
    )
    val chosen = gradients[(kotlin.math.abs(name.hashCode() + gradientSeed)) % gradients.size]

    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Brush.linearGradient(chosen))
                .border(1.5.dp, tokens.surfaceBorder, shape),
            contentAlignment = Alignment.Center
        ) {
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "$name avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
        }
        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(size * 0.28f)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(tokens.onlineGreen)
                    .border(2.dp, tokens.surfaceGlassElevated, CircleShape)
            )
        }
    }
}

@Composable
fun FloatingCapsuleNavBar(
    selectedTab: MainNavTab,
    unreadChatsCount: Int,
    onSelectTab: (MainNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPrivacyXTokens.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (tokens.usesGlass) 18.dp else 2.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = tokens.accentPrimary.copy(alpha = 0.22f)
                ),
            shape = RoundedCornerShape(32.dp),
            color = tokens.navBarBackground,
            border = BorderStroke(tokens.borderWidth, tokens.surfaceBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainNavTab.entries.forEach { tab ->
                    val selected = tab == selectedTab
                    val pillColor by animateColorAsState(
                        targetValue = if (selected) tokens.accentPrimary.copy(alpha = 0.16f) else Color.Transparent,
                        label = "navPill"
                    )
                    val iconTint by animateColorAsState(
                        targetValue = if (selected) tokens.accentPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "navTint"
                    )
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(pillColor)
                            .clickable { onSelectTab(tab) }
                            .minimumInteractiveComponentSize()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag(tab.testTag),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BadgedBox(
                            badge = {
                                if (tab == MainNavTab.CHATS && unreadChatsCount > 0) {
                                    Badge(
                                        containerColor = tokens.accentPrimary,
                                        contentColor = Color.White
                                    ) {
                                        Text(
                                            text = if (unreadChatsCount > 99) "99+" else unreadChatsCount.toString(),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = iconTint,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = iconTint,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdaptiveNavRail(
    selectedTab: MainNavTab,
    onSelectTab: (MainNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPrivacyXTokens.current
    NavigationRail(
        modifier = modifier,
        containerColor = tokens.navBarBackground,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        MainNavTab.entries.forEach { tab ->
            NavigationRailItem(
                selected = tab == selectedTab,
                onClick = { onSelectTab(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                modifier = Modifier.testTag(tab.testTag)
            )
        }
    }
}

@Composable
fun LiveThemePreviewCard(
    themeId: AppThemeId,
    isDarkPreview: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val previewTokens = buildThemeTokens(
        themeId = themeId,
        isDark = isDarkPreview,
        customAccentColor = null
    )
    val currentTokens = LocalPrivacyXTokens.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .testTag("theme_card_${themeId.id}"),
        shape = RoundedCornerShape(22.dp),
        color = previewTokens.surfaceGlassElevated,
        border = BorderStroke(
            width = if (isSelected) 2.5.dp else 1.dp,
            color = if (isSelected) currentTokens.accentPrimary else previewTokens.surfaceBorder
        )
    ) {
        Box(
            modifier = Modifier
                .background(previewTokens.backgroundBrush)
                .padding(14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = themeId.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isDarkPreview) Color.White else Color(0xFF1A142D)
                    )
                    if (isSelected) {
                        Surface(
                            color = currentTokens.accentPrimary,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (currentTokens.themeId == AppThemeId.CLASSIC_BW && currentTokens.isDark) Color.Black else Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = themeId.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDarkPreview) Color(0xFFD1CBE8) else Color(0xFF564D6F)
                )
                Spacer(modifier = Modifier.height(10.dp))
                // Mini chat preview inside card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = previewTokens.receivedBubbleColor,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(0.8.dp, previewTokens.surfaceBorder)
                    ) {
                        Text(
                            text = "Encrypted",
                            style = MaterialTheme.typography.bodySmall,
                            color = previewTokens.receivedBubbleText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(previewTokens.sentBubbleGradient))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Glass UI",
                            style = MaterialTheme.typography.bodySmall,
                            color = previewTokens.sentBubbleText
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedAiOrb(
    isListeningOrStreaming: Boolean,
    reducedMotion: Boolean,
    size: Dp = 150.dp,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPrivacyXTokens.current
    val infiniteTransition = rememberInfiniteTransition(label = "aiOrb")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orbPulse"
    )
    val effectiveScale = if (reducedMotion || !isListeningOrStreaming) 1f else pulse

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = (this.size.minDimension / 2f) * 0.85f * effectiveScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tokens.accentPink.copy(alpha = 0.35f),
                        tokens.accentPrimary.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.25f
                ),
                radius = radius * 1.25f
            )
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF93C5FD),
                        tokens.accentSecondary,
                        tokens.accentPink,
                        Color(0xFFFDE68A)
                    ),
                    start = Offset(center.x - radius, center.y - radius),
                    end = Offset(center.x + radius, center.y + radius)
                ),
                radius = radius
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.72f), Color.Transparent),
                    center = Offset(center.x - radius * 0.32f, center.y - radius * 0.32f),
                    radius = radius * 0.55f
                ),
                radius = radius * 0.55f,
                center = Offset(center.x - radius * 0.32f, center.y - radius * 0.32f)
            )
        }
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = "PrivacyX AI Orb",
            tint = Color.White,
            modifier = Modifier.size(size * 0.28f)
        )
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    val tokens = LocalPrivacyXTokens.current
    GlassCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = tokens.accentPrimary.copy(alpha = 0.14f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = tokens.accentPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
