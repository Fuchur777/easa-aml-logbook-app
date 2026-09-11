package nl.part66l.logbook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Part66Blue,
    onPrimary = NeutralWhite,
    primaryContainer = Part66BlueContainer,
    onPrimaryContainer = Part66OnBlueContainer,
    inversePrimary = Part66BlueLight,

    secondary = Secondary,
    onSecondary = NeutralWhite,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,

    tertiary = Tertiary,
    onTertiary = NeutralWhite,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,

    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral90,
    onSurfaceVariant = Neutral35,

    surfaceContainerLowest = NeutralWhite,
    surfaceContainerLow = Neutral97,
    surfaceContainer = Neutral95,
    surfaceContainerHigh = Neutral93,
    surfaceContainerHighest = Neutral90,
    surfaceDim = Neutral87,
    surfaceBright = Neutral99,

    outline = Neutral55,
    outlineVariant = Neutral80,

    inverseSurface = Neutral22,
    inverseOnSurface = Neutral95,

    error = ErrorRed,
    onError = NeutralWhite,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
)

private val DarkColorScheme = darkColorScheme(
    /*
     * The brand blue is #176FC1 wherever it is seen AS the brand — that is the top bar, and
     * part66TopAppBarColors now names Part66Blue directly so it is identical in both themes.
     *
     * The `primary` *role* is a different job. Material hands it to every TextButton and
     * OutlinedButton as their content colour, and #176FC1 on this dark surface measures 3.5:1 —
     * below the 4.5:1 a line of text needs. Sixteen files use those buttons and none override
     * the colour, so tying the role to the brand fill made every one of them fail in dark mode.
     * The role therefore takes the lighter tint here (10.7:1), and onPrimary goes dark to suit
     * the few places primary is still a fill, such as a checked Switch track.
     */
    primary = Part66BlueLight,
    onPrimary = Part66OnBlueDark,
    primaryContainer = Part66BlueContainerDark,
    onPrimaryContainer = Part66OnBlueContainerDark,
    inversePrimary = Part66Blue,

    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = SecondaryContainer,

    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = TertiaryContainer,

    background = NeutralDark09,
    onBackground = NeutralDark91,
    surface = NeutralDark09,
    onSurface = NeutralDark91,
    surfaceVariant = Neutral22,
    onSurfaceVariant = NeutralDark78,

    surfaceContainerLowest = NeutralDark06,
    surfaceContainerLow = NeutralDark12,
    surfaceContainer = NeutralDark14,
    surfaceContainerHigh = NeutralDark18,
    surfaceContainerHighest = Neutral22,
    surfaceDim = NeutralDark09,
    surfaceBright = NeutralDark24,

    outline = NeutralDark60,
    outlineVariant = NeutralDark30,

    inverseSurface = NeutralDark91,
    inverseOnSurface = Neutral22,

    error = ErrorRedDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
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
