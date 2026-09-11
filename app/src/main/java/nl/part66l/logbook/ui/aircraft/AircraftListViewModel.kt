package nl.part66l.logbook.ui.aircraft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.AircraftRepository
import nl.part66l.logbook.data.AircraftWithRegistration
import nl.part66l.logbook.data.SettingsRepository

@HiltViewModel
class AircraftListViewModel @Inject constructor(
    private val aircraftRepository: AircraftRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Toggled in the list's own header rather than Settings — it's a view filter, not a preference you set once and forget. */
    val showArchived: StateFlow<Boolean> = settingsRepository.showArchivedAircraft
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onShowArchivedChange(value: Boolean) {
        viewModelScope.launch { settingsRepository.setShowArchivedAircraft(value) }
    }

    // Eagerly, not WhileSubscribed: this is a small table for a single-user app,
    // and always-fresh state is simpler to reason about (and to test) than a
    // sharing policy tied to UI subscriber lifecycle for negligible resource gain.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val aircraft: StateFlow<List<AircraftWithRegistration>> = settingsRepository.showArchivedAircraft
        .flatMapLatest { includeArchived -> aircraftRepository.observeAllWithRegistration(includeArchived) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Persists a full manual re-ordering, top to bottom. */
    fun reorder(orderedIds: List<String>) {
        viewModelScope.launch { aircraftRepository.reorder(orderedIds) }
    }
}
