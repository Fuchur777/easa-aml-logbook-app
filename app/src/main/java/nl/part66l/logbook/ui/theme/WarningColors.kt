package nl.part66l.logbook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import nl.part66l.logbook.domain.WarningLevel

/** The colour a [WarningLevel] shows in, or null for [WarningLevel.NONE] — callers keep their default colour in that case. */
@Composable
fun WarningLevel.color(): Color? = when (this) {
    WarningLevel.NONE -> null
    WarningLevel.AMBER -> if (isSystemInDarkTheme()) Part66WarningAmberDark else Part66WarningAmber
    WarningLevel.RED -> MaterialTheme.colorScheme.error
}

/**
 * The label colour legible on the fill [color] returns, or null for [WarningLevel.NONE] — the
 * caller supplies its own pair in that case.
 *
 * Amber needs a dark label (white is 4.0:1 on the light amber and 1.7:1 on the dark one), red
 * needs a light one (black on the error red is only 3.2:1). There is no single value that works
 * on both, which is exactly why the fill must never be handed out on its own.
 */
@Composable
fun WarningLevel.onColor(): Color? = when (this) {
    WarningLevel.NONE -> null
    WarningLevel.AMBER -> NeutralDark06
    WarningLevel.RED -> MaterialTheme.colorScheme.onError
}
