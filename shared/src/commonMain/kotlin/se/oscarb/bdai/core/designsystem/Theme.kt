package se.oscarb.bdai.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Google Gemini-inspired palette: Deep Indigo, Electric Blue, and Soft Violet Sparkle accents
val GeminiBlue = Color(0xFF4285F4)
val GeminiIndigo = Color(0xFF5E5CE6)
val GeminiViolet = Color(0xFF9B72CB)
val GeminiRose = Color(0xFFD96570)

val GeminiSparkleBrush = Brush.linearGradient(
    colors = listOf(GeminiBlue, GeminiViolet, GeminiRose)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B5BDB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE3FF),
    onPrimaryContainer = Color(0xFF0A1B5C),
    secondary = Color(0xFF6741D9),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDE7FF),
    onSecondaryContainer = Color(0xFF230F5A),
    tertiary = Color(0xFF9C36B5),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF8E2FF),
    onTertiaryContainer = Color(0xFF380047),
    background = Color(0xFFF8F9FE),
    onBackground = Color(0xFF191C24),
    surface = Color(0xFFF8F9FE),
    onSurface = Color(0xFF191C24),
    surfaceVariant = Color(0xFFE3E6F4),
    onSurfaceVariant = Color(0xFF444756),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F3FC),
    surfaceContainer = Color(0xFFEBEEF8),
    surfaceContainerHigh = Color(0xFFE4E7F3),
    surfaceContainerHighest = Color(0xFFDDE1EE),
    outline = Color(0xFF757788),
    outlineVariant = Color(0xFFC5C7D7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondary = Color(0xFFD0BCFF),
    onSecondary = Color(0xFF381E72),
    secondaryContainer = Color(0xFF4F378B),
    onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = Color(0xFFF0B0FF),
    onTertiary = Color(0xFF550A67),
    tertiaryContainer = Color(0xFF702482),
    onTertiaryContainer = Color(0xFFF8E2FF),
    background = Color(0xFF101218),
    onBackground = Color(0xFFE2E2EB),
    surface = Color(0xFF101218),
    onSurface = Color(0xFFE2E2EB),
    surfaceVariant = Color(0xFF434754),
    onSurfaceVariant = Color(0xFFC4C6D4),
    surfaceContainerLowest = Color(0xFF0B0D13),
    surfaceContainerLow = Color(0xFF171A22),
    surfaceContainer = Color(0xFF1D202A),
    surfaceContainerHigh = Color(0xFF272A36),
    surfaceContainerHighest = Color(0xFF323542),
    outline = Color(0xFF8E909E),
    outlineVariant = Color(0xFF434754),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
