package nl.part66l.logbook.ui.aircraft

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.AircraftRepository
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.Structure
import nl.part66l.logbook.domain.Subcategory
import nl.part66l.logbook.ui.navigation.Destination

@HiltViewModel
class AircraftFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val aircraftRepository: AircraftRepository,
) : ViewModel() {

    private val aircraftId: String? = savedStateHandle[Destination.AircraftEdit.ARG_AIRCRAFT_ID]

    private val _state = MutableStateFlow(AircraftFormState(aircraftId = aircraftId, loading = aircraftId != null))
    val state: StateFlow<AircraftFormState> = _state.asStateFlow()

    /** Baseline to detect unsaved changes against — updated to match [_state] right after load and right after every save. */
    private var initialState: AircraftFormState = _state.value

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    private val _deleted = MutableSharedFlow<Unit>()
    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()

    init {
        aircraftId?.let { id ->
            viewModelScope.launch {
                val entity = aircraftRepository.byId(id)
                val registration = aircraftRepository.currentRegistration(id, LocalDate.now()).orEmpty()
                val hasHistory = aircraftRepository.hasWorkHistory(id)
                if (entity != null) {
                    _state.update {
                        it.copy(
                            manufacturer = entity.manufacturer,
                            type = entity.type,
                            serialNumber = entity.serialNumber,
                            propulsion = entity.propulsion,
                            structure = entity.structure,
                            subcategoryOverride = entity.subcategoryOverride,
                            registration = registration,
                            archived = entity.archived,
                            hasWorkHistory = hasHistory,
                            loading = false,
                        )
                    }
                } else {
                    _state.update { it.copy(loading = false) }
                }
                initialState = _state.value
            }
        }
    }

    /** Whether the form differs from what's saved — gates the unsaved-changes prompt on close/back. Archive/loading/etc. are metadata, not pending edits. */
    fun isDirty(): Boolean {
        fun AircraftFormState.normalized() = copy(loading = false, saving = false, deleting = false, hasWorkHistory = false, archived = false)
        return _state.value.normalized() != initialState.normalized()
    }

    fun onManufacturerChange(value: String) = _state.update { it.copy(manufacturer = value) }
    fun onTypeChange(value: String) = _state.update { it.copy(type = value) }
    fun onSerialNumberChange(value: String) = _state.update { it.copy(serialNumber = value) }
    fun onPropulsionChange(value: Propulsion) = _state.update { it.copy(propulsion = value) }

    /** Leaving MIXED clears any override — it only means something for a mixed aircraft. */
    fun onStructureChange(value: Structure) = _state.update {
        it.copy(structure = value, subcategoryOverride = if (value == Structure.MIXED) it.subcategoryOverride else null)
    }

    fun onSubcategoryOverrideChange(value: Subcategory) = _state.update { it.copy(subcategoryOverride = value) }
    fun onRegistrationChange(value: String) = _state.update { it.copy(registration = value) }
    fun onValidFromChange(value: LocalDate?) = _state.update { it.copy(validFrom = value ?: it.validFrom) }

    fun onArchiveToggle() {
        val id = _state.value.aircraftId ?: return
        val newValue = !_state.value.archived
        viewModelScope.launch {
            aircraftRepository.setArchived(id, newValue)
            _state.update { it.copy(archived = newValue) }
        }
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val id = current.aircraftId
            if (id == null) {
                aircraftRepository.create(
                    manufacturer = current.manufacturer.trim(),
                    type = current.type.trim(),
                    serialNumber = current.serialNumber.trim(),
                    propulsion = current.propulsion,
                    structure = current.structure,
                    subcategoryOverride = current.subcategoryOverride,
                    registration = current.registration.trim().uppercase(),
                    validFrom = current.validFrom,
                )
            } else {
                aircraftRepository.update(
                    id = id,
                    manufacturer = current.manufacturer.trim(),
                    type = current.type.trim(),
                    serialNumber = current.serialNumber.trim(),
                    propulsion = current.propulsion,
                    structure = current.structure,
                    subcategoryOverride = current.subcategoryOverride,
                    registration = current.registration.trim().uppercase(),
                    registrationValidFrom = current.validFrom,
                )
            }
            _state.update { it.copy(saving = false) }
            initialState = _state.value
            _saved.emit(Unit)
        }
    }

    fun delete() {
        val current = _state.value
        if (!current.canDelete) return
        val id = current.aircraftId ?: return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true) }
            aircraftRepository.delete(id)
            _state.update { it.copy(deleting = false) }
            _deleted.emit(Unit)
        }
    }
}
