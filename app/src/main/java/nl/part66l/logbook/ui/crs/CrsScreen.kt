package nl.part66l.logbook.ui.crs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import nl.part66l.logbook.R
import nl.part66l.logbook.data.CrsEntity
import nl.part66l.logbook.di.LocalKeystoreSignerEntryPoint
import nl.part66l.logbook.domain.SignatureState
import nl.part66l.logbook.signing.BiometricSigningGate
import nl.part66l.logbook.ui.documents.openPdf
import nl.part66l.logbook.ui.theme.part66ConfirmButtonColors
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
    val activity = context as? FragmentActivity
    val coroutineScope = rememberCoroutineScope()
    val localSigner = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, LocalKeystoreSignerEntryPoint::class.java).localKeystoreSigner()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Certificate of Release to Service") },
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
                    Text("Generate CRS", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.maintenanceIncomplete, onCheckedChange = viewModel::onMaintenanceIncompleteChange)
                        Text("Maintenance could not be completed")
                    }
                    if (state.maintenanceIncomplete) {
                        DeferredItemEditor(
                            descriptions = state.deferredItemDescriptions,
                            onAdd = viewModel::onDeferredItemAdd,
                            onRemove = viewModel::onDeferredItemRemove,
                        )
                    }
                    OutlinedTextField(
                        value = state.limitations,
                        onValueChange = viewModel::onLimitationsChange,
                        label = { Text("Limitations to airworthiness or operations") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (activity != null) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val authorized = BiometricSigningGate(activity).authorize(localSigner)
                                    authorized.fold(
                                        onSuccess = { signer -> viewModel.signNow(signer) },
                                        onFailure = { error -> viewModel.onSignAuthorizationFailed(error) },
                                    )
                                }
                            },
                            enabled = !state.generating && !state.signing,
                            colors = part66ConfirmButtonColors(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(painterResource(R.drawable.ic_esign), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(if (state.signing) "Signing…" else "Sign Digitally")
                        }
                    }
                    Button(
                        onClick = viewModel::generate,
                        enabled = !state.generating && !state.signing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(painterResource(R.drawable.ic_printsign), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(if (state.generating) "Generating…" else "Print and Sign")
                    }
                }
            }
        }
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Signing failed") },
            text = { Text(error.displayMessage()) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }
}

private fun CrsGenerationError.displayMessage(): String = when (this) {
    CrsGenerationError.EntryNotFound -> "This work entry no longer exists — it may have been deleted."
    CrsGenerationError.BiometricCancelled -> "Signing was cancelled."
    CrsGenerationError.BiometricUnavailable -> "No usable biometric is set up on this device — enrol a fingerprint or face in your device settings to sign locally."
    CrsGenerationError.KeyInvalidated -> "The signing key was invalidated, likely by a change to your device's enrolled biometrics. Signing again will generate a new key."
    CrsGenerationError.StrongBoxUnavailable -> "This device has no dedicated secure hardware; the signing key is protected by the device's trusted execution environment instead."
    is CrsGenerationError.Other -> message
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
        // Only the print-and-wet-sign path produces a paper original that needs a photographed
        // record — an eSignature CRS is already the signed document, nothing further to attach.
        if (crs.signatureState == SignatureState.ISSUED_UNSIGNED_PRINT) {
            val signedPhotoPath = crs.signedPhotoLocalPath
            if (signedPhotoPath != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { openSignedPhoto(context, signedPhotoPath) }) {
                        Icon(painterResource(R.drawable.ic_photo), contentDescription = "Show signed copy")
                    }
                    Text(
                        "Show signed copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { showRemovePhotoConfirm = true }) { Text("✕") }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { photoPickerLauncher.launch("image/*") }) {
                        Icon(painterResource(R.drawable.ic_camera), contentDescription = "Attach signed copy")
                    }
                    Text(
                        "Attach signed copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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

/** Queued up here, raised against the certificate's own id once it's actually issued (§5.7) — a deferred item always cites a real CRS. */
@Composable
private fun DeferredItemEditor(
    descriptions: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (Int) -> Unit,
) {
    var description by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Deferred items", style = MaterialTheme.typography.labelLarge)
        if (descriptions.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(descriptions.size) { index ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(descriptions[index]) },
                        trailingIcon = {
                            IconButton(onClick = { onRemove(index) }) { Text("✕") }
                        },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Add a deferred item") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onAdd(description); description = "" }, enabled = description.isNotBlank()) {
                Text("Add")
            }
        }
    }
}
