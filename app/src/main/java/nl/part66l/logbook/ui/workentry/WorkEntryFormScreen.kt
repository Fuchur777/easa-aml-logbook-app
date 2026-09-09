package nl.part66l.logbook.ui.workentry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import nl.part66l.logbook.data.DeferredItemEntity
import nl.part66l.logbook.data.DocumentEntity
import nl.part66l.logbook.data.DocumentationRefInput
import nl.part66l.logbook.data.PartUsedInput
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.DocumentCategory
import nl.part66l.logbook.domain.EntryRole
import nl.part66l.logbook.ui.components.DatePickerField
import nl.part66l.logbook.ui.components.DropdownField
import nl.part66l.logbook.ui.components.PersonAutocompleteField
import nl.part66l.logbook.ui.components.PersonPickerField
import nl.part66l.logbook.ui.components.UnsavedChangesDialog
import nl.part66l.logbook.ui.documents.displayLabel
import nl.part66l.logbook.ui.theme.Part66ConfirmGreen
import nl.part66l.logbook.ui.theme.part66TopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkEntryFormScreen(
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onClose: () -> Unit,
    /** Only ever invoked when [WorkEntryFormState.isEditing] — a new, unsaved entry has no id to view certificates for. */
    onCertificates: (String) -> Unit = {},
    viewModel: WorkEntryFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val aircraftOptions by viewModel.aircraftOptions.collectAsStateWithLifecycle()
    val knownHelperNames by viewModel.knownHelperNames.collectAsStateWithLifecycle()
    val knownHelperLicenceNumbers by viewModel.knownHelperLicenceNumbers.collectAsStateWithLifecycle()
    val availableTasks by viewModel.availableTasks.collectAsStateWithLifecycle()
    val catalogueSectionOrder by viewModel.catalogueSectionOrder.collectAsStateWithLifecycle()
    val collapsedCatalogueSections by viewModel.collapsedCatalogueSections.collectAsStateWithLifecycle()
    val documentOptions by viewModel.documentOptions.collectAsStateWithLifecycle()
    val openDeferredItems by viewModel.openDeferredItems.collectAsStateWithLifecycle()
    var showUnsavedDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showTaskPicker by remember { mutableStateOf(false) }
    var workorderExpanded by remember { mutableStateOf(true) }

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
                title = { Text(if (state.isEditing) "Edit work entry" else "Add work entry") },
                navigationIcon = {
                    IconButton(onClick = { if (viewModel.isDirty()) showUnsavedDialog = true else onClose() }) {
                        Text("✕")
                    }
                },
                colors = part66TopAppBarColors(),
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { workorderExpanded = !workorderExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (workorderExpanded) "▼" else "▶", modifier = Modifier.padding(end = 8.dp))
                        Text("Workorder", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    }
                    if (workorderExpanded) {
                        PersonAutocompleteField(
                            label = "Issuer name",
                            knownNames = knownHelperNames,
                            value = state.workorderIssuerName,
                            onValueChange = viewModel::onWorkorderIssuerNameChange,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        DatePickerField(
                            label = "Workorder date",
                            value = state.workorderDate,
                            onValueChange = viewModel::onWorkorderDateChange,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.workorderRequestedWork,
                            onValueChange = viewModel::onWorkorderRequestedWorkChange,
                            label = { Text("Requested work") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.workorderReference,
                            onValueChange = viewModel::onWorkorderReferenceChange,
                            label = { Text("Workorder reference") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Aircraft", style = MaterialTheme.typography.titleMedium)
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

                    // Bench/component work has no airframe to read hours/launches from — N/A on
                    // the eventual CRS/workorder printout rather than these fields at all.
                    if (state.aircraftSelection is AircraftSelection.Specific) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = state.airframeHours,
                                onValueChange = viewModel::onAirframeHoursChange,
                                label = { Text("Airframe hours") },
                                isError = state.airframeHoursError != null,
                                supportingText = { state.airframeHoursError?.let { Text(it) } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = state.launches,
                                onValueChange = viewModel::onLaunchesChange,
                                label = { Text("Launches") },
                                isError = state.launchesError != null,
                                supportingText = { state.launchesError?.let { Text(it) } },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Work details", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = viewModel::onDescriptionChange,
                        label = { Text("Description of work done") },
                        isError = state.descriptionError != null,
                        supportingText = { state.descriptionError?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SessionDatesEditor(
                        dates = state.sessionDates,
                        onAdd = viewModel::onSessionDateAdd,
                        onRemove = viewModel::onSessionDateRemove,
                        daysWorkedOverride = state.daysWorkedOverride,
                        onDaysWorkedOverrideChange = viewModel::onDaysWorkedOverrideChange,
                        daysWorkedOverrideError = state.daysWorkedOverrideError,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Annual inspection", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.annualInspection, onCheckedChange = viewModel::onAnnualInspectionChange)
                        Text("Annual inspection performed")
                    }
                    if (state.annualInspection) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = state.concurrentWithArc, onCheckedChange = viewModel::onConcurrentWithArcChange)
                            Text("Concurrent with an airworthiness review")
                        }
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    DocumentationRefEditor(
                        refs = state.documentationRefs,
                        documentOptions = documentOptions,
                        onAdd = viewModel::onDocumentationRefAdd,
                        onRemove = viewModel::onDocumentationRefRemove,
                        onCreateDocument = viewModel::onCreateDocument,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    PartUsedEditor(
                        parts = state.partsUsed,
                        onAdd = viewModel::onPartUsedAdd,
                        onRemove = viewModel::onPartUsedRemove,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (openDeferredItems.isNotEmpty()) {
                Card {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Deferred items", style = MaterialTheme.typography.titleMedium)
                        DropdownField(
                            label = "Closes deferred item (optional)",
                            value = openDeferredItems.find { it.id == state.closesDeferredItemId },
                            options = listOf<DeferredItemEntity?>(null) + openDeferredItems,
                            optionLabel = { it?.description ?: "None" },
                            onValueChange = { viewModel.onClosesDeferredItemChange(it?.id) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Appendix II tasks (Route B)", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { showTaskPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (state.completedTaskIds.isEmpty()) "Select tasks completed"
                            else "${state.completedTaskIds.size} task(s) completed — tap to change",
                        )
                    }
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Activity", style = MaterialTheme.typography.titleMedium)
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
                }
            }

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sign-off", style = MaterialTheme.typography.titleMedium)
                    DropdownField(
                        label = "Sign-off",
                        value = state.role,
                        options = EntryRole.entries.filter { it != EntryRole.SUPERVISED_ANOTHER },
                        optionLabel = { it.displayLabel },
                        onValueChange = viewModel::onRoleChange,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = state.supervisedAnother, onCheckedChange = viewModel::onSupervisedAnotherChange)
                        Text("Assisted by")
                    }
                    if (state.supervisedAnother) {
                        PersonPickerField(
                            knownNames = knownHelperNames,
                            knownLicenceNumbers = knownHelperLicenceNumbers,
                            selectedNames = state.helperNames,
                            onAdd = viewModel::onHelperAdd,
                            onRemove = viewModel::onHelperRemove,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
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

            if (state.isEditing) {
                OutlinedButton(
                    onClick = { state.entryId?.let(onCertificates) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Certificates")
                }

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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this work entry?") },
            text = { Text("This permanently removes the entry and everything on it — helpers, documentation, parts, task completions. This cannot be undone.") },
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

private val SESSION_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy")

/** One job can span several days — each date is its own removable chip, same shape as the documentation/parts editors below. */
@Composable
private fun SessionDatesEditor(
    dates: List<LocalDate>,
    onAdd: (LocalDate) -> Unit,
    onRemove: (LocalDate) -> Unit,
    daysWorkedOverride: String,
    onDaysWorkedOverrideChange: (String) -> Unit,
    daysWorkedOverrideError: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Dates worked", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(dates.sorted()) { date ->
                InputChip(
                    selected = false,
                    onClick = {},
                    label = { Text(date.format(SESSION_DATE_FORMAT)) },
                    trailingIcon = if (dates.size > 1) {
                        { IconButton(onClick = { onRemove(date) }) { Text("✕") } }
                    } else {
                        null
                    },
                )
            }
        }
        DatePickerField(
            label = "Add another day",
            value = null,
            onValueChange = { it?.let(onAdd) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = daysWorkedOverride,
            onValueChange = onDaysWorkedOverrideChange,
            label = { Text("Days worked") },
            placeholder = { Text("Calculated: ${dates.distinct().size}") },
            isError = daysWorkedOverrideError != null,
            supportingText = {
                Text(daysWorkedOverrideError ?: "Leave blank to use the number of distinct dates above")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** "Name (Revision) - Category" — display label for the dropdown and the picked-refs list; the CRS itself renders the fields separately. */
private val DocumentEntity.pickerLabel: String
    get() = "$name${revision?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""} - ${category.displayLabel}"

private val DocumentationRefInput.displayLabel: String
    get() = "$reference${revision?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}${category?.let { " - ${it.displayLabel}" } ?: ""}"

@Composable
private fun DocumentationRefEditor(
    refs: List<DocumentationRefInput>,
    documentOptions: List<DocumentEntity>,
    onAdd: (DocumentationRefInput) -> Unit,
    onRemove: (Int) -> Unit,
    onCreateDocument: (name: String, category: DocumentCategory, revision: String?, revisionDate: LocalDate?, link: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNewDocument by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Documentation used", style = MaterialTheme.typography.titleMedium)
        refs.forEachIndexed { index, ref ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ref.displayLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemove(index) }) { Text("✕") }
            }
        }
        // Picking a document adds it straight away — no separate confirm step.
        DropdownField(
            label = "Document",
            value = null,
            options = documentOptions,
            optionLabel = { it.pickerLabel },
            onValueChange = { document ->
                onAdd(DocumentationRefInput(document.name, document.revision, document.revisionDate, document.category))
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (documentOptions.isEmpty()) {
            Text(
                "No documents in the directory yet — add one below, or from the hamburger menu's Documents screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TextButton(onClick = { showNewDocument = !showNewDocument }, modifier = Modifier.fillMaxWidth()) {
            Text(if (showNewDocument) "Cancel new document" else "+ New document")
        }
        if (showNewDocument) {
            NewDocumentFields(
                onCreate = { name, category, revision, revisionDate, link ->
                    onCreateDocument(name, category, revision, revisionDate, link)
                    showNewDocument = false
                },
            )
        }
    }
}

@Composable
private fun NewDocumentFields(
    onCreate: (name: String, category: DocumentCategory, revision: String?, revisionDate: LocalDate?, link: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(DocumentCategory.MANUAL) }
    var revision by remember { mutableStateOf("") }
    var revisionDate by remember { mutableStateOf<LocalDate?>(null) }
    var link by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownField(
            label = "Category",
            value = category,
            options = DocumentCategory.entries,
            optionLabel = { it.displayLabel },
            onValueChange = { category = it },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = revision,
            onValueChange = { revision = it },
            label = { Text("Revision") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        DatePickerField(
            label = "Revision date",
            value = revisionDate,
            onValueChange = { revisionDate = it },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            label = { Text("Link (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = {
                if (name.isNotBlank()) {
                    onCreate(name.trim(), category, revision.trim().ifBlank { null }, revisionDate, link.trim().ifBlank { null })
                }
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Create document")
        }
    }
}

@Composable
private fun PartUsedEditor(
    parts: List<PartUsedInput>,
    onAdd: (PartUsedInput) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var description by remember { mutableStateOf("") }
    var partNumber by remember { mutableStateOf("") }
    var batchOrSerial by remember { mutableStateOf("") }
    var formOneRef by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Parts and materials used", style = MaterialTheme.typography.titleMedium)
        parts.forEachIndexed { index, part ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val details = listOfNotNull(
                    part.description?.takeIf { it.isNotBlank() },
                    part.batchOrSerial?.takeIf { it.isNotBlank() }?.let { "batch/serial $it" },
                    part.formOneRef?.takeIf { it.isNotBlank() }?.let { "Form 1 $it" },
                    part.quantity?.takeIf { it.isNotBlank() }?.let { "qty $it" },
                ).joinToString(", ")
                Text(
                    part.partNumber + if (details.isNotEmpty()) " ($details)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemove(index) }) { Text("✕") }
            }
        }
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = partNumber,
                onValueChange = { partNumber = it },
                label = { Text("Part number") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = batchOrSerial,
                onValueChange = { batchOrSerial = it },
                label = { Text("Batch / serial") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = formOneRef,
                onValueChange = { formOneRef = it },
                label = { Text("EASA Form 1 ref") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it },
                label = { Text("Quantity") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedButton(
            onClick = {
                if (partNumber.isNotBlank()) {
                    onAdd(
                        PartUsedInput(
                            partNumber = partNumber.trim(),
                            description = description.trim().ifBlank { null },
                            batchOrSerial = batchOrSerial.trim().ifBlank { null },
                            formOneRef = formOneRef.trim().ifBlank { null },
                            quantity = quantity.trim().ifBlank { null },
                        ),
                    )
                    description = ""
                    partNumber = ""
                    batchOrSerial = ""
                    formOneRef = ""
                    quantity = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("+ Add part")
        }
    }
}
