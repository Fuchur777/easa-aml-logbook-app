package nl.part66l.logbook.ui.crs

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.part66l.logbook.R
import nl.part66l.logbook.data.IssuedCrsRow
import nl.part66l.logbook.ui.components.DropdownField
import nl.part66l.logbook.ui.documents.openPdf
import nl.part66l.logbook.ui.theme.part66TopAppBarColors

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun IssuedCrsListScreen(
    onClose: () -> Unit,
    viewModel: IssuedCrsListViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val aircraftOptions by viewModel.aircraftOptions.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val allSelected by viewModel.allSelected.collectAsStateWithLifecycle()
    val selectionMode = selectedIds.isNotEmpty()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            if (selectionMode) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    navigationIcon = { IconButton(onClick = viewModel::onClearSelection) { Text("✕") } },
                    actions = {
                        TextButton(onClick = viewModel::onSelectAllToggle) {
                            Text(if (allSelected) "Unselect all" else "Select all", color = Color.White)
                        }
                        IconButton(
                            onClick = { viewModel.exportSelected { zip -> shareCrsZip(context, zip) } },
                            enabled = !exporting,
                        ) {
                            if (exporting) {
                                Text("…", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            } else {
                                Icon(
                                    painterResource(R.drawable.ic_download),
                                    contentDescription = "Export the selected certificates",
                                    tint = Color.White,
                                )
                            }
                        }
                    },
                    colors = part66TopAppBarColors(),
                    expandedHeight = 48.dp,
                )
            } else {
                TopAppBar(
                    title = { Text("CRS Library") },
                    navigationIcon = { IconButton(onClick = onClose) { Text("✕") } },
                    colors = part66TopAppBarColors(),
                    expandedHeight = 48.dp,
                )
            }
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
                OutlinedTextField(
                    value = filter.numberQuery,
                    onValueChange = viewModel::onNumberQueryChange,
                    label = { Text("CRS name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = filter.helperQuery,
                    onValueChange = viewModel::onHelperQueryChange,
                    label = { Text("Assisted by") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            HorizontalDivider()
            if (rows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                    Text("No issued certificates match this filter.", textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(rows, key = { it.crs.id }) { row ->
                        val selected = row.crs.id in selectedIds
                        IssuedCrsRowView(
                            row = row,
                            selectionMode = selectionMode,
                            selected = selected,
                            backgroundColor = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            onClick = {
                                if (selectionMode) {
                                    viewModel.onRowToggleSelected(row.crs.id)
                                } else {
                                    row.crs.pdfLocalPath?.let { openPdf(context, it) }
                                }
                            },
                            onLongClick = { viewModel.onRowLongPress(row.crs.id) },
                            onOpenSignedPhoto = { row.crs.signedPhotoLocalPath?.let { openSignedPhoto(context, it) } },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IssuedCrsRowView(
    row: IssuedCrsRow,
    selectionMode: Boolean,
    selected: Boolean,
    backgroundColor: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpenSignedPhoto: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                enabled = selectionMode || row.crs.pdfLocalPath != null,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(row.crs.number, style = MaterialTheme.typography.titleSmall)
            Text(
                "${row.crs.completionDate.format(DATE_FORMAT)} · ${row.aircraftRegistration ?: "Bench / component work"} · ${row.crs.signatureState.displayLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (row.helperNames.isNotEmpty()) {
                Text(
                    "Assisted by " + row.helperNames.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val signedPhotoPath = row.crs.signedPhotoLocalPath
        if (signedPhotoPath != null) {
            SignedPhotoThumbnail(
                path = signedPhotoPath,
                // In selection mode, a tap on the thumbnail must fall through to the row's own
                // combinedClickable (toggling selection) rather than opening the photo — it gets
                // no click handler of its own while selection mode is active.
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .let { if (selectionMode) it else it.combinedClickable(onClick = onOpenSignedPhoto, onLongClick = onLongClick) },
            )
        }
    }
}

/** Decoded off the main thread, at a coarse downsample — a signed-copy photo is a full-resolution camera shot, not a 48dp thumbnail. */
@Composable
private fun SignedPhotoThumbnail(path: String, modifier: Modifier = Modifier) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            runCatching { BitmapFactory.decodeFile(path, options)?.asImageBitmap() }.getOrNull()
        }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(bitmap = loaded, contentDescription = "Signed copy", contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
