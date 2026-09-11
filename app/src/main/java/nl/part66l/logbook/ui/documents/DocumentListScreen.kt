package nl.part66l.logbook.ui.documents

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.part66l.logbook.R
import nl.part66l.logbook.data.DocumentEntity
import nl.part66l.logbook.domain.DocumentCategory
import nl.part66l.logbook.ui.components.DropdownField
import nl.part66l.logbook.ui.theme.part66TopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(
    onAddDocument: () -> Unit,
    onEditDocument: (String) -> Unit,
    onClose: () -> Unit,
    viewModel: DocumentListViewModel = hiltViewModel(),
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val aircraftOptions by viewModel.aircraftOptions.collectAsStateWithLifecycle()
    val category by viewModel.category.collectAsStateWithLifecycle()
    val aircraftId by viewModel.aircraftId.collectAsStateWithLifecycle()
    val showArchived by viewModel.showArchived.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Documents") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Text("✕") }
                },
                colors = part66TopAppBarColors(),
                expandedHeight = 48.dp,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDocument) { Text("+") }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DropdownField(
                        label = "Category",
                        value = category,
                        options = listOf<DocumentCategory?>(null) + DocumentCategory.entries,
                        optionLabel = { it?.displayLabel ?: "All categories" },
                        onValueChange = viewModel::onCategoryChange,
                        modifier = Modifier.weight(1f),
                    )
                    DropdownField(
                        label = "Used on",
                        value = aircraftId,
                        options = listOf<String?>(null) + aircraftOptions.map { it.aircraft.id },
                        optionLabel = { id ->
                            id?.let { aid ->
                                aircraftOptions.find { it.aircraft.id == aid }
                                    ?.let { it.registration ?: "${it.aircraft.manufacturer} ${it.aircraft.type}" }
                            } ?: "Any aircraft"
                        },
                        onValueChange = viewModel::onAircraftChange,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Show archived", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = showArchived, onCheckedChange = viewModel::onShowArchivedChange)
                }
            }

            if (documents.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (category == null && aircraftId == null) {
                            "No documents yet — tap + to add one."
                        } else {
                            "No documents match these filters."
                        },
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(documents, key = { _, document -> document.id }) { index, document ->
                        Column {
                            DocumentRow(
                                document = document,
                                backgroundColor = if (index % 2 == 0) {
                                    MaterialTheme.colorScheme.surface
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                },
                                onClick = { onEditDocument(document.id) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentRow(document: DocumentEntity, backgroundColor: Color, onClick: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(document.name, style = MaterialTheme.typography.titleMedium)
            Text(
                document.category.displayLabel +
                    (document.revision?.let { " · Rev $it" } ?: "") +
                    (if (document.archived) " · Archived" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val pdfPath = document.pdfPath
        if (pdfPath != null) {
            IconButton(onClick = { openPdf(context, pdfPath) }) {
                Icon(painterResource(R.drawable.ic_pdf), contentDescription = "Open PDF")
            }
        }
        document.link?.let { link ->
            IconButton(onClick = { uriHandler.openUri(link) }) {
                Icon(painterResource(R.drawable.ic_weblink), contentDescription = "Open link")
            }
        }
    }
}
