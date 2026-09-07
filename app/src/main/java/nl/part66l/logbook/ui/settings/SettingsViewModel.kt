package nl.part66l.logbook.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.SettingsRepository

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val showArchivedAircraft: StateFlow<Boolean> = settingsRepository.showArchivedAircraft
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onShowArchivedAircraftChange(value: Boolean) {
        viewModelScope.launch { settingsRepository.setShowArchivedAircraft(value) }
    }
}
