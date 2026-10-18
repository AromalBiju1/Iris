package dev.elyssia.iris.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val IrisPrimary = Color(0xFF6C5CE7)
private val IrisPrimaryContainer = Color(0xFFE5DEFF)
private val IrisSecondary = Color(0xFFA192F7)
private val IrisOnPrimary = Color(0xFFFFFFFF)
private val IrisBackground = Color(0xFFFAF9FF)
private val IrisSurface = Color(0xFFFFFFFF)
private val IrisDarkBackground = Color(0xFF14121F)
private val IrisDarkSurface = Color(0xFF201D2E)

private val LightScheme = lightColorScheme(
    primary = IrisPrimary,
    onPrimary = IrisOnPrimary,
    primaryContainer = IrisPrimaryContainer,
    onPrimaryContainer = Color(0xFF21005D),
    secondary = IrisSecondary,
    background = IrisBackground,
    onBackground = Color(0xFF1C1B22),
    surface = IrisSurface,
    onSurface = Color(0xFF1C1B22),
    outline = Color(0xFFB9B5CC),
    surfaceVariant = Color(0xFFEDEBF7),
    onSurfaceVariant = Color(0xFF4A4760),
)

private val DarkScheme = darkColorScheme(
    primary = IrisSecondary,
    onPrimary = Color(0xFF1E1A4A),
    primaryContainer = Color(0xFF453A85),
    onPrimaryContainer = IrisPrimaryContainer,
    secondary = IrisPrimary,
    background = IrisDarkBackground,
    onBackground = Color(0xFFE5E1F5),
    surface = IrisDarkSurface,
    onSurface = Color(0xFFE5E1F5),
    outline = Color(0xFF57526E),
    surfaceVariant = Color(0xFF2C293D),
    onSurfaceVariant = Color(0xFFCAC3E5),
)

@Composable
fun IrisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme,
        content = content,
    )
}
