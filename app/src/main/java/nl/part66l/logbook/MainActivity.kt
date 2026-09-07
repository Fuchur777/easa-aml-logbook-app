package nl.part66l.logbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import nl.part66l.logbook.ui.Part66LogApp
import nl.part66l.logbook.ui.theme.Part66LogTheme

// @AndroidEntryPoint is required for hiltViewModel() to work anywhere in this
// Activity's Compose tree.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Part66LogTheme {
                Part66LogApp()
            }
        }
    }
}
