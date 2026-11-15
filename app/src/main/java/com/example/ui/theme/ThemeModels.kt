package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppThemeId(
    val id: String,
    val title: String,
    val subtitle: String
) {
    MATERIAL_LIQUID_GLASS(
        id = "material_liquid_glass",
        title = "Material Liquid Glass",
        subtitle = "Lavender, purple & pink ambient glass"
    ),
    PURE_IOS_GLASS(
        id = "pure_ios_glass",
        title = "Pure iOS Glass",
        subtitle = "Deep translucent crystal & specular highlights"
    ),
    CLASSIC_BW(
        id = "classic_bw",
        title = "Classic Black & White",
        subtitle = "High-contrast minimal monochrome"
    );

    companion object {
        fun fromId(raw: String?): AppThemeId =
            entries.firstOrNull { it.id == raw } ?: MATERIAL_LIQUID_GLASS
    }
}

enum class AppearanceMode(val id: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromId(raw: String?): AppearanceMode =
            entries.firstOrNull { it.id == raw } ?: SYSTEM
    }
}

@Immutable
data class PrivacyXThemeTokens(
    val themeId: AppThemeId,
    val isDark: Boolean,
    val usesGlass: Boolean,
    val usesBlur: Boolean,
    val backgroundBrush: Brush,
    val surfaceGlass: Color,
    val surfaceGlassElevated: Color,
    val surfaceBorder: Color,
    val navBarBackground: Color,
    val sentBubbleGradient: List<Color>,
    val sentBubbleText: Color,
    val receivedBubbleColor: Color,
    val receivedBubbleText: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val accentPink: Color,
    val radialFabGradient: List<Color>,
    val radialOverlayColor: Color,
    val cardCornerRadius: Dp,
    val borderWidth: Dp,
    val blurRadius: Dp,
    val onlineGreen: Color,
    val warningAmber: Color,
    val dangerRed: Color
)
