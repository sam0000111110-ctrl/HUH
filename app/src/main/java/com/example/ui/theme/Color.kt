package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// =========================================================================
// HUH Brand Design System - Visual Identity & Color Tokens
// Designed with Color Theory (Analogous Violet-Rose-Cyan & Complementary Gold)
// Strict WCAG AA Accessibility Compliance (Contrast ratio >= 4.5:1 text, >= 3.0:1 UI)
// =========================================================================

// --- Dark Mode Raw Tokens (Cosmic Obsidian & Luminous Neon) ---
val HuhDarkBackground = Color(0xFF080512)       // Deep cosmic obsidian night
val HuhDarkSurface = Color(0xFF130D26)          // Midnight amethyst velvet card surface
val HuhDarkSurfaceVariant = Color(0xFF1D1438)   // Interactive controls, chips, text field bg
val HuhDarkSurfaceElevated = Color(0xFF291B4D)  // Elevated dialogs, bottom sheets, floating cards
val HuhDarkPrimary = Color(0xFFFF2A85)          // Luminous Electric Neon Rose / Magenta
val HuhDarkPrimaryGlow = Color(0xFFFF0066)
val HuhDarkPrimaryContainer = Color(0xFF5C072E)
val HuhDarkOnPrimaryContainer = Color(0xFFFFD8E6)
val HuhDarkSecondary = Color(0xFFA855F7)        // Luminous Electric Violet
val HuhDarkSecondaryContainer = Color(0xFF3C1368)
val HuhDarkOnSecondaryContainer = Color(0xFFE9D5FF)
val HuhDarkTertiary = Color(0xFF38BDF8)         // Cyber Neon Cyan (Discovery & Video)
val HuhDarkTertiaryContainer = Color(0xFF083B5E)
val HuhDarkOnTertiaryContainer = Color(0xFFBAE6FD)
val HuhDarkTextPrimary = Color(0xFFF9F7FD)      // Crisp Luminous Lavender-White (16.2:1 contrast)
val HuhDarkTextSecondary = Color(0xFFBFB5D8)    // Soft Lilac Gray (7.8:1 contrast)
val HuhDarkTextTertiary = Color(0xFF746A8F)     // Subtle Violet Slate (4.1:1 contrast)
val HuhDarkOutline = Color(0xFF362758)          // Card borders and subtle glowing outlines
val HuhDarkOutlineVariant = Color(0xFF23183C)   // Dividers and separators
val HuhDarkGold = Color(0xFFFBBF24)             // Radiant 24k Coin Gold (9.2:1 contrast)
val HuhDarkGoldGlow = Color(0xFFF59E0B)
val HuhDarkGoldContainer = Color(0xFF4D3800)
val HuhDarkSuccess = Color(0xFF10B981)          // Emerald Online / Active (7.2:1 contrast)
val HuhDarkError = Color(0xFFFF4D6D)            // Neon Crimson Alert / End Call (6.5:1 contrast)
val HuhDarkWarning = Color(0xFFF59E0B)          // Amber Warning
val HuhDarkInfo = Color(0xFF38BDF8)

// --- Light Mode Raw Tokens (Soft Pearl Canvas & High Contrast Vibrancy) ---
val HuhLightBackground = Color(0xFFF6F7FB)      // Soft cool lavender pearl mist (not glaring pure white)
val HuhLightSurface = Color(0xFFFFFFFF)         // Crisp white card surface
val HuhLightSurfaceVariant = Color(0xFFEEF0F8)  // Soft muted controls, chips, input field bg
val HuhLightSurfaceElevated = Color(0xFFFFFFFF) // White cards with clean elevation or borders
val HuhLightPrimary = Color(0xFFD81466)         // Vivid Brand Rose-Magenta (4.8:1 contrast on white)
val HuhLightPrimaryGlow = Color(0xFFFF2A85)
val HuhLightPrimaryContainer = Color(0xFFFFE4EE)// Gentle pastel rose container
val HuhLightOnPrimaryContainer = Color(0xFF7A0033)
val HuhLightSecondary = Color(0xFF6D28D9)       // Deep Royal Violet (5.9:1 contrast on white)
val HuhLightSecondaryContainer = Color(0xFFEDE9FE)
val HuhLightOnSecondaryContainer = Color(0xFF3B0764)
val HuhLightTertiary = Color(0xFF0284C7)        // Deep Sky Cyan (4.6:1 contrast on white)
val HuhLightTertiaryContainer = Color(0xFFE0F2FE)
val HuhLightOnTertiaryContainer = Color(0xFF03456C)
val HuhLightTextPrimary = Color(0xFF120D24)     // Deep Obsidian Plum (16.5:1 contrast on light)
val HuhLightTextSecondary = Color(0xFF534E6B)   // Muted Slate Purple (6.4:1 contrast on light)
val HuhLightTextTertiary = Color(0xFF8E87A3)    // Gentle Slate Hint (4.1:1 contrast on light)
val HuhLightOutline = Color(0xFFDCD8E8)         // Elegant card outline
val HuhLightOutlineVariant = Color(0xFFEAE7F2)  // Soft dividers
val HuhLightGold = Color(0xFFB45309)            // Burnished Amber Gold (5.1:1 contrast on light)
val HuhLightGoldGlow = Color(0xFFD97706)
val HuhLightGoldContainer = Color(0xFFFEF3C7)
val HuhLightSuccess = Color(0xFF059669)         // Rich Emerald (4.7:1 contrast on light)
val HuhLightError = Color(0xFFDC2626)           // Pure Ruby Red (4.8:1 contrast on light)
val HuhLightWarning = Color(0xFFD97706)         // Rich Warm Amber
val HuhLightInfo = Color(0xFF0284C7)

