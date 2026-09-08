package nl.part66l.logbook.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Single-value equivalent of [PersonPickerField] — the value typed *is* the field
 * (no separate query state), with a suggestion list from the same name directory
 * dropped in below while it's non-blank and doesn't already match a known name
 * exactly. The typed name (new or existing) is resolved into the person directory
 * by the caller at save time, same as [PersonPickerField]'s selections.
 */
@Composable
fun PersonAutocompleteField(
    label: String,
    knownNames: List<String>,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val trimmed = value.trim()
        if (trimmed.isNotEmpty() && !knownNames.any { it.equals(trimmed, ignoreCase = true) }) {
            val matches = knownNames.filter { it.contains(trimmed, ignoreCase = true) }
            matches.forEach { name ->
                Text(
                    name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onValueChange(name) }
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
}
