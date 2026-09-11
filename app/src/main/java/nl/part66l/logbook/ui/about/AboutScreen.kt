package nl.part66l.logbook.ui.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import nl.part66l.logbook.BuildConfig
import nl.part66l.logbook.ui.theme.part66TopAppBarColors

private const val DEVELOPER_EMAIL = "frank+amlog@schellenberg.nl"

/** One third-party dependency actually shipped in the release build — test-only libraries (Robolectric, JUnit, ...) aren't listed since they never reach a device. */
private data class OpenSourceLicence(val name: String, val licence: String)

private val LICENCES = listOf(
    OpenSourceLicence("Kotlin", "Apache License 2.0"),
    OpenSourceLicence("AndroidX (Jetpack Compose, Room, Lifecycle, Navigation, Paging, DataStore, Biometric, ExifInterface, Core)", "Apache License 2.0"),
    OpenSourceLicence("Material Components for Android", "Apache License 2.0"),
    OpenSourceLicence("Dagger / Hilt", "Apache License 2.0"),
    OpenSourceLicence("kotlinx.coroutines", "Apache License 2.0"),
    OpenSourceLicence("kotlinx.serialization", "Apache License 2.0"),
    OpenSourceLicence("PdfBox-Android", "Apache License 2.0"),
    OpenSourceLicence("Bouncy Castle (bcprov / bcpkix)", "Bouncy Castle Licence (MIT-style)"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onClose: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = { IconButton(onClick = onClose) { Text("✕") } },
                colors = part66TopAppBarColors(),
                expandedHeight = 48.dp,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("AMlog", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Developer", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Questions, bug reports, feature requests — get in touch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { openEmail(context, DEVELOPER_EMAIL) }) {
                        Text(DEVELOPER_EMAIL)
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Disclaimer", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "AMlog does not determine your privileges or recency status — it records what " +
                            "you enter and shows a calculation from it. You remain solely responsible for " +
                            "verifying compliance with your competent authority's requirements before " +
                            "relying on anything this app displays.\n\n" +
                            "Certificates signed on-device use a hardware-backed key with no third-party " +
                            "trust provider behind it — your competent authority, not this app, is the " +
                            "trust anchor for that signature (see the Signing certificate screen).\n\n" +
                            "AMlog has no backend and never transmits your data anywhere.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Licences used", style = MaterialTheme.typography.titleMedium)
                    LICENCES.forEach { entry ->
                        Column {
                            Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                            Text(entry.licence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Opens the device's mail app with [address] pre-filled — the user composes and sends, or doesn't, entirely on their own. Silently does nothing if no mail app is installed. */
private fun openEmail(context: Context, address: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address"))
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No mail app installed — nothing sensible to do from here.
    }
}
