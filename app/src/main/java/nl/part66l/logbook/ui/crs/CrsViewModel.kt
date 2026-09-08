package nl.part66l.logbook.ui.crs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.CrsEntity
import nl.part66l.logbook.data.CrsRepository
import nl.part66l.logbook.ui.navigation.Destination

@HiltViewModel
class CrsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val crsRepository: CrsRepository,
) : ViewModel() {

    private val entryId: String = savedStateHandle[Destination.Crs.ARG_ENTRY_ID]
        ?: error("CrsViewModel requires ${Destination.Crs.ARG_ENTRY_ID}")

    /** Every certificate already issued against this entry — a fresh generation adds a row, never replaces one. */
    val issued: StateFlow<List<CrsEntity>> = crsRepository.forEntry(entryId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _state = MutableStateFlow(CrsFormState())
    val state: StateFlow<CrsFormState> = _state.asStateFlow()

    fun onLimitationsChange(value: String) = _state.update { it.copy(limitations = value) }
    fun onMaintenanceIncompleteChange(value: Boolean) = _state.update { it.copy(maintenanceIncomplete = value) }

    fun generate() {
        val current = _state.value
        if (current.generating) return
        viewModelScope.launch {
            _state.update { it.copy(generating = true) }
            crsRepository.generateUnsigned(
                entryId = entryId,
                limitations = current.limitations.trim().ifBlank { null },
                maintenanceIncomplete = current.maintenanceIncomplete,
            )
            _state.value = CrsFormState() // reset for the next one, now that generating is done
        }
    }
}
