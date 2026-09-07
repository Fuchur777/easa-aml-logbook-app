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
 * when nothing matches — the caller resolves names to directory entries on save, not here.
 */
@Composable
fun PersonPickerField(
    knownNames: List<String>,
    selectedNames: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Null renders no heading — for placements (e.g. under a checkbox) where the surrounding context already labels the field. */
    label: String? = null,
) {
    var query by remember { mutableStateOf("") }

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
                    Text(
                        name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAdd(name); query = "" }
                            .padding(vertical = 10.dp),
                    )
                }
                if (!exactMatch) {
                    Text(
                        "+ Add \"$trimmed\" as new",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAdd(trimmed); query = "" }
                            .padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}
