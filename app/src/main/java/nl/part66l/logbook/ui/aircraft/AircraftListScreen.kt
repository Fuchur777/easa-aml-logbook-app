package nl.part66l.logbook.ui.aircraft

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.part66l.logbook.data.AircraftEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftListScreen(
    onProfileClick: () -> Unit,
    onAddAircraft: () -> Unit,
    viewModel: AircraftListViewModel = hiltViewModel(),
) {
    val aircraft by viewModel.aircraft.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aircraft") },
                actions = { IconButton(onClick = onProfileClick) { Text("👤") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAircraft) { Text("+") }
        },
    ) { padding ->
        if (aircraft.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No aircraft yet — tap + to add one.")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(aircraft, key = { it.id }) { entry ->
                    AircraftRow(entry)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AircraftRow(aircraft: AircraftEntity) {
    ListItem(
        headlineContent = { Text("${aircraft.manufacturer} ${aircraft.type}") },
        supportingContent = { Text("Serial ${aircraft.serialNumber}") },
    )
}
