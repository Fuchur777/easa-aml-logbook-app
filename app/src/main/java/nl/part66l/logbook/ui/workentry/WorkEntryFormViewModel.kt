package nl.part66l.logbook.ui.workentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.AircraftRepository
import nl.part66l.logbook.data.AircraftWithRegistration
import nl.part66l.logbook.data.PersonRepository
import nl.part66l.logbook.data.WorkEntryRepository
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.EntryRole

@HiltViewModel
class WorkEntryFormViewModel @Inject constructor(
    private val workEntryRepository: WorkEntryRepository,
    aircraftRepository: AircraftRepository,
    personRepository: PersonRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkEntryFormState())
    val state: StateFlow<WorkEntryFormState> = _state.asStateFlow()

    /** Baseline to detect unsaved changes against — updated right after every save. */
    private var initialState: WorkEntryFormState = _state.value

    /** Archived aircraft aren't offered for new work — matches the Aircraft list's default visibility. */
    val aircraftOptions: StateFlow<List<AircraftWithRegistration>> = aircraftRepository.observeAllWithRegistration(includeArchived = false)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Backs the helper picker's search-as-you-type suggestions. */
    val knownHelperNames: StateFlow<List<String>> = personRepository.all()
        .map { people -> people.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    fun onAircraftSelectionChange(value: AircraftSelection) = _state.update { it.copy(aircraftSelection = value) }
    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }

    fun onActivityTypeToggle(type: ActivityType) = _state.update {
        it.copy(activityTypes = if (type in it.activityTypes) it.activityTypes - type else it.activityTypes + type)
    }

    fun onRoleChange(value: EntryRole) = _state.update { it.copy(role = value) }

    /** Unchecking clears any named helpers — they only mean something while this is checked. */
    fun onSupervisedAnotherChange(value: Boolean) = _state.update {
        it.copy(supervisedAnother = value, helperNames = if (value) it.helperNames else emptyList())
    }
    fun onSessionDateChange(value: LocalDate?) = _state.update { it.copy(sessionDate = value ?: it.sessionDate) }

    fun onHelperAdd(name: String) = _state.update {
        if (name.isBlank() || name in it.helperNames) it else it.copy(helperNames = it.helperNames + name)
    }

    fun onHelperRemove(name: String) = _state.update { it.copy(helperNames = it.helperNames - name) }
    fun onResearchAndPaperworkChange(value: Boolean) = _state.update { it.copy(researchAndPaperwork = value) }

    /** Whether the form differs from its last-saved state — gates the unsaved-changes prompt on close/back. */
    fun isDirty(): Boolean = _state.value.copy(saving = false) != initialState.copy(saving = false)

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            workEntryRepository.create(
                aircraftId = (current.aircraftSelection as? AircraftSelection.Specific)?.aircraftId,
                description = current.description.trim(),
                activityTypes = current.activityTypes,
                role = current.role,
                supervisedAnother = current.supervisedAnother,
                sessionDate = current.sessionDate,
                helperNames = current.helperNames,
                researchAndPaperwork = current.researchAndPaperwork,
            )
            _state.update { it.copy(saving = false) }
            initialState = _state.value
            _saved.emit(Unit)
        }
    }
}
