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
import nl.part66l.logbook.data.DeferredItemRepository
import nl.part66l.logbook.ui.navigation.Destination

@HiltViewModel
class CrsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val crsRepository: CrsRepository,
    private val deferredItemRepository: DeferredItemRepository,
) : ViewModel() {

    private val entryId: String = savedStateHandle[Destination.Crs.ARG_ENTRY_ID]
        ?: error("CrsViewModel requires ${Destination.Crs.ARG_ENTRY_ID}")

    /** Every certificate already issued against this entry — a fresh generation adds a row, never replaces one. */
    val issued: StateFlow<List<CrsEntity>> = crsRepository.forEntry(entryId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _state = MutableStateFlow(CrsFormState())
    val state: StateFlow<CrsFormState> = _state.asStateFlow()

    fun onLimitationsChange(value: String) = _state.update { it.copy(limitations = value) }

    /** Unchecking clears any queued deferred items — they only mean something while this is checked. */
    fun onMaintenanceIncompleteChange(value: Boolean) = _state.update {
        it.copy(maintenanceIncomplete = value, deferredItemDescriptions = if (value) it.deferredItemDescriptions else emptyList())
    }

    fun onDeferredItemAdd(description: String) {
        val trimmed = description.trim()
        if (trimmed.isBlank()) return
        _state.update { it.copy(deferredItemDescriptions = it.deferredItemDescriptions + trimmed) }
    }

    fun onDeferredItemRemove(index: Int) = _state.update {
        it.copy(deferredItemDescriptions = it.deferredItemDescriptions.filterIndexed { i, _ -> i != index })
    }

    /** Adds straight to the record — [issued] picks it up reactively, no round trip needed here. */
    fun onPhotoAttached(crsId: String, path: String) {
        viewModelScope.launch { crsRepository.setSignedPhoto(crsId, path) }
    }

    /** Clears a wrongly attached photo — the slot goes back to "tap to attach", same as if none had been picked. */
    fun onPhotoRemoved(crsId: String) {
        viewModelScope.launch { crsRepository.setSignedPhoto(crsId, null) }
    }

    fun generate() {
        val current = _state.value
        if (current.generating) return
        viewModelScope.launch {
            _state.update { it.copy(generating = true) }
            val crs = crsRepository.generateUnsigned(
                entryId = entryId,
                limitations = current.limitations.trim().ifBlank { null },
                maintenanceIncomplete = current.maintenanceIncomplete,
                deferredItemDescriptions = current.deferredItemDescriptions,
            )
            // Raised against the number just allocated — a deferred item always cites a real, issued CRS.
            crs?.let { issued -> current.deferredItemDescriptions.forEach { deferredItemRepository.raise(issued.id, it) } }
            _state.value = CrsFormState() // reset for the next one, now that generating is done
        }
    }
}
