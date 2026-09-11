package nl.part66l.logbook.ui.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import nl.part66l.logbook.BuildConfig
import nl.part66l.logbook.R
import nl.part66l.logbook.ui.theme.part66TopAppBarColors

private const val DEVELOPER_EMAIL = "frank+amlog@schellenberg.nl"

/** One third-party dependency actually shipped in the release build — test-only libraries (Robolectric, JUnit, ...) aren't listed since they never reach a device. */
private data class OpenSourceLicence(val name: String, val licence: String)

/** One provision the app's behaviour actually traces to, and what it governs here. */
private data class RegulationReference(val reference: String, val appliesTo: String)

private val REGULATIONS = listOf(
    RegulationReference("66.A.20(b)(2) and its AMC", "The recency requirement itself, the 24-month window, and the 100/50-day route."),
    RegulationReference("AMC 66.A.45(h)", "The half-of-the-catalogue route, and the requirement to cover every section."),
    RegulationReference(
        "Appendix II to AMC to Annex III — Table B, and the engine blocks of Table A it cross-references",
        "The task catalogue itself, against which completions are counted.",
    ),
    RegulationReference("NPA 2025-12 (proposed AMC2 66.A.20(b)(2))", "The annual-inspection route, shown but not counted while it remains a proposal."),
    RegulationReference("ML.A.801(d), (e), (f), (g) and AMC1 ML.A.801(e)", "What a certificate of release to service must contain, and how assistance is recorded."),
)

private val LICENCES = listOf(
    OpenSourceLicence("Kotlin", "Apache License 2.0"),
    OpenSourceLicence("AndroidX (Jetpack Compose, Room, Lifecycle, Navigation, Paging, DataStore, WorkManager, Biometric, ExifInterface, Core)", "Apache License 2.0"),
    OpenSourceLicence("Material Components for Android", "Apache License 2.0"),
    OpenSourceLicence("Dagger / Hilt", "Apache License 2.0"),
    OpenSourceLicence("kotlinx.coroutines", "Apache License 2.0"),
    OpenSourceLicence("kotlinx.serialization", "Apache License 2.0"),
    OpenSourceLicence("PdfBox-Android", "Apache License 2.0"),
    OpenSourceLicence("Bouncy Castle (bcprov / bcpkix)", "Bouncy Castle Licence (MIT-style)"),
    OpenSourceLicence("OkHttp (Square)", "Apache License 2.0"),
    // Not open source — but it ships in the app and is what talks to Drive, so it is named here rather than quietly omitted.
    OpenSourceLicence("Google Play services (auth)", "Google APIs Terms of Service — proprietary"),
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
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("AMlog", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Image(
                        painter = painterResource(R.drawable.ic_amlog_logo),
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("How recency is worked out", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Recency is assessed separately for each subcategory you hold, over a rolling " +
                            "24-month window ending today. A subcategory counts as current if any one " +
                            "of the routes below is met — they are alternatives, not requirements to " +
                            "combine. All of them are shown so you can see which one you are living on " +
                            "and how close the others are.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    RouteExplanation(
                        title = "Days of experience",
                        body = "100 days on which you logged maintenance experience in the window, or 50 " +
                            "where your competent authority agreed a reduction in advance. Every logged " +
                            "day counts once, whatever its length.\n\n" +
                            "The same AMC lets up to 20% of the duration be replaced by training, " +
                            "technical support or maintenance planning. AMlog does not model that — it " +
                            "would put a claim flag on every entry to serve a case most independent " +
                            "certifying staff never make, and anyone who does make it has already " +
                            "agreed it with their authority.",
                    )
                    RouteExplanation(
                        title = "Share of the task catalogue",
                        body = "Half of the Appendix II tasks that apply to the subcategory, completed " +
                            "within the window, with at least one completion in every section. Where a " +
                            "task is completed more than once only the latest counts, so repeating work " +
                            "extends it rather than expiring early. A relevant substitute task may " +
                            "stand in for a listed one.",
                    )
                    RouteExplanation(
                        title = "Annual inspections",
                        body = "A proposed third route, shown greyed out and not counted towards your " +
                            "status. It becomes active only if and when the proposal is adopted, at " +
                            "which point it applies to inspections already logged.",
                    )
                    RouteExplanation(
                        title = "Recently certified",
                        body = "Within 24 months of your initial certification date, you are current by " +
                            "default: the window that would show a lapse has not yet fully elapsed. " +
                            "This applies only when that date is recorded on your profile.",
                    )
                    Text(
                        "AMlog can only count what is in it. Work logged on paper, or before you " +
                            "installed the app, is invisible to this calculation — so a subcategory " +
                            "shown as not current may simply be incompletely recorded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Regulations applied", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Rules are taken from the Easy Access Rules for Continuing Airworthiness " +
                            "(Regulation (EU) No 1321/2014), September 2025 revision.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    REGULATIONS.forEach { entry ->
                        Column {
                            Text(entry.reference, style = MaterialTheme.typography.bodyMedium)
                            Text(entry.appliesTo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        "Where a rule changes, the calculation changes with it — nothing is frozen into " +
                            "your records, so past entries are re-evaluated under the rules in force now.",
                        style = MaterialTheme.typography.bodySmall,
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
                            "AMlog has no backend of its own and the developer never receives your " +
                            "data. Two things can copy your records off this device, both into your " +
                            "own Google account and neither to anyone else:\n\n" +
                            "Android's own app backup is on by default, for AMlog as for most apps. It " +
                            "copies your entries, photos and certificates into your Google account's " +
                            "backup so a replacement phone can restore them. It is encrypted with your " +
                            "device screen lock, so Google cannot read it. Turn it off in Android's " +
                            "Settings under Google, Backup.\n\n" +
                            "Google Drive sync is off until you connect it. Once connected, " +
                            "certificates, photos, documents and full-device backups are uploaded to " +
                            "that account and nowhere else. You can disconnect at any time, and the app " +
                            "remains fully functional offline without it.\n\n" +
                            "Your signing key is the exception: it never leaves this device and is in " +
                            "neither copy. A restored install can still verify certificates you have " +
                            "already issued, but signs new ones with a new key.",
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

/** One route's name and what satisfies it, in the "How recency is worked out" card. */
@Composable
private fun RouteExplanation(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
