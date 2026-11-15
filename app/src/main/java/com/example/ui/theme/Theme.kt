package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalPrivacyXTokens = staticCompositionLocalOf {
    buildThemeTokens(
        themeId = AppThemeId.MATERIAL_LIQUID_GLASS,
        isDark = false,
        customAccentColor = null
    )
}

val PrivacyXShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

fun parseHexColorOrNull(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    return try {
        val cleaned = hex.removePrefix("#")
        val parsed = cleaned.toLong(16)
        when (cleaned.length) {
            6 -> Color(0xFF000000L or parsed)
            8 -> Color(parsed)
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

fun buildThemeTokens(
    themeId: AppThemeId,
    isDark: Boolean,
    customAccentColor: Color?
): PrivacyXThemeTokens {
    val accent = customAccentColor ?: when (themeId) {
        AppThemeId.MATERIAL_LIQUID_GLASS -> RoyalViolet
        AppThemeId.PURE_IOS_GLASS -> IosCrystalViolet
        AppThemeId.CLASSIC_BW -> if (isDark) PureWhite else PureBlack
    }

    return when (themeId) {
        AppThemeId.MATERIAL_LIQUID_GLASS -> if (isDark) {
            PrivacyXThemeTokens(
                themeId = themeId,
                isDark = true,
                usesGlass = true,
                usesBlur = true,
                backgroundBrush = Brush.linearGradient(
                    colors = listOf(MidnightLavenderBg, MidnightPlumBg, MidnightVioletBg)
                ),
                surfaceGlass = DarkGlassSurface.copy(alpha = 0.78f),
                surfaceGlassElevated = Color(0xFF2D2552).copy(alpha = 0.88f),
                surfaceBorder = DarkGlassBorder.copy(alpha = 0.65f),
                navBarBackground = Color(0xFF1F193B).copy(alpha = 0.88f),
                sentBubbleGradient = listOf(accent, ElectricLavender),
                sentBubbleText = PureWhite,
                receivedBubbleColor = Color(0xFF2B234A).copy(alpha = 0.85f),
                receivedBubbleText = LavenderGlowText,
                accentPrimary = accent,
                accentSecondary = ElectricLavender,
                accentPink = SoftOrchidPink,
                radialFabGradient = listOf(accent, SoftOrchidPink),
                radialOverlayColor = Color(0xFF0C0818).copy(alpha = 0.62f),
                cardCornerRadius = 24.dp,
                borderWidth = 1.dp,
                blurRadius = 18.dp,
                onlineGreen = OnlineEmerald,
                warningAmber = AmberAlert,
                dangerRed = CoralDanger
            )
        } else {
            PrivacyXThemeTokens(
                themeId = themeId,
                isDark = false,
                usesGlass = true,
                usesBlur = true,
                backgroundBrush = Brush.linearGradient(
                    colors = listOf(LavenderMist, LavenderCloud, BlushPetal, MintWhisper)
                ),
                surfaceGlass = PureWhite.copy(alpha = 0.76f),
                surfaceGlassElevated = PureWhite.copy(alpha = 0.90f),
                surfaceBorder = PureWhite.copy(alpha = 0.92f),
                navBarBackground = PureWhite.copy(alpha = 0.84f),
                sentBubbleGradient = listOf(accent, ElectricLavender),
                sentBubbleText = PureWhite,
                receivedBubbleColor = PureWhite.copy(alpha = 0.88f),
                receivedBubbleText = DeepPlumInk,
                accentPrimary = accent,
                accentSecondary = ElectricLavender,
                accentPink = SoftOrchidPink,
                radialFabGradient = listOf(accent, ElectricLavender),
                radialOverlayColor = Color(0xFFE9E2FF).copy(alpha = 0.62f),
                cardCornerRadius = 24.dp,
                borderWidth = 1.2.dp,
                blurRadius = 18.dp,
                onlineGreen = OnlineEmerald,
                warningAmber = AmberAlert,
                dangerRed = CoralDanger
            )
        }

        AppThemeId.PURE_IOS_GLASS -> if (isDark) {
            PrivacyXThemeTokens(
                themeId = themeId,
                isDark = true,
                usesGlass = true,
                usesBlur = true,
                backgroundBrush = Brush.verticalGradient(
                    colors = listOf(IosDarkTop, IosDarkMid, IosDarkBottom)
                ),
                surfaceGlass = Color(0xFF1B1E36).copy(alpha = 0.62f),
                surfaceGlassElevated = Color(0xFF272B4A).copy(alpha = 0.78f),
                surfaceBorder = PureWhite.copy(alpha = 0.24f),
                navBarBackground = Color(0xFF171A30).copy(alpha = 0.72f),
                sentBubbleGradient = listOf(IosCrystalBlue, accent),
                sentBubbleText = PureWhite,
                receivedBubbleColor = PureWhite.copy(alpha = 0.14f),
                receivedBubbleText = PureWhite,
                accentPrimary = accent,
                accentSecondary = IosCrystalBlue,
                accentPink = Color(0xFFD9B8FF),
                radialFabGradient = listOf(IosCrystalBlue, accent),
                radialOverlayColor = Color(0xFF060914).copy(alpha = 0.68f),
                cardCornerRadius = 28.dp,
                borderWidth = 0.9.dp,
                blurRadius = 24.dp,
                onlineGreen = OnlineEmerald,
                warningAmber = AmberAlert,
                dangerRed = CoralDanger
            )
        } else {
            PrivacyXThemeTokens(
                themeId = themeId,
                isDark = false,
                usesGlass = true,
                usesBlur = true,
                backgroundBrush = Brush.verticalGradient(
                    colors = listOf(IosIceTop, IosIceMid, IosIceBottom)
                ),
                surfaceGlass = PureWhite.copy(alpha = 0.58f),
                surfaceGlassElevated = PureWhite.copy(alpha = 0.76f),
                surfaceBorder = PureWhite.copy(alpha = 0.95f),
                navBarBackground = PureWhite.copy(alpha = 0.66f),
                sentBubbleGradient = listOf(IosCrystalBlue, accent),
                sentBubbleText = PureWhite,
                receivedBubbleColor = PureWhite.copy(alpha = 0.76f),
                receivedBubbleText = Color(0xFF111322),
                accentPrimary = accent,
                accentSecondary = IosCrystalBlue,
                accentPink = Color(0xFFC084FC),
                radialFabGradient = listOf(IosCrystalBlue, accent),
                radialOverlayColor = Color(0xFFDDE7FF).copy(alpha = 0.58f),
                cardCornerRadius = 28.dp,
                borderWidth = 1.dp,
                blurRadius = 24.dp,
                onlineGreen = OnlineEmerald,
                warningAmber = AmberAlert,
                dangerRed = CoralDanger
            )
        }

        AppThemeId.CLASSIC_BW -> if (isDark) {
            PrivacyXThemeTokens(
                themeId = themeId,
                isDark = true,
                usesGlass = false,
                usesBlur = false,
                backgroundBrush = Brush.verticalGradient(listOf(PureBlack, PureBlack)),
                surfaceGlass = CharcoalSurface,
                surfaceGlassElevated = Color(0xFF1E1E22),
                surfaceBorder = NeutralHairlineDark,
                navBarBackground = Color(0xFF0E0E10),
                sentBubbleGradient = listOf(PureWhite, PureWhite),
                sentBubbleText = PureBlack,
                receivedBubbleColor = Color(0xFF1C1C1F),
                receivedBubbleText = PureWhite,
                accentPrimary = PureWhite,
                accentSecondary = Color(0xFFD4D4D8),
                accentPink = Color(0xFFA1A1AA),
                radialFabGradient = listOf(PureWhite, Color(0xFFE4E4E7)),
                radialOverlayColor = PureBlack.copy(alpha = 0.78f),
                cardCornerRadius = 14.dp,
                borderWidth = 1.dp,
                blurRadius = 0.dp,
                onlineGreen = OnlineEmerald,
                warningAmber = AmberAlert,
                dangerRed = CoralDanger
            )
        } else {
            PrivacyXThemeTokens(
                themeId = themeId,
                isDark = false,
                usesGlass = false,
                usesBlur = false,
                backgroundBrush = Brush.verticalGradient(listOf(PureWhite, OffWhite)),
                surfaceGlass = PureWhite,
                surfaceGlassElevated = OffWhite,
                surfaceBorder = NeutralHairlineLight,
                navBarBackground = PureWhite,
                sentBubbleGradient = listOf(PureBlack, PureBlack),
                sentBubbleText = PureWhite,
                receivedBubbleColor = Color(0xFFECECEF),
                receivedBubbleText = PureBlack,
                accentPrimary = PureBlack,
                accentSecondary = Color(0xFF3F3F46),
                accentPink = Color(0xFF52525B),
                radialFabGradient = listOf(PureBlack, Color(0xFF27272A)),
                radialOverlayColor = PureBlack.copy(alpha = 0.45f),
                cardCornerRadius = 14.dp,
                borderWidth = 1.dp,
                blurRadius = 0.dp,
                onlineGreen = OnlineEmerald,
                warningAmber = AmberAlert,
                dangerRed = CoralDanger
            )
        }
    }
}

@Composable
fun PrivacyXTheme(
    themeId: AppThemeId = AppThemeId.MATERIAL_LIQUID_GLASS,
    appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    customAccentHex: String? = null,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = when (appearanceMode) {
        AppearanceMode.SYSTEM -> systemDark
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }

    val customAccent = if (themeId == AppThemeId.CLASSIC_BW) null else parseHexColorOrNull(customAccentHex)
    val tokens = buildThemeTokens(themeId = themeId, isDark = isDark, customAccentColor = customAccent)

    val animDuration = if (reducedMotion) 0 else 280
    val animatedPrimary = animateColorAsState(tokens.accentPrimary, tween(animDuration), label = "primary").value
    val animatedSurface = animateColorAsState(tokens.surfaceGlass, tween(animDuration), label = "surface").value

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = animatedPrimary,
            onPrimary = if (themeId == AppThemeId.CLASSIC_BW) PureBlack else PureWhite,
            secondary = tokens.accentSecondary,
            onSecondary = if (themeId == AppThemeId.CLASSIC_BW) PureBlack else PureWhite,
            tertiary = tokens.accentPink,
            background = if (themeId == AppThemeId.CLASSIC_BW) PureBlack else MidnightLavenderBg,
            onBackground = PureWhite,
            surface = animatedSurface,
            onSurface = PureWhite,
            surfaceVariant = tokens.surfaceGlassElevated,
            onSurfaceVariant = if (themeId == AppThemeId.CLASSIC_BW) NeutralInkSecondaryDark else Color(0xFFC9C0E8),
            outline = tokens.surfaceBorder,
            error = CoralDanger
        )
    } else {
        lightColorScheme(
            primary = animatedPrimary,
            onPrimary = PureWhite,
            secondary = tokens.accentSecondary,
            onSecondary = PureWhite,
            tertiary = tokens.accentPink,
            background = if (themeId == AppThemeId.CLASSIC_BW) PureWhite else LavenderMist,
            onBackground = if (themeId == AppThemeId.CLASSIC_BW) PureBlack else DeepPlumInk,
            surface = animatedSurface,
            onSurface = if (themeId == AppThemeId.CLASSIC_BW) PureBlack else DeepPlumInk,
            surfaceVariant = tokens.surfaceGlassElevated,
            onSurfaceVariant = if (themeId == AppThemeId.CLASSIC_BW) NeutralInkSecondaryLight else MutedPlumGrey,
            outline = tokens.surfaceBorder,
            error = CoralDanger
        )
    }

    CompositionLocalProvider(LocalPrivacyXTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PrivacyXTypography,
            shapes = PrivacyXShapes,
            content = content
        )
    }
}
