package nl.part66l.logbook.ui.aircraft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import nl.part66l.logbook.data.AircraftEntity
import nl.part66l.logbook.data.AircraftRepository

@HiltViewModel
class AircraftListViewModel @Inject constructor(
    aircraftRepository: AircraftRepository,
) : ViewModel() {

    // Eagerly, not WhileSubscribed: this is a small table for a single-user app,
    // and always-fresh state is simpler to reason about (and to test) than a
    // sharing policy tied to UI subscriber lifecycle for negligible resource gain.
    val aircraft: StateFlow<List<AircraftEntity>> = aircraftRepository.all()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
