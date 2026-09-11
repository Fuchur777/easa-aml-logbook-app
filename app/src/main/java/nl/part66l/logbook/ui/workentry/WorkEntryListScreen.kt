package nl.part66l.logbook.ui.workentry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import java.time.format.DateTimeFormatter
import nl.part66l.logbook.data.WorkEntryListRow
import nl.part66l.logbook.ui.components.DropdownField
import nl.part66l.logbook.ui.theme.part66TopAppBarColors

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkEntryListScreen(
    onAddEntry: () -> Unit,
    onEditEntry: (String) -> Unit,
    viewModel: WorkEntryListViewModel = hiltViewModel(),
) {
    val entries = viewModel.entries.collectAsLazyPagingItems()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val aircraftOptions by viewModel.aircraftOptions.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Work entries") },
                colors = part66TopAppBarColors(),
                expandedHeight = 48.dp,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddEntry) { Text("+") }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DropdownField(
                    label = "Aircraft",
                    value = filter.aircraftId,
                    options = listOf<String?>(null) + aircraftOptions.map { it.aircraft.id },
                    optionLabel = { id ->
                        id?.let { aid ->
                            aircraftOptions.find { it.aircraft.id == aid }
                                ?.let { it.registration ?: "${it.aircraft.manufacturer} ${it.aircraft.type}" }
                        } ?: "All aircraft"
                    },
                    onValueChange = viewModel::onAircraftFilterChange,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::onSortToggle) {
                    Text(if (filter.ascending) "Oldest first ▲" else "Newest first ▼")
                }
            }
            HorizontalDivider()
            if (entries.itemCount == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No work entries yet — tap + to add one.")
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(count = entries.itemCount, key = entries.itemKey { it.entry.id }) { index ->
                        val row = entries[index]
                        if (row != null) {
                            Column {
                                WorkEntryRow(
                                    row = row,
                                    backgroundColor = if (index % 2 == 0) {
                                        MaterialTheme.colorScheme.surface
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    },
                                    onClick = { onEditEntry(row.entry.id) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkEntryRow(row: WorkEntryListRow, backgroundColor: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            (row.workDate?.format(DATE_FORMAT) ?: "No date") + " · " + (row.aircraftRegistration ?: "Bench / component work"),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(row.entry.description, style = MaterialTheme.typography.bodyMedium)
        val activity = row.activityTypes.joinToString(", ") { it.displayLabel }.ifEmpty { "No activity recorded" }
        Text(
            activity + if (row.entry.supervisedAnother) " · Assisted by another" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
