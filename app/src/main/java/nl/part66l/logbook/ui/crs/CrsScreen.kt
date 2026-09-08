package nl.part66l.logbook.ui.crs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.CrsEntity
import nl.part66l.logbook.ui.documents.openPdf
import nl.part66l.logbook.ui.theme.Part66ConfirmGreen
import nl.part66l.logbook.ui.theme.part66TopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrsScreen(
    onClose: () -> Unit,
    viewModel: CrsViewModel = hiltViewModel(),
) {
    val issued by viewModel.issued.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Certificates") },
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
            if (issued.isNotEmpty()) {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Issued", style = MaterialTheme.typography.titleMedium)
                        issued.forEachIndexed { index, crs ->
                            if (index > 0) HorizontalDivider()
                            CrsRow(
                                crs = crs,
                                onClick = { crs.pdfLocalPath?.let { openPdf(context, it) } },
                                onPhotoAttached = { path -> viewModel.onPhotoAttached(crs.id, path) },
                                onPhotoRemoved = { viewModel.onPhotoRemoved(crs.id) },
                            )
                        }
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Generate certificate", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Unsigned, for printing and signing by hand. Local hardware-backed signing " +
                            "(§9.3) isn't built yet — this produces the same document you'd hand-sign.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.limitations,
                        onValueChange = viewModel::onLimitationsChange,
                        label = { Text("Limitations to airworthiness or operations") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.maintenanceIncomplete, onCheckedChange = viewModel::onMaintenanceIncompleteChange)
                        Text("Maintenance could not be completed")
                    }
                    Button(
                        onClick = viewModel::generate,
                        enabled = !state.generating,
                        colors = ButtonDefaults.buttonColors(containerColor = Part66ConfirmGreen),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.generating) "Generating…" else "Generate CRS")
                    }
                }
            }
        }
    }
}

@Composable
private fun CrsRow(crs: CrsEntity, onClick: () -> Unit, onPhotoAttached: (String) -> Unit, onPhotoRemoved: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showRemovePhotoConfirm by remember { mutableStateOf(false) }
    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                copySignedPhotoToInternalStorage(context, uri)?.let(onPhotoAttached)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = crs.pdfLocalPath != null, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(crs.number, style = MaterialTheme.typography.titleSmall)
            Text(
                "${crs.completionDate} · ${crs.signatureState.displayLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (crs.pdfLocalPath != null) {
            Text("📄", style = MaterialTheme.typography.titleMedium)
        }
        val signedPhotoPath = crs.signedPhotoLocalPath
        if (signedPhotoPath != null) {
            IconButton(onClick = { openSignedPhoto(context, signedPhotoPath) }) {
                Text("🖼️", style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = { showRemovePhotoConfirm = true }) { Text("✕") }
        } else {
            IconButton(onClick = { photoPickerLauncher.launch("image/*") }) {
                Text("📷", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (showRemovePhotoConfirm) {
        AlertDialog(
            onDismissRequest = { showRemovePhotoConfirm = false },
            title = { Text("Remove this photo?") },
            text = { Text("This removes the attached signed-copy photo from ${crs.number}. The original file on your device is untouched — you can attach it again if needed.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemovePhotoConfirm = false
                    onPhotoRemoved()
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showRemovePhotoConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
