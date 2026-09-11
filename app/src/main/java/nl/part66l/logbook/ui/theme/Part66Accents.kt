package nl.part66l.logbook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The confirm green, lightened for dark mode. The light value is unchanged from the
 * app icon's green; on a dark ground that same green sinks into the surface, so dark
 * mode uses a brighter step of the same hue (8.6:1 against the dark surface).
 */
@Composable
fun confirmGreen(): Color = if (isSystemInDarkTheme()) Part66ConfirmGreenDark else Part66ConfirmGreen

/**
 * Save / confirm buttons. The label is near-black on both greens — white reaches only
 * 3.5:1 on the light-mode green and is hopeless on the brighter dark-mode one, whereas
 * the dark label clears 5.5:1 against either.
 */
@Composable
fun part66ConfirmButtonColors(): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = confirmGreen(),
    contentColor = NeutralDark06,
)


/**
 * A switch sitting on the brand-blue top bar.
 *
 * Material's checked switch draws its track *and* its border in [primary], which on a
 * primary-coloured bar leaves a white thumb apparently floating in the bar with no
 * control around it. A white border gives the switch its edge back; the track stays
 * blue, so "on" still reads as filled rather than outlined.
 */
@Composable
fun part66TopBarSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedBorderColor = Color.White,
    checkedThumbColor = Color.White,
)

/**
 * The label colour that is legible on [confirmGreen].
 *
 * White is not: it reaches 3.5:1 on the light-mode green and only 2.1:1 on the brighter
 * dark-mode one. Exposed alongside the fill so no call site has to guess — handing out a
 * background without its on-colour is what let the recency badge ship unreadable.
 */
@Composable
fun onConfirmGreen(): Color = NeutralDark06
