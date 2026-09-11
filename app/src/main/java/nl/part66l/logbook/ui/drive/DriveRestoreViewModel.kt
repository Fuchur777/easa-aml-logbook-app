package nl.part66l.logbook.ui.drive

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.DriveBackupEntry
import nl.part66l.logbook.data.DriveBackupRepository

@HiltViewModel
class DriveRestoreViewModel @Inject constructor(
    private val driveBackupRepository: DriveBackupRepository,
) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _backups = MutableStateFlow<List<DriveBackupEntry>>(emptyList())
    val backups: StateFlow<List<DriveBackupEntry>> = _backups.asStateFlow()

    private val _restoring = MutableStateFlow(false)
    val restoring: StateFlow<Boolean> = _restoring.asStateFlow()

    private val _restoreComplete = MutableStateFlow(false)
    val restoreComplete: StateFlow<Boolean> = _restoreComplete.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun loadBackups(activity: FragmentActivity) {
        viewModelScope.launch {
            _loading.value = true
            driveBackupRepository.listBackups(activity).fold(
                onSuccess = { _backups.value = it; _errorMessage.value = null },
                onFailure = { _errorMessage.value = it.message ?: "Could not list backups." },
            )
            _loading.value = false
        }
    }

    fun restore(activity: FragmentActivity, backup: DriveBackupEntry) {
        viewModelScope.launch {
            _restoring.value = true
            driveBackupRepository.restoreBackup(activity, backup).fold(
                onSuccess = { _restoreComplete.value = true },
                onFailure = { _errorMessage.value = it.message ?: "Restore failed." },
            )
            _restoring.value = false
        }
    }

    fun dismissError() {
        _errorMessage.value = null
    }
}
