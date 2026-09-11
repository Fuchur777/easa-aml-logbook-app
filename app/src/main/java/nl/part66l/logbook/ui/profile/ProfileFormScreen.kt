package nl.part66l.logbook.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import nl.part66l.logbook.domain.Subcategory
import nl.part66l.logbook.ui.components.DatePickerField
import nl.part66l.logbook.ui.components.UnsavedChangesDialog
import nl.part66l.logbook.ui.theme.part66ConfirmButtonColors
import nl.part66l.logbook.ui.theme.part66TopAppBarColors

/**
 * Shared by both the first-run gate (Destination.ProfileSetup) and later edits
 * (Destination.Profile) — same form, same ViewModel type, different caller-side
 * navigation once [onSaved] fires. [onClose] is null for the first-run gate —
 * there's nothing to go back to yet, so no X button or back interception there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileFormScreen(
    onSaved: () -> Unit,
    onClose: (() -> Unit)? = null,
    onRestore: (() -> Unit)? = null,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showUnsavedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onSaved() }
    }

    if (onClose != null) {
        BackHandler(enabled = viewModel.isDirty()) { showUnsavedDialog = true }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    if (onClose != null) {
                        IconButton(onClick = { if (viewModel.isDirty()) showUnsavedDialog = true else onClose() }) {
                            Text("✕")
                        }
                    }
                },
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
            if (onClose == null && onRestore != null) {
                TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                    Text("Setting up a new device? Restore from a Drive backup instead")
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Contact", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::onNameChange,
                        label = { Text("Name") },
                        isError = state.nameError != null,
                        supportingText = { state.nameError?.let { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.phoneNumber,
                        onValueChange = viewModel::onPhoneNumberChange,
                        label = { Text("Phone number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = viewModel::onEmailChange,
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Licence", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = state.licenceNumber,
                        onValueChange = viewModel::onLicenceNumberChange,
                        label = { Text("Licence number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.issuingAuthority,
                        onValueChange = viewModel::onIssuingAuthorityChange,
                        label = { Text("Issuing authority") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DatePickerField(
                        label = "Valid from",
                        value = state.licenceValidFrom,
                        onValueChange = viewModel::onLicenceValidFromChange,
                        isError = state.licenceDatesError != null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DatePickerField(
                        label = "Valid till",
                        value = state.licenceExpiry,
                        onValueChange = viewModel::onLicenceExpiryChange,
                        isError = state.licenceDatesError != null,
                        supportingText = state.licenceDatesError,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DatePickerField(
                        label = "Initial certification date",
                        value = state.initialCertificationDate,
                        onValueChange = viewModel::onInitialCertificationDateChange,
                        isError = state.initialCertificationDateError != null,
                        supportingText = state.initialCertificationDateError,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text("Held subcategories", style = MaterialTheme.typography.labelLarge)
                    SubcategoryCheckbox("L1", state.holdsL1) { viewModel.onSubcategoryToggle(Subcategory.L1, it) }
                    SubcategoryCheckbox("L1C", state.holdsL1C) { viewModel.onSubcategoryToggle(Subcategory.L1C, it) }
                    SubcategoryCheckbox("L2", state.holdsL2) { viewModel.onSubcategoryToggle(Subcategory.L2, it) }
                    SubcategoryCheckbox("L2C", state.holdsL2C) { viewModel.onSubcategoryToggle(Subcategory.L2C, it) }
                    state.subcategoryError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
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
        }
    }

    if (showUnsavedDialog && onClose != null) {
        UnsavedChangesDialog(
            onSave = { showUnsavedDialog = false; viewModel.save() },
            onDiscard = { showUnsavedDialog = false; onClose() },
            onCancel = { showUnsavedDialog = false },
        )
    }
}

@Composable
private fun SubcategoryCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}
