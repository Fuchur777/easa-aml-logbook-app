package nl.part66l.logbook.ui.aircraft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.Structure
import nl.part66l.logbook.domain.Subcategory
import nl.part66l.logbook.ui.components.DatePickerField
import nl.part66l.logbook.ui.components.RadioGroupField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftFormScreen(
    onSaved: () -> Unit,
    viewModel: AircraftFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onSaved() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Add aircraft") }) },
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
            OutlinedTextField(
                value = state.serialNumber,
                onValueChange = viewModel::onSerialNumberChange,
                label = { Text("Serial number") },
                isError = state.serialNumberError != null,
                supportingText = { state.serialNumberError?.let { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            RadioGroupField(
                label = "Propulsion",
                value = state.propulsion,
                options = Propulsion.entries,
                optionLabel = { it.name },
                onValueChange = viewModel::onPropulsionChange,
            )
            RadioGroupField(
                label = "Structure",
                value = state.structure,
                options = Structure.entries,
                optionLabel = { it.name },
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
            DatePickerField(
                label = "Registration valid from",
                value = state.validFrom,
                onValueChange = viewModel::onValidFromChange,
                modifier = Modifier.fillMaxWidth(),
            )

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
