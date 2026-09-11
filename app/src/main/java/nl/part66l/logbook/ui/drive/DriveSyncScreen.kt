package nl.part66l.logbook.ui.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import nl.part66l.logbook.ui.theme.part66TopAppBarColors

private val LAST_SYNC_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveSyncScreen(
    onClose: () -> Unit,
    onRestore: () -> Unit,
    viewModel: DriveSyncViewModel = hiltViewModel(),
) {
    val connectedAccountEmail by viewModel.connectedAccountEmail.collectAsStateWithLifecycle()
    val lastSyncAt by viewModel.lastSyncAt.collectAsStateWithLifecycle()
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val lastSummary by viewModel.lastSummary.collectAsStateWithLifecycle()
    val backingUp by viewModel.backingUp.collectAsStateWithLifecycle()
    val backupCreated by viewModel.backupCreated.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? FragmentActivity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Google Drive backup") },
                navigationIcon = { IconButton(onClick = onClose) { Text("✕") } },
                colors = part66TopAppBarColors(),
                expandedHeight = 48.dp,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Backs up signed certificates, photos and documents to Drive, mirroring your " +
                    "aircraft and work-entry folders, plus full-device backups you can restore " +
                    "from. The app is fully functional offline without any of this.",
                style = MaterialTheme.typography.bodyMedium,
            )

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val account = connectedAccountEmail
                    if (account != null) {
                        LabeledValue("Connected account", account)
                        LabeledValue("Last synced", lastSyncAt?.let { LAST_SYNC_FORMAT.format(it) } ?: "Never")
                        Button(
                            onClick = { activity?.let(viewModel::syncNow) },
                            enabled = !syncing && activity != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (syncing) "Syncing…" else "Sync now")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sync automatically")
                                Text(
                                    "Runs in the background roughly every 12 hours, in addition to \"Sync now\".",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = autoSyncEnabled, onCheckedChange = viewModel::onAutoSyncToggle)
                        }
                        OutlinedButton(
                            onClick = viewModel::disconnect,
                            enabled = !syncing,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Disconnect")
                        }

                        HorizontalDivider()

                        Text("Full-device backup", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "A complete copy of everything on this device — for moving to a new " +
                                "phone or recovering from one lost or reset. Unencrypted; only " +
                                "this Google account can see it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = { activity?.let(viewModel::createBackup) },
                            enabled = !backingUp && activity != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (backingUp) "Backing up…" else "Create backup now")
                        }
                        OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                            Text("Restore from a Drive backup")
                        }
                    } else {
                        Button(
                            onClick = { activity?.let(viewModel::connect) },
                            enabled = !syncing && activity != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (syncing) "Connecting…" else "Connect Google Drive")
                        }
                        OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) {
                            Text("Restore from a Drive backup")
                        }
                    }

                    lastSummary?.let { summary ->
                        HorizontalDivider()
                        Text("Last sync result", style = MaterialTheme.typography.titleMedium)
                        Text("${summary.uploaded} uploaded, ${summary.failed} failed")
                        summary.errors.forEach { error ->
                            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (backupCreated) {
        AlertDialog(
            onDismissRequest = viewModel::dismissBackupCreated,
            title = { Text("Backup created") },
            text = { Text("Uploaded to the \"Backups\" folder in your Drive.") },
            confirmButton = { TextButton(onClick = viewModel::dismissBackupCreated) { Text("OK") } },
        )
    }

    errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Google Drive") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
