package nl.part66l.logbook.ui.aircraft

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

@HiltViewModel
class AircraftFormViewModel @Inject constructor(
    private val aircraftRepository: AircraftRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AircraftFormState())
    val state: StateFlow<AircraftFormState> = _state.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

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

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
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
            _state.update { it.copy(saving = false) }
            _saved.emit(Unit)
        }
    }
}
