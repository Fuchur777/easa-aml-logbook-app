package nl.part66l.logbook.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.part66l.logbook.domain.Subcategory

/**
 * Shared by both the first-run gate (Destination.ProfileSetup) and later edits
 * (Destination.Profile) — same form, same ViewModel type, different caller-side
 * navigation once [onSaved] fires.
 *
 * Not yet built: date pickers for licence expiry and the recency-reduction
 * date. Both stay null until entered, which is a valid, harmless default —
 * left out of this pass to keep the first form's scope reasonable rather than
 * because they don't matter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileFormScreen(
    onSaved: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onSaved() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Profile") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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

            Text("Held subcategories", style = MaterialTheme.typography.labelLarge)
            SubcategoryCheckbox("L1", state.holdsL1) { viewModel.onSubcategoryToggle(Subcategory.L1, it) }
            SubcategoryCheckbox("L1C", state.holdsL1C) { viewModel.onSubcategoryToggle(Subcategory.L1C, it) }
            SubcategoryCheckbox("L2", state.holdsL2) { viewModel.onSubcategoryToggle(Subcategory.L2, it) }
            SubcategoryCheckbox("L2C", state.holdsL2C) { viewModel.onSubcategoryToggle(Subcategory.L2C, it) }
            state.subcategoryError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.recencyReductionGranted, onCheckedChange = viewModel::onRecencyReductionGrantedChange)
                Text("50% recency reduction granted by my competent authority")
            }
            if (state.recencyReductionGranted) {
                OutlinedTextField(
                    value = state.recencyReductionAuthority,
                    onValueChange = viewModel::onRecencyReductionAuthorityChange,
                    label = { Text("Authority") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.recencyReductionReference,
                    onValueChange = viewModel::onRecencyReductionReferenceChange,
                    label = { Text("Reference") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.saving) "Saving…" else "Save")
            }
        }
    }
}

@Composable
private fun SubcategoryCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}
