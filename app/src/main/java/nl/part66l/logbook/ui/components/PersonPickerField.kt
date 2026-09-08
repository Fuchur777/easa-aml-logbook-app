package nl.part66l.logbook.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Search-or-add picker for helper names, backed by an existing name directory. Selected
 * names show as removable chips; typing filters the directory, with an "Add as new" row
 * when nothing matches. Picking a known name resolves immediately; adding a genuinely new
 * one first offers an optional licence number, same shape as the document picker's inline
 * "+ New document" flow — the caller resolves names (and any entered licence) to directory
 * entries on save, not here.
 */
@Composable
fun PersonPickerField(
    knownNames: List<String>,
    selectedNames: List<String>,
    onAdd: (name: String, licenceNumber: String?) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Null renders no heading — for placements (e.g. under a checkbox) where the surrounding context already labels the field. */
    label: String? = null,
    /** Name -> licence number, for known contacts — shown alongside a matched suggestion so a licence already on file is visible before picking. */
    knownLicenceNumbers: Map<String, String?> = emptyMap(),
) {
    var query by remember { mutableStateOf("") }
    var pendingNewName by remember { mutableStateOf<String?>(null) }
    var pendingLicence by remember { mutableStateOf("") }

    fun addExisting(name: String) {
        onAdd(name, null)
        query = ""
    }

    fun confirmNew() {
        val name = pendingNewName ?: return
        onAdd(name, pendingLicence.trim().ifBlank { null })
        pendingNewName = null
        pendingLicence = ""
        query = ""
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        label?.let { Text(it, style = MaterialTheme.typography.labelLarge) }

        if (selectedNames.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(selectedNames) { name ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(name) },
                        trailingIcon = {
                            IconButton(onClick = { onRemove(name) }) { Text("✕") }
                        },
                    )
                }
            }
        }

        if (pendingNewName != null) {
            Text("Add \"$pendingNewName\" as a new contact", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = pendingLicence,
                onValueChange = { pendingLicence = it },
                label = { Text("Licence number (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = ::confirmNew) { Text("Add") }
        } else {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search or add name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (query.isNotBlank()) {
                val trimmed = query.trim()
                val matches = knownNames.filter { it.contains(trimmed, ignoreCase = true) && it !in selectedNames }
                val exactMatch = knownNames.any { it.equals(trimmed, ignoreCase = true) }

                Column {
                    matches.forEach { name ->
                        val licence = knownLicenceNumbers[name]
                        Text(
                            if (licence.isNullOrBlank()) name else "$name ($licence)",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { addExisting(name) }
                                .padding(vertical = 10.dp),
                        )
                    }
                    if (!exactMatch) {
                        Text(
                            "+ Add \"$trimmed\" as new",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pendingNewName = trimmed }
                                .padding(vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
