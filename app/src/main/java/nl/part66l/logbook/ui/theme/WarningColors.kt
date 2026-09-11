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
