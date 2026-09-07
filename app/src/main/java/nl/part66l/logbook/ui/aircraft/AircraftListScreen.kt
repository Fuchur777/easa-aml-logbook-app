package nl.part66l.logbook.ui.aircraft

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.part66l.logbook.data.AircraftWithRegistration
import nl.part66l.logbook.ui.theme.part66TopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftListScreen(
    onAddAircraft: () -> Unit,
    onEditAircraft: (String) -> Unit,
    viewModel: AircraftListViewModel = hiltViewModel(),
) {
    val aircraft by viewModel.aircraft.collectAsStateWithLifecycle()

    // Optimistic local order while a drag is in progress; kept in sync with the
    // repository otherwise, and committed back to it once a drag ends.
    var items by remember { mutableStateOf(aircraft) }
    LaunchedEffect(aircraft) { items = aircraft }

    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var rowHeightPx by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aircraft") },
                colors = part66TopAppBarColors(),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAircraft) { Text("+") }
        },
    ) { padding ->
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No aircraft yet — tap + to add one.")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                itemsIndexed(items, key = { _, entry -> entry.aircraft.id }) { index, entry ->
                    val isDragging = entry.aircraft.id == draggingId
                    Column {
                        AircraftRow(
                            entry = entry,
                            backgroundColor = if (index % 2 == 0) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            modifier = Modifier
                                .graphicsLayer { translationY = if (isDragging) dragOffsetY else 0f }
                                .zIndex(if (isDragging) 1f else 0f)
                                .onGloballyPositioned { if (rowHeightPx == 0f) rowHeightPx = it.size.height.toFloat() }
                                .clickable(enabled = draggingId == null) { onEditAircraft(entry.aircraft.id) },
                            onDragStart = { draggingId = entry.aircraft.id; dragOffsetY = 0f },
                            onDrag = { deltaY ->
                                dragOffsetY += deltaY
                                val height = rowHeightPx
                                val currentIndex = items.indexOfFirst { it.aircraft.id == entry.aircraft.id }
                                if (height > 0f && dragOffsetY > height / 2 && currentIndex < items.lastIndex) {
                                    items = items.toMutableList().apply { add(currentIndex + 1, removeAt(currentIndex)) }
                                    dragOffsetY -= height
                                } else if (height > 0f && dragOffsetY < -height / 2 && currentIndex > 0) {
                                    items = items.toMutableList().apply { add(currentIndex - 1, removeAt(currentIndex)) }
                                    dragOffsetY += height
                                }
                            },
                            onDragEnd = {
                                draggingId = null
                                dragOffsetY = 0f
                                viewModel.reorder(items.map { it.aircraft.id })
                            },
                            onDragCancel = {
                                draggingId = null
                                dragOffsetY = 0f
                                items = aircraft
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AircraftRow(
    entry: AircraftWithRegistration,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDrag: (deltaY: Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.registration ?: "No registration",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${entry.aircraft.manufacturer} ${entry.aircraft.type}" + if (entry.aircraft.archived) " · Archived" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "☰",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 12.dp)
                .pointerInput(entry.aircraft.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDrag = { change, dragAmount -> change.consume(); onDrag(dragAmount.y) },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                    )
                },
        )
    }
}
