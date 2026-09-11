package nl.part66l.logbook.ui.drive

import android.content.Context
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.DriveBackupRepository
import nl.part66l.logbook.data.DriveSyncRepository
import nl.part66l.logbook.data.SettingsRepository
import nl.part66l.logbook.data.SyncSummary
import nl.part66l.logbook.drive.DriveSyncScheduler

@HiltViewModel
class DriveSyncViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val driveSyncRepository: DriveSyncRepository,
    private val driveBackupRepository: DriveBackupRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val connectedAccountEmail: StateFlow<String?> = settingsRepository.connectedGoogleAccountEmail
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val lastSyncAt: StateFlow<Instant?> = settingsRepository.lastDriveSyncAt
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val autoSyncEnabled: StateFlow<Boolean> = settingsRepository.driveAutoSyncEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _lastSummary = MutableStateFlow<SyncSummary?>(null)
    val lastSummary: StateFlow<SyncSummary?> = _lastSummary.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _backingUp = MutableStateFlow(false)
    val backingUp: StateFlow<Boolean> = _backingUp.asStateFlow()

    private val _backupCreated = MutableStateFlow(false)
    val backupCreated: StateFlow<Boolean> = _backupCreated.asStateFlow()

    fun connect(activity: FragmentActivity) {
        viewModelScope.launch {
            _syncing.value = true
            driveSyncRepository.connect(activity).fold(
                onSuccess = { _errorMessage.value = null },
                onFailure = { _errorMessage.value = it.message ?: "Could not connect to Google Drive." },
            )
            _syncing.value = false
        }
    }

    fun syncNow(activity: FragmentActivity) {
        viewModelScope.launch {
            _syncing.value = true
            driveSyncRepository.syncNow(activity).fold(
                onSuccess = { summary ->
                    _lastSummary.value = summary
                    _errorMessage.value = null
                },
                onFailure = { _errorMessage.value = it.message ?: "Sync failed." },
            )
            _syncing.value = false
        }
    }

    /**
     * Disconnecting has to stop the background worker too, not just forget the account.
     *
     * The periodic work is enqueued by name and survives process death, and the OAuth grant
     * lives with Play Services rather than with us — so clearing the stored email alone left
     * a worker that still got a token and kept uploading to the account the user had just
     * disconnected. Worse, the "Sync automatically" switch sits inside the connected branch of
     * this screen, so once disconnected there was no control left to turn it off with.
     */
    fun disconnect() {
        _lastSummary.value = null
        DriveSyncScheduler.cancel(appContext)
        viewModelScope.launch {
            settingsRepository.setDriveAutoSyncEnabled(false)
            driveSyncRepository.disconnect()
        }
    }

    fun onAutoSyncToggle(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDriveAutoSyncEnabled(enabled) }
        if (enabled) DriveSyncScheduler.schedule(appContext) else DriveSyncScheduler.cancel(appContext)
    }

    fun createBackup(activity: FragmentActivity) {
        viewModelScope.launch {
            _backingUp.value = true
            driveBackupRepository.createBackup(activity).fold(
                onSuccess = {
                    _backupCreated.value = true
                    _errorMessage.value = null
                },
                onFailure = { _errorMessage.value = it.message ?: "Backup failed." },
            )
            _backingUp.value = false
        }
    }

    fun dismissBackupCreated() {
        _backupCreated.value = false
    }

    fun dismissError() {
        _errorMessage.value = null
    }
}
