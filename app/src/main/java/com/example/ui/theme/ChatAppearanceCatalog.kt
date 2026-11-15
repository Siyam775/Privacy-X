package com.example.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.ui.components.PrivacyXBackground

enum class ChatColorPreset(
    val id: String,
    val label: String,
    val sentStartHex: Long,
    val sentEndHex: Long,
    val accentHex: Long,
    val darkBubbleText: Boolean = false
) {
    DEFAULT("default", "Theme Default", 0xFF8B5CF6, 0xFF6366F1, 0xFF7C3AED),
    ROYAL_LAVENDER("royal_lavender", "Royal Lavender", 0xFF7C3AED, 0xFFA855F7, 0xFF7C3AED),
    MESSENGER_POP("messenger_pop", "Messenger Pop", 0xFF6366F1, 0xFFEC4899, 0xFF8B5CF6),
    OCEAN_BLUE("ocean_blue", "Ocean Blue", 0xFF0284C7, 0xFF38BDF8, 0xFF0284C7),
    WHATSAPP_EMERALD("whatsapp_emerald", "Emerald Mint", 0xFF059669, 0xFF10B981, 0xFF059669),
    TELEGRAM_SKY("telegram_sky", "Telegram Sky", 0xFF2563EB, 0xFF06B6D4, 0xFF2563EB),
    INSTA_SUNSET("insta_sunset", "Insta Sunset", 0xFFF97316, 0xFFE11D48, 0xFFE11D48),
    ROSE_QUARTZ("rose_quartz", "Rose Quartz", 0xFFDB2777, 0xFFF472B6, 0xFFDB2777),
    CRIMSON_FLAME("crimson_flame", "Crimson Flame", 0xFFDC2626, 0xFFF97316, 0xFFDC2626),
    MIDNIGHT_INDIGO("midnight_indigo", "Midnight Indigo", 0xFF312E81, 0xFF4F46E5, 0xFF4F46E5),
    AMBER_GOLD("amber_gold", "Golden Amber", 0xFFD97706, 0xFFF59E0B, 0xFFD97706),
    MONO_GRAPHITE("mono_graphite", "Minimal Graphite", 0xFF18181B, 0xFF52525B, 0xFF27272A);

    val sentGradient: List<Color>
        get() = listOf(Color(sentStartHex), Color(sentEndHex))

    val accentColor: Color
        get() = Color(accentHex)

    companion object {
        fun fromId(raw: String?): ChatColorPreset =
            entries.firstOrNull { it.id == raw } ?: DEFAULT
    }
}

enum class ChatWallpaperPreset(
    val id: String,
    val label: String,
    val gradientColors: List<Long>,
    val isDarkCanvas: Boolean,
    val patternType: String
) {
    DEFAULT("default", "App Glass", emptyList(), false, "none"),
    LAVENDER_AURORA("lavender_aurora", "Lavender Aurora", listOf(0xFFF3E8FF, 0xFFE0E7FF, 0xFFFCE7F3), false, "orbs"),
    DOODLE_CLASSIC("doodle_classic", "Privacy Doodle", listOf(0xFFECE5DD, 0xFFE3DDD5), false, "doodles"),
    OCEAN_BREEZE("ocean_breeze", "Ocean Breeze", listOf(0xFFE0F2FE, 0xFFCCFBF1, 0xFFE0E7FF), false, "waves"),
    BLUSH_SILK("blush_silk", "Blush Silk", listOf(0xFFFFF1F2, 0xFFFCE7F3, 0xFFFAF5FF), false, "orbs"),
    SAGE_FOREST("sage_forest", "Sage Forest", listOf(0xFFECFDF5, 0xFFD1FAE5, 0xFFE0F2FE), false, "doodles"),
    STARLIGHT_NIGHT("starlight_night", "Starlight Night", listOf(0xFF0F172A, 0xFF1E1B4B, 0xFF311042), true, "stars"),
    CYBER_VIOLET("cyber_violet", "Cyber Grid", listOf(0xFF090D16, 0xFF1A103D, 0xFF111827), true, "grid"),
    WARM_SAND("warm_sand", "Warm Parchment", listOf(0xFFFEF3C7, 0xFFFDE68A, 0xFFFFFBEB), false, "waves"),
    AMOLED_ONYX("amoled_onyx", "Pure Onyx", listOf(0xFF050507, 0xFF0E0E14), true, "stars");

    companion object {
        fun fromId(raw: String?): ChatWallpaperPreset =
            entries.firstOrNull { it.id == raw } ?: DEFAULT
    }
}