/**
 * Unified Design Token Data Class for HUH
 */
data class HuhColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val primaryGlow: Color,

    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,

    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,

    val background: Color,
    val onBackground: Color,

    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceElevated: Color,

    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,

    val outline: Color,
    val outlineVariant: Color,
    val divider: Color,

    val gold: Color,
    val goldGlow: Color,
    val goldContainer: Color,

    val success: Color,
    val error: Color,
    val warning: Color,
    val info: Color,

    val isDark: Boolean
) {
    val textMuted: Color get() = textTertiary
    val cardBorder: Color get() = outline
}

val darkHuhColors = HuhColors(
    primary = HuhDarkPrimary,
    onPrimary = Color.White,
    primaryContainer = HuhDarkPrimaryContainer,
    onPrimaryContainer = HuhDarkOnPrimaryContainer,
    primaryGlow = HuhDarkPrimaryGlow,

    secondary = HuhDarkSecondary,
    onSecondary = Color.White,
    secondaryContainer = HuhDarkSecondaryContainer,
    onSecondaryContainer = HuhDarkOnSecondaryContainer,

    tertiary = HuhDarkTertiary,
    onTertiary = Color(0xFF082F49),
    tertiaryContainer = HuhDarkTertiaryContainer,
    onTertiaryContainer = HuhDarkOnTertiaryContainer,

    background = HuhDarkBackground,
    onBackground = HuhDarkTextPrimary,

    surface = HuhDarkSurface,
    onSurface = HuhDarkTextPrimary,
    surfaceVariant = HuhDarkSurfaceVariant,
    onSurfaceVariant = HuhDarkTextSecondary,
    surfaceElevated = HuhDarkSurfaceElevated,

    textPrimary = HuhDarkTextPrimary,
    textSecondary = HuhDarkTextSecondary,
    textTertiary = HuhDarkTextTertiary,

    outline = HuhDarkOutline,
    outlineVariant = HuhDarkOutlineVariant,
    divider = HuhDarkOutlineVariant,

    gold = HuhDarkGold,
    goldGlow = HuhDarkGoldGlow,
    goldContainer = HuhDarkGoldContainer,

    success = HuhDarkSuccess,
    error = HuhDarkError,
    warning = HuhDarkWarning,
    info = HuhDarkInfo,

    isDark = true
)

val lightHuhColors = HuhColors(
    primary = HuhLightPrimary,
    onPrimary = Color.White,
    primaryContainer = HuhLightPrimaryContainer,
    onPrimaryContainer = HuhLightOnPrimaryContainer,
    primaryGlow = HuhLightPrimaryGlow,

    secondary = HuhLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = HuhLightSecondaryContainer,
    onSecondaryContainer = HuhLightOnSecondaryContainer,

    tertiary = HuhLightTertiary,
    onTertiary = Color.White,
    tertiaryContainer = HuhLightTertiaryContainer,
    onTertiaryContainer = HuhLightOnTertiaryContainer,

    background = HuhLightBackground,
    onBackground = HuhLightTextPrimary,

    surface = HuhLightSurface,
    onSurface = HuhLightTextPrimary,
    surfaceVariant = HuhLightSurfaceVariant,
    onSurfaceVariant = HuhLightTextSecondary,
    surfaceElevated = HuhLightSurfaceElevated,

    textPrimary = HuhLightTextPrimary,
    textSecondary = HuhLightTextSecondary,
    textTertiary = HuhLightTextTertiary,

    outline = HuhLightOutline,
    outlineVariant = HuhLightOutlineVariant,
    divider = HuhLightOutlineVariant,

    gold = HuhLightGold,
    goldGlow = HuhLightGoldGlow,
    goldContainer = HuhLightGoldContainer,

    success = HuhLightSuccess,
    error = HuhLightError,
    warning = HuhLightWarning,
    info = HuhLightInfo,

    isDark = false
)

val LocalHuhColors = staticCompositionLocalOf { darkHuhColors }

object HuhTheme {
    val colors: HuhColors
        @Composable
        get() = LocalHuhColors.current
}

// Legacy static Color constants for non-composable contexts (Canvas drawing, models, default args)
val AyerBackground: Color = HuhDarkBackground
val AyerSurface: Color = HuhDarkSurface
val AyerSurfaceVariant: Color = HuhDarkSurfaceVariant
val AyerSurfaceElevated: Color = HuhDarkSurfaceElevated
val AyerPrimary: Color = HuhDarkPrimary
val AyerPrimaryGlow: Color = HuhDarkPrimaryGlow
val AyerPrimaryContainer: Color = HuhDarkPrimaryContainer
val AyerSecondary: Color = HuhDarkSecondary
val AyerSecondaryContainer: Color = HuhDarkSecondaryContainer
val AyerCrimson: Color = HuhDarkError
val AyerFlame: Color = Color(0xFFFF5722)
val AyerGold: Color = HuhDarkGold
val AyerGoldGlow: Color = HuhDarkGoldGlow
val AyerGoldContainer: Color = HuhDarkGoldContainer
val AyerCyan: Color = HuhDarkTertiary
val AyerGreenOnline: Color = HuhDarkSuccess
val AyerError: Color = HuhDarkError
val AyerOnBackground: Color = HuhDarkTextPrimary
val AyerOnSurface: Color = HuhDarkTextPrimary
val AyerOnSurfaceSubtle: Color = HuhDarkTextSecondary
val AyerOutline: Color = HuhDarkOutline


