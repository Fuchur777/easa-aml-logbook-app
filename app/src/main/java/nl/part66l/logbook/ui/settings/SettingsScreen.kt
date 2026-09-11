package nl.part66l.logbook.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.part66l.logbook.ui.theme.part66TopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onClose: () -> Unit,
    onSigningInfo: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val showArchivedAircraft by viewModel.showArchivedAircraft.collectAsStateWithLifecycle()
    val recencyReductionGranted by viewModel.recencyReductionGranted.collectAsStateWithLifecycle()
    val recencyReductionReference by viewModel.recencyReductionReference.collectAsStateWithLifecycle()
    val researchCountsTowardRecency by viewModel.researchCountsTowardRecency.collectAsStateWithLifecycle()
    val crsNumberTemplate by viewModel.crsNumberTemplate.collectAsStateWithLifecycle()
    val crsAnnualReset by viewModel.crsAnnualReset.collectAsStateWithLifecycle()
    val crsStartAt by viewModel.crsStartAt.collectAsStateWithLifecycle()
    val crsNumberingError by viewModel.crsNumberingError.collectAsStateWithLifecycle()
    val crsShowCertifyingStaffContact by viewModel.crsShowCertifyingStaffContact.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Digital signing", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "The local signing key's certificate and fingerprint (§9.3) — export it once to " +
                            "submit to your competent authority.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onSigningInfo, modifier = Modifier.fillMaxWidth()) {
                        Text("Signing certificate")
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Aircraft", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show archived aircraft")
                            Text(
                                "Archived aircraft are hidden from the list by default",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = showArchivedAircraft, onCheckedChange = viewModel::onShowArchivedAircraftChange)
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Recency reduction", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = recencyReductionGranted,
                            onCheckedChange = viewModel::onRecencyReductionGrantedChange,
                        )
                        Text("50% recency reduction granted by my competent authority")
                    }
                    if (recencyReductionGranted) {
                        OutlinedTextField(
                            value = recencyReductionReference,
                            onValueChange = viewModel::onRecencyReductionReferenceChange,
                            label = { Text("Reference") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Research & paperwork", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = researchCountsTowardRecency,
                            onCheckedChange = viewModel::onResearchCountsTowardRecencyChange,
                        )
                        Text("Research & paperwork days count toward Route A recency")
                    }
                    Text(
                        "Off by default — research & paperwork isn't in AMC 66.A.20(b)(2)'s activity list. " +
                            "An entry that combines it with a real regulatory activity always counts, " +
                            "regardless of this setting; this only affects a day where research & paperwork " +
                            "was the only activity logged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Certifying staff contact details", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Print phone and email on the CRS")
                            Text(
                                "Off by default. Adds your phone number and email, from your profile, next to " +
                                    "the licence number on every generated certificate.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = crsShowCertifyingStaffContact, onCheckedChange = viewModel::onCrsShowCertifyingStaffContactChange)
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("CRS numbering", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Placeholders: {YYYY}, {REG} (registration, or NOREG for bench/component work) and " +
                            "{SEQ:N} (zero-padded to N digits) — a fixed prefix is just literal text in front " +
                            "of them. {SEQ:N} is required, used once, and must be last. Issued numbers never " +
                            "change when this format does.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = crsNumberTemplate,
                        onValueChange = viewModel::onCrsNumberTemplateChange,
                        label = { Text("Template") },
                        isError = crsNumberingError != null,
                        supportingText = { crsNumberingError?.let { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = crsStartAt,
                        onValueChange = viewModel::onCrsStartAtChange,
                        label = { Text("Start at") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reset sequence every year")
                            Text(
                                "Off keeps the sequence climbing across year boundaries, even though {YYYY} still prints.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = crsAnnualReset, onCheckedChange = viewModel::onCrsAnnualResetChange)
                    }
                }
            }
        }
    }
}
