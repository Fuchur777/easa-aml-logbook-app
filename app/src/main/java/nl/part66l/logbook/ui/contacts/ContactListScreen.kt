package nl.part66l.logbook.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.part66l.logbook.data.PersonEntity
import nl.part66l.logbook.ui.theme.part66TopAppBarColors
import nl.part66l.logbook.ui.theme.part66TopBarSwitchColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactListScreen(
    onAddContact: () -> Unit,
    onEditContact: (String) -> Unit,
    onClose: () -> Unit,
    viewModel: ContactListViewModel = hiltViewModel(),
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val showArchived by viewModel.showArchived.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contacts") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Text("✕") }
                },
                actions = {
                    Text("Archived", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    Switch(
                        checked = showArchived,
                        onCheckedChange = viewModel::onShowArchivedChange,
                        colors = part66TopBarSwitchColors(),
                        modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                    )
                },
                colors = part66TopAppBarColors(),
                expandedHeight = 48.dp,
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddContact) { Text("+") }
        },
    ) { padding ->
        if (contacts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                Text("No contacts yet — tap + to add one.", textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                itemsIndexed(contacts, key = { _, contact -> contact.id }) { index, contact ->
                    Column {
                        ContactRow(
                            contact = contact,
                            backgroundColor = if (index % 2 == 0) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            onClick = { onEditContact(contact.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: PersonEntity, backgroundColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name, style = MaterialTheme.typography.titleMedium)
            val subtitle = listOfNotNull(
                contact.licenceNumber?.takeIf { it.isNotBlank() },
                if (contact.archived) "Archived" else null,
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
