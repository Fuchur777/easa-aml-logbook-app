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
import nl.part66l.logbook.ui.documents.openPdf
import nl.part66l.logbook.ui.theme.part66TopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SigningInfoScreen(
    onClose: () -> Unit,
    viewModel: SigningInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
            }
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
