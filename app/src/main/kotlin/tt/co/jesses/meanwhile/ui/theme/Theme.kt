package tt.co.jesses.meanwhile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Material 3 palette built from the icon: indigo is primary, teal secondary, sun tertiary, and the
 * neutrals lean indigo. Tones are CIE L* with OKLCH hue and chroma, an approximation of Material's
 * HCT ramps; swap in values from Material Theme Builder if exact ones are wanted.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF4C54B4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBE1FF),
    onPrimaryContainer = Color(0xFF160164),
    inversePrimary = Color(0xFFB8C4FF),
    secondary = Color(0xFF25695E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFACF0E2),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Color(0xFF7D5800),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA8),
    onTertiaryContainer = Color(0xFF271900),
    error = Color(0xFFB5261D),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410000),
    background = Color(0xFFFBFCFF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFBFCFF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFDEE2F2),
    onSurfaceVariant = Color(0xFF434653),
    surfaceTint = Color(0xFF4C54B4),
    inverseSurface = Color(0xFF2F3037),
    inverseOnSurface = Color(0xFFEEF0F9),
    outline = Color(0xFF737685),
    outlineVariant = Color(0xFFC2C6D5),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFBFCFF),
    surfaceDim = Color(0xFFD8DAE2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F3FC),
    surfaceContainer = Color(0xFFEBEEF6),
    surfaceContainerHigh = Color(0xFFE6E8F1),
    surfaceContainerHighest = Color(0xFFE0E2EB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8C4FF),
    onPrimary = Color(0xFF24217E),
    primaryContainer = Color(0xFF373B99),
    onPrimaryContainer = Color(0xFFDBE1FF),
    inversePrimary = Color(0xFF4C54B4),
    secondary = Color(0xFF90D3C6),
    onSecondary = Color(0xFF003730),
    secondaryContainer = Color(0xFF005046),
    onSecondaryContainer = Color(0xFFACF0E2),
    tertiary = Color(0xFFF4BE5C),
    onTertiary = Color(0xFF422C00),
    tertiaryContainer = Color(0xFF5F4100),
    onTertiaryContainer = Color(0xFFFFDEA8),
    error = Color(0xFFFFB4A8),
    onError = Color(0xFF690000),
    errorContainer = Color(0xFF930001),
    onErrorContainer = Color(0xFFFFDAD4),
    background = Color(0xFF1A1B21),
    onBackground = Color(0xFFE0E2EB),
    surface = Color(0xFF1A1B21),
    onSurface = Color(0xFFE0E2EB),
    surfaceVariant = Color(0xFF434653),
    onSurfaceVariant = Color(0xFFC2C6D5),
    surfaceTint = Color(0xFFB8C4FF),
    inverseSurface = Color(0xFFE0E2EB),
    inverseOnSurface = Color(0xFF2F3037),
    outline = Color(0xFF8D909F),
    outlineVariant = Color(0xFF434653),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF373940),
    surfaceDim = Color(0xFF121319),
    surfaceContainerLowest = Color(0xFF0D0E14),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF282A30),
    surfaceContainerHighest = Color(0xFF33343B),
)

@Composable
fun MeanwhileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
