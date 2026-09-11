package nl.part66l.logbook.ui.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import nl.part66l.logbook.data.DriveBackupEntry
import nl.part66l.logbook.ui.theme.part66TopAppBarColors
import kotlin.system.exitProcess

private val BACKUP_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm").withZone(ZoneId.systemDefault())

/**
 * Reached from the Drive backup card in Settings, and from the first-run profile screen (a fresh
 * install has no other way to reach Settings at all). [onClose] is null for the first-run case,
 * same convention as `ProfileFormScreen`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveRestoreScreen(
    onClose: (() -> Unit)?,
    viewModel: DriveRestoreViewModel = hiltViewModel(),
) {
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val backups by viewModel.backups.collectAsStateWithLifecycle()
    val restoring by viewModel.restoring.collectAsStateWithLifecycle()
    val restoreComplete by viewModel.restoreComplete.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? FragmentActivity
    var pendingRestore by remember { mutableStateOf<DriveBackupEntry?>(null) }

    LaunchedEffect(activity) {
        activity?.let(viewModel::loadBackups)
    }

    if (restoreComplete) {
        Scaffold { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Restore complete", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "AMLog has to start fresh to read the restored records — the database was " +
                        "replaced underneath it while it was running.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = {
                        // finishAffinity() alone only closes the activities; Android may keep the
                        // process cached and hand it back on relaunch, and that process still holds
                        // Room's handle on the database file we just replaced — so the user would
                        // reopen the app and see the old data. Ending the process is the point of
                        // this button, not a side effect of it.
                        activity?.finishAffinity()
                        exitProcess(0)
                    },
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    Text("Close AMLog")
                }
                Text(
                    "Then open it again from your home screen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Restore from Drive") },
                navigationIcon = { onClose?.let { close -> IconButton(onClick = close) { Text("✕") } } },
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
                "Choose a backup to restore. This replaces everything currently on this device — " +
                    "there's no undo once it starts.",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (loading) {
                CircularProgressIndicator()
            } else if (backups.isEmpty()) {
                Text(
                    "No backups found for this Google account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                backups.forEach { backup ->
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                        ) {
                            Text(backup.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                BACKUP_DATE_FORMAT.format(backup.createdAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { pendingRestore = backup }, enabled = !restoring) {
                                Text("Restore this backup")
                            }
                        }
                    }
                }
            }
        }
    }

    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { if (!restoring) pendingRestore = null },
            title = { Text("Restore \"${backup.name}\"?") },
            text = { Text("This replaces everything currently on this device with the contents of this backup. There's no undo.") },
            confirmButton = {
                TextButton(
                    onClick = { activity?.let { viewModel.restore(it, backup) } },
                    enabled = !restoring,
                ) {
                    Text(if (restoring) "Restoring…" else "Replace everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }, enabled = !restoring) { Text("Cancel") }
            },
        )
    }

    errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Restore from Drive") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }
}
