package nl.part66l.logbook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Part66BlueDark,
    onPrimary = Part66OnBlueDark,
    primaryContainer = Part66BlueContainerDark,
    onPrimaryContainer = Part66OnBlueContainerDark,
)

private val LightColorScheme = lightColorScheme(
    primary = Part66Blue,
    onPrimary = Color.White,
    primaryContainer = Part66BlueContainer,
    onPrimaryContainer = Part66OnBlueContainer,
)

/**
 * Deliberately not using dynamic (Material You / per-device wallpaper) color — the
 * app icon's blue and green are the brand, and should look the same on every device.
 */
@Composable
fun Part66LogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
