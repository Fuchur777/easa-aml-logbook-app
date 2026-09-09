package nl.part66l.logbook

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import nl.part66l.logbook.ui.Part66LogApp
import nl.part66l.logbook.ui.theme.Part66LogTheme

// @AndroidEntryPoint is required for hiltViewModel() to work anywhere in this
// Activity's Compose tree. FragmentActivity (rather than plain ComponentActivity,
// which it extends) is required by androidx.biometric.BiometricPrompt for CrsSigner's
// per-use biometric prompt (§9.3) — it has no plain-Activity constructor.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
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
