package nl.part66l.logbook.ui.signing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import nl.part66l.logbook.data.SigningKeyEntity
import nl.part66l.logbook.ui.documents.openPdf
import nl.part66l.logbook.ui.theme.part66TopAppBarColors

private val KEY_HISTORY_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SigningInfoScreen(
    onClose: () -> Unit,
    viewModel: SigningInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyHistory by viewModel.keyHistory.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Signing certificate") },
                navigationIcon = { IconButton(onClick = onClose) { Text("✕") } },
                colors = part66TopAppBarColors(),
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
            if (state.loading) {
                CircularProgressIndicator()
            } else {
                Text(
                    "Register this fingerprint with your competent authority once, before relying on " +
                        "locally signed certificates — a signed CRS is only as trustworthy as the record " +
                        "an inspector can check it against. Do this again if you rotate the key or move to a new device.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LabeledValue("Licence holder", state.licenceHolderName)
                        LabeledValue("Licence number", state.licenceNumber)
                        LabeledValue("Issuing authority", state.issuingAuthority)
                        LabeledValue("Signing method", "${state.method} — ${state.keyStorage}")
                        LabeledValue("Authentication", state.authentication)
                        LabeledValue("Certificate subject", state.certificateSubject)
                        SelectionContainer {
                            LabeledValue("Certificate fingerprint (SHA-256)", state.fingerprint)
                        }
                    }
                }

                Button(
                    onClick = { viewModel.exportPdf { path -> openPdf(context, path) } },
                    enabled = !state.exporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.exporting) "Preparing…" else "Export as PDF")
                }

                if (keyHistory.isNotEmpty()) {
                    Card {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Key history", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Every key this device has ever generated for local signing — a key superseded here " +
                                    "(rotated, or invalidated by a change to enrolled biometrics) never signs anything " +
                                    "again, but everything it already signed still checks against its own fingerprint below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            keyHistory.forEachIndexed { index, key ->
                                if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                KeyHistoryRow(key)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyHistoryRow(key: SigningKeyEntity) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            if (key.retiredAt == null) "Current — ${key.keyStorage}" else key.keyStorage,
            style = MaterialTheme.typography.labelLarge,
            color = if (key.retiredAt == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        SelectionContainer {
            Text(key.fingerprint, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Generated ${KEY_HISTORY_DATE_FORMAT.format(key.generatedAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val retiredAt = key.retiredAt
        if (retiredAt != null) {
            Text(
                "Retired ${KEY_HISTORY_DATE_FORMAT.format(retiredAt)}" + (key.retiredReason?.let { " — $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
