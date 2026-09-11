package nl.part66l.logbook.ui.aircraft

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.Structure
import nl.part66l.logbook.domain.Subcategory
import nl.part66l.logbook.ui.components.DatePickerField
import nl.part66l.logbook.ui.components.RadioGroupField
import nl.part66l.logbook.ui.components.UnsavedChangesDialog
import nl.part66l.logbook.ui.theme.Part66ConfirmGreen
import nl.part66l.logbook.ui.theme.part66TopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftFormScreen(
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onClose: () -> Unit,
    viewModel: AircraftFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onSaved() }
    }
    LaunchedEffect(Unit) {
        viewModel.deleted.collect { onDeleted() }
    }

    BackHandler(enabled = viewModel.isDirty()) { showUnsavedDialog = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit aircraft" else "Add aircraft") },
                navigationIcon = {
                    IconButton(onClick = { if (viewModel.isDirty()) showUnsavedDialog = true else onClose() }) {
                        Text("✕")
                    }
                },
                colors = part66TopAppBarColors(),
                expandedHeight = 48.dp,
            )
        },
    ) { padding ->
        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator(modifier = Modifier.padding(top = 32.dp)) }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.archived) {
                Text(
                    "This aircraft is archived — it's hidden from the list unless \"Show archived aircraft\" is on in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = state.manufacturer,
                onValueChange = viewModel::onManufacturerChange,
                label = { Text("Manufacturer") },
                isError = state.manufacturerError != null,
                supportingText = { state.manufacturerError?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.type,
                onValueChange = viewModel::onTypeChange,
                label = { Text("Type") },
                isError = state.typeError != null,
                supportingText = { state.typeError?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            RadioGroupField(
                label = "Sailplane type",
                value = state.propulsion,
                options = Propulsion.entries,
                optionLabel = { it.displayLabel },
                onValueChange = viewModel::onPropulsionChange,
            )
            RadioGroupField(
                label = "Structure",
                // Mixed construction is real but rare enough to hide from new entries for
                // now — it still renders below if an aircraft already has it (e.g. imported).
                options = Structure.entries.filter { it != Structure.MIXED || it == state.structure },
                value = state.structure,
                optionLabel = { it.displayLabel },
                onValueChange = viewModel::onStructureChange,
            )
            if (state.structure == Structure.MIXED) {
                RadioGroupField(
                    label = "Subcategory (mixed construction cannot be derived automatically)",
                    value = state.subcategoryOverride,
                    options = Subcategory.entries,
                    optionLabel = { it.name },
                    onValueChange = viewModel::onSubcategoryOverrideChange,
                )
                state.subcategoryOverrideError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedTextField(
                value = state.registration,
                onValueChange = viewModel::onRegistrationChange,
                label = { Text("Registration") },
                isError = state.registrationError != null,
                supportingText = { state.registrationError?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.serialNumber,
                onValueChange = viewModel::onSerialNumberChange,
                label = { Text("Serial number") },
                isError = state.serialNumberError != null,
                supportingText = { state.serialNumberError?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DatePickerField(
                label = if (state.isEditing) "New registration valid from" else "Registration valid from",
                value = state.validFrom,
                onValueChange = viewModel::onValidFromChange,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                colors = ButtonDefaults.buttonColors(containerColor = Part66ConfirmGreen),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.saving) "Saving…" else "Save")
            }

            if (state.isEditing) {
                OutlinedButton(
                    onClick = viewModel::onArchiveToggle,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.archived) "Unarchive" else "Archive")
                }

                if (state.hasWorkHistory) {
                    Text(
                        "Can't be deleted — it has work entries on record. Archive it instead to hide it from the list.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = state.canDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.deleting) "Deleting…" else "Delete")
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this aircraft?") },
            text = { Text("This permanently removes ${state.manufacturer} ${state.type} (${state.registration}). This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            onSave = { showUnsavedDialog = false; viewModel.save() },
            onDiscard = { showUnsavedDialog = false; onClose() },
            onCancel = { showUnsavedDialog = false },
        )
    }
}

private val Propulsion.displayLabel: String
    get() = when (this) {
        Propulsion.UNPOWERED -> "Pure sailplane"
        Propulsion.POWERED_SAILPLANE -> "Turbo / Self-launch / TMG"
        Propulsion.ELA1 -> "ELA1"
    }

private val Structure.displayLabel: String
    get() = when (this) {
        Structure.COMPOSITE -> "Composite"
        Structure.METAL -> "Metal"
        Structure.WOOD_AND_FABRIC -> "Wood & Fabric"
        Structure.MIXED -> "Mixed"
    }