@Composable
fun ChatWallpaperLayer(
    wallpaperPresetId: String,
    customWallpaperUri: String,
    dimAlpha: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val preset = ChatWallpaperPreset.fromId(wallpaperPresetId)
    val tokens = LocalPrivacyXTokens.current

    Box(modifier = modifier.fillMaxSize()) {
        when {
            customWallpaperUri.isNotBlank() -> {
                AsyncImage(
                    model = customWallpaperUri,
                    contentDescription = "Custom chat wallpaper",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            preset == ChatWallpaperPreset.DEFAULT || preset.gradientColors.isEmpty() -> {
                PrivacyXBackground {}
            }
            else -> {
                val colors = preset.gradientColors.map { Color(it) }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(colors))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val ink = if (preset.isDarkCanvas) {
                            Color.White.copy(alpha = 0.08f)
                        } else {
                            tokens.accentPrimary.copy(alpha = 0.09f)
                        }
                        when (preset.patternType) {
                            "stars" -> {
                                val stepX = size.width / 5f
                                val stepY = size.height / 9f
                                for (row in 0..8) {
                                    for (col in 0..4) {
                                        val cx = col * stepX + ((row * 37) % 48)
                                        val cy = row * stepY + ((col * 29) % 40)
                                        val r = if ((row + col) % 3 == 0) 2.5f else 1.4f
                                        drawCircle(color = Color.White.copy(alpha = 0.32f), radius = r, center = Offset(cx, cy))
                                    }
                                }
                            }
                            "grid" -> {
                                val spacing = 64f
                                var x = 0f
                                while (x < size.width) {
                                    drawLine(ink, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                                    x += spacing
                                }
                                var y = 0f
                                while (y < size.height) {
                                    drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                                    y += spacing
                                }
                            }
                            "doodles" -> {
                                val stepX = size.width / 4f
                                val stepY = size.height / 7f
                                for (r in 0..6) {
                                    for (c in 0..3) {
                                        val cx = c * stepX + stepX * 0.5f
                                        val cy = r * stepY + stepY * 0.5f
                                        if ((r + c) % 2 == 0) {
                                            drawCircle(ink, radius = 22f, center = Offset(cx, cy), style = Stroke(2f))
                                        } else {
                                            drawRect(
                                                ink,
                                                topLeft = Offset(cx - 16f, cy - 16f),
                                                size = androidx.compose.ui.geometry.Size(32f, 32f),
                                                style = Stroke(2f)
                                            )
                                        }
                                    }
                                }
                            }
                            "waves" -> {
                                for (i in 1..6) {
                                    val cy = size.height * (i / 6.5f)
                                    drawCircle(
                                        brush = Brush.radialGradient(listOf(ink, Color.Transparent), Offset(size.width * (i % 2), cy), size.width * 0.6f),
                                        radius = size.width * 0.6f,
                                        center = Offset(size.width * (i % 2), cy)
                                    )
                                }
                            }
                            else -> {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        listOf(tokens.accentPrimary.copy(alpha = 0.16f), Color.Transparent),
                                        Offset(size.width * 0.2f, size.height * 0.2f),
                                        size.width * 0.7f
                                    ),
                                    radius = size.width * 0.7f,
                                    center = Offset(size.width * 0.2f, size.height * 0.2f)
                                )
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        listOf(tokens.accentPink.copy(alpha = 0.14f), Color.Transparent),
                                        Offset(size.width * 0.85f, size.height * 0.75f),
                                        size.width * 0.7f
                                    ),
                                    radius = size.width * 0.7f,
                                    center = Offset(size.width * 0.85f, size.height * 0.75f)
                                )
                            }
                        }
                    }
                }
            }
        }
        if (dimAlpha > 0f && (customWallpaperUri.isNotBlank() || preset != ChatWallpaperPreset.DEFAULT)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = dimAlpha.coerceIn(0f, 0.78f)))
            )
        }
        content()
    }
}
