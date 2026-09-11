package nl.part66l.logbook.ui.workentry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import nl.part66l.logbook.data.CatalogueTaskEntity
import nl.part66l.logbook.ui.theme.part66TopAppBarColors

/**
 * Full-screen rather than a small AlertDialog — the applicable catalogue can run to
 * ~100 tasks, grouped by section, with a search field to actually find one.
 *
 * Section order and fold state are [sectionOrder]/[collapsedSections] — owned by the
 * caller (persisted in Settings) so they survive across entries. While [query] is
 * non-blank, folding and reordering are suspended: every matching section is shown
 * expanded so a search never hides a result behind a closed section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCompletionPickerDialog(
    tasks: List<CatalogueTaskEntity>,
    selectedIds: Set<String>,
    sectionOrder: List<String>,
    collapsedSections: Set<String>,
    onToggle: (String) -> Unit,
    onSectionReorder: (List<String>) -> Unit,
    onSectionFoldToggle: (String) -> Unit,
    onDone: () -> Unit,
) {
    var query by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Tasks completed (${selectedIds.size})") },
                    actions = { TextButton(onClick = onDone) { Text("Done", color = Color.White) } },
                    colors = part66TopAppBarColors(),
                    expandedHeight = 48.dp,
                )
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search tasks or reference") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )

                val searching = query.isNotBlank()
                val filtered = if (!searching) {
                    tasks
                } else {
                    tasks.filter { it.text.contains(query, ignoreCase = true) || it.reference.contains(query, ignoreCase = true) }
                }
                val bySection = filtered.groupBy { it.section }
                val orderedSections = remember(sectionOrder, bySection.keys) {
                    val known = sectionOrder.filter { it in bySection.keys }
                    val missing = bySection.keys.filterNot { it in sectionOrder }.sorted()
                    known + missing
                }

                if (filtered.isEmpty()) {
                    Text(
                        if (tasks.isEmpty()) "No applicable tasks — check a catalogue is seeded and subcategories are held in Profile." else "No tasks match \"$query\".",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        orderedSections.forEachIndexed { index, section ->
                            val sectionTasks = bySection[section].orEmpty()
                            val collapsed = !searching && section in collapsedSections
                            item(key = "header-$section") {
                                SectionHeader(
                                    section = section,
                                    reference = sectionTasks.firstOrNull()?.reference.orEmpty(),
                                    completedCount = sectionTasks.count { it.id in selectedIds },
                                    totalCount = sectionTasks.size,
                                    collapsed = collapsed,
                                    showControls = !searching,
                                    canMoveUp = index > 0,
                                    canMoveDown = index < orderedSections.lastIndex,
                                    backgroundColor = if (index % 2 == 0) {
                                        MaterialTheme.colorScheme.surface
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    },
                                    onFoldToggle = { onSectionFoldToggle(section) },
                                    onMoveUp = {
                                        onSectionReorder(orderedSections.toMutableList().apply { add(index - 1, removeAt(index)) })
                                    },
                                    onMoveDown = {
                                        onSectionReorder(orderedSections.toMutableList().apply { add(index + 1, removeAt(index)) })
                                    },
                                )
                            }
                            if (!collapsed) {
                                items(sectionTasks, key = { it.id }) { task ->
                                    TaskRow(task = task, selected = task.id in selectedIds, onToggle = { onToggle(task.id) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    section: String,
    reference: String,
    completedCount: Int,
    totalCount: Int,
    collapsed: Boolean,
    showControls: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    backgroundColor: Color,
    onFoldToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(enabled = showControls, onClick = onFoldToggle)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (showControls && collapsed) "▶" else "▼",
            modifier = Modifier.padding(horizontal = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "$section ($completedCount/$totalCount)",
                style = MaterialTheme.typography.titleSmall,
            )
            if (reference.isNotBlank()) {
                Text(
                    reference,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        if (showControls) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp) { Text("↑") }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) { Text("↓") }
        }
    }
}

@Composable
private fun TaskRow(task: CatalogueTaskEntity, selected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Text(task.text, style = MaterialTheme.typography.bodyMedium)
    }
}
