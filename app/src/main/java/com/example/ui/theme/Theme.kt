package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val HuhDarkColorScheme = darkColorScheme(
    primary = HuhDarkPrimary,
    onPrimary = Color.White,
    primaryContainer = HuhDarkPrimaryContainer,
    onPrimaryContainer = HuhDarkOnPrimaryContainer,
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
    outline = HuhDarkOutline,
    outlineVariant = HuhDarkOutlineVariant,
    error = HuhDarkError,
    onError = Color.White
)

private val HuhLightColorScheme = lightColorScheme(
    primary = HuhLightPrimary,
    onPrimary = Color.White,
    primaryContainer = HuhLightPrimaryContainer,
    onPrimaryContainer = HuhLightOnPrimaryContainer,
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
    outline = HuhLightOutline,
    outlineVariant = HuhLightOutlineVariant,
    error = HuhLightError,
    onError = Color.White
)

@Composable
fun AyerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) HuhDarkColorScheme else HuhLightColorScheme
    val huhColors = if (darkTheme) darkHuhColors else lightHuhColors

    CompositionLocalProvider(LocalHuhColors provides huhColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

