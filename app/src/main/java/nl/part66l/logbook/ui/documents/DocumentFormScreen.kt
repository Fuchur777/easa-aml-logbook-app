package nl.part66l.logbook.ui.documents

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import nl.part66l.logbook.R
import nl.part66l.logbook.domain.DocumentCategory
import nl.part66l.logbook.ui.components.DatePickerField
import nl.part66l.logbook.ui.components.DropdownField
import nl.part66l.logbook.ui.components.UnsavedChangesDialog
import nl.part66l.logbook.ui.theme.part66ConfirmButtonColors
import nl.part66l.logbook.ui.theme.part66TopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentFormScreen(
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onClose: () -> Unit,
    viewModel: DocumentFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val pdfPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                copyPdfToInternalStorage(context, uri)?.let { (path, fileName) -> viewModel.onPdfAttached(path, fileName) }
            }
        }
    }

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
                title = { Text(if (state.isEditing) "Edit document" else "Add document") },
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
                    "This document is archived — it's hidden from the entry-form picker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Name") },
                isError = state.nameError != null,
                supportingText = { state.nameError?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DropdownField(
                label = "Category",
                value = state.category,
                options = DocumentCategory.entries,
                optionLabel = { it.displayLabel },
                onValueChange = viewModel::onCategoryChange,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.revision,
                onValueChange = viewModel::onRevisionChange,
                label = { Text("Revision") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            DatePickerField(
                label = "Revision date",
                value = state.revisionDate,
                onValueChange = viewModel::onRevisionDateChange,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.link,
                onValueChange = viewModel::onLinkChange,
                label = { Text("Link (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PDF attachment", style = MaterialTheme.typography.labelLarge)
                if (state.pdfFileName.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painterResource(R.drawable.ic_pdf),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            state.pdfFileName,
                            style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { openPdf(context, state.pdfPath) },
                        )
                        TextButton(onClick = viewModel::onPdfRemoved) { Text("Remove") }
                    }
                }
                OutlinedButton(
                    onClick = { pdfPickerLauncher.launch("application/pdf") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.pdfFileName.isNotBlank()) "Replace PDF" else "Attach PDF")
                }
            }

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                colors = part66ConfirmButtonColors(),
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
            title = { Text("Delete this document?") },
            text = { Text("This permanently removes \"${state.name}\" from the directory. Past entries that used it keep their own snapshot text — this cannot be undone.") },
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
