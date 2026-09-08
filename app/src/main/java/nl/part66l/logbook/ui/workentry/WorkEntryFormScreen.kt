package nl.part66l.logbook.ui.workentry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.EntryRole
import nl.part66l.logbook.ui.components.DatePickerField
import nl.part66l.logbook.ui.components.DropdownField
import nl.part66l.logbook.ui.components.PersonPickerField
import nl.part66l.logbook.ui.components.UnsavedChangesDialog
import nl.part66l.logbook.ui.theme.Part66ConfirmGreen
import nl.part66l.logbook.ui.theme.part66TopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkEntryFormScreen(
    onSaved: () -> Unit,
    onClose: () -> Unit,
    viewModel: WorkEntryFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val aircraftOptions by viewModel.aircraftOptions.collectAsStateWithLifecycle()
    val knownHelperNames by viewModel.knownHelperNames.collectAsStateWithLifecycle()
    val availableTasks by viewModel.availableTasks.collectAsStateWithLifecycle()
    val catalogueSectionOrder by viewModel.catalogueSectionOrder.collectAsStateWithLifecycle()
    val collapsedCatalogueSections by viewModel.collapsedCatalogueSections.collectAsStateWithLifecycle()
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showTaskPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onSaved() }
    }

    BackHandler(enabled = viewModel.isDirty()) { showUnsavedDialog = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add work entry") },
                navigationIcon = {
                    IconButton(onClick = { if (viewModel.isDirty()) showUnsavedDialog = true else onClose() }) {
                        Text("✕")
                    }
                },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DropdownField(
                    label = "Aircraft",
                    value = state.aircraftSelection,
                    options = listOf(AircraftSelection.Bench) + aircraftOptions.map { AircraftSelection.Specific(it.aircraft.id) },
                    optionLabel = { selection ->
                        when (selection) {
                            AircraftSelection.Unselected -> ""
                            AircraftSelection.Bench -> "Bench / component work (no aircraft)"
                            is AircraftSelection.Specific -> aircraftOptions.find { it.aircraft.id == selection.aircraftId }
                                ?.let { it.registration ?: "${it.aircraft.manufacturer} ${it.aircraft.type}" }
                                .orEmpty()
                        }
                    },
                    onValueChange = viewModel::onAircraftSelectionChange,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.aircraftSelectionError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Description of work done") },
                isError = state.descriptionError != null,
                supportingText = { state.descriptionError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Activity", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActivityType.entries.forEach { type ->
                        FilterChip(
                            selected = type in state.activityTypes,
                            onClick = { viewModel.onActivityTypeToggle(type) },
                            label = { Text(type.displayLabel) },
                        )
                    }
                }
                state.activityTypesError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.researchAndPaperwork, onCheckedChange = viewModel::onResearchAndPaperworkChange)
                    Text("Research & paperwork (not part of the regulatory activity list)")
                }
            }

            DropdownField(
                label = "Role",
                value = state.role,
                options = EntryRole.entries.filter { it != EntryRole.SUPERVISED_ANOTHER },
                optionLabel = { it.displayLabel },
                onValueChange = viewModel::onRoleChange,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.supervisedAnother, onCheckedChange = viewModel::onSupervisedAnotherChange)
                    Text("Supervised other persons")
                }
                if (state.supervisedAnother) {
                    PersonPickerField(
                        knownNames = knownHelperNames,
                        selectedNames = state.helperNames,
                        onAdd = viewModel::onHelperAdd,
                        onRemove = viewModel::onHelperRemove,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            DatePickerField(
                label = "Date",
                value = state.sessionDate,
                onValueChange = viewModel::onSessionDateChange,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Appendix II tasks (Route B)", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = { showTaskPicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (state.completedTaskIds.isEmpty()) "Select tasks completed"
                        else "${state.completedTaskIds.size} task(s) completed — tap to change",
                    )
                }
            }

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                colors = ButtonDefaults.buttonColors(containerColor = Part66ConfirmGreen),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.saving) "Saving…" else "Save")
            }
        }
    }

    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            onSave = { showUnsavedDialog = false; viewModel.save() },
            onDiscard = { showUnsavedDialog = false; onClose() },
            onCancel = { showUnsavedDialog = false },
        )
    }

    if (showTaskPicker) {
        TaskCompletionPickerDialog(
            tasks = availableTasks,
            selectedIds = state.completedTaskIds,
            sectionOrder = catalogueSectionOrder,
            collapsedSections = collapsedCatalogueSections,
            onToggle = viewModel::onTaskCompletionToggle,
            onSectionReorder = viewModel::onCatalogueSectionReorder,
            onSectionFoldToggle = viewModel::onCatalogueSectionFoldToggle,
            onDone = { showTaskPicker = false },
        )
    }
}
