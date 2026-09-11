package nl.part66l.logbook.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The brand blue on every top bar app-wide, with white content for contrast.
 *
 * Names [Part66Blue] rather than the `primary` role on purpose. This is the one place the blue
 * is seen as the brand, so it has to be #176FC1 in both themes; the role has to be legible as
 * button text on a dark surface, which the same value is not. Reading the role here tied the two
 * together and meant one of them always lost.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun part66TopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Part66Blue,
    titleContentColor = Color.White,
    navigationIconContentColor = Color.White,
    actionIconContentColor = Color.White,
)
