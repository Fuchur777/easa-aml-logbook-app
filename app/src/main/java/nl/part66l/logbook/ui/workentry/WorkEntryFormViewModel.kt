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
import nl.part66l.logbook.data.CatalogueRepository
import nl.part66l.logbook.data.CatalogueTaskEntity
import nl.part66l.logbook.data.DocumentEntity
import nl.part66l.logbook.data.DocumentRepository
import nl.part66l.logbook.data.DocumentationRefInput
import nl.part66l.logbook.data.PartUsedInput
import nl.part66l.logbook.data.PersonRepository
import nl.part66l.logbook.data.SettingsRepository
import nl.part66l.logbook.data.WorkEntryRepository
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.DocumentCategory
import nl.part66l.logbook.domain.EntryRole

@HiltViewModel
class WorkEntryFormViewModel @Inject constructor(
    private val workEntryRepository: WorkEntryRepository,
    aircraftRepository: AircraftRepository,
    personRepository: PersonRepository,
    private val catalogueRepository: CatalogueRepository,
    private val settingsRepository: SettingsRepository,
    private val documentRepository: DocumentRepository,
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

    /** Catalogue tasks applicable to the profile's held subcategories — backs the task-completion picker. Loaded once; the catalogue doesn't change mid-session. */
    private val _availableTasks = MutableStateFlow<List<CatalogueTaskEntity>>(emptyList())
    val availableTasks: StateFlow<List<CatalogueTaskEntity>> = _availableTasks.asStateFlow()

    /** User's saved section order/fold state for the task picker — persisted so it survives across entries and app restarts. */
    val catalogueSectionOrder: StateFlow<List<String>> = settingsRepository.catalogueSectionOrder
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val collapsedCatalogueSections: StateFlow<Set<String>> = settingsRepository.collapsedCatalogueSections
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Non-archived documents from the directory — backs the "Documentation used" picker. */
    val documentOptions: StateFlow<List<DocumentEntity>> = documentRepository.observeAll(includeArchived = false)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    init {
        viewModelScope.launch {
            _availableTasks.value = catalogueRepository.applicableTasksForProfile()
        }
    }

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

    fun onWorkorderIssuerNameChange(value: String) = _state.update { it.copy(workorderIssuerName = value) }
    fun onWorkorderDateChange(value: LocalDate?) = _state.update { it.copy(workorderDate = value) }
    fun onWorkorderRequestedWorkChange(value: String) = _state.update { it.copy(workorderRequestedWork = value) }
    fun onWorkorderReferenceChange(value: String) = _state.update { it.copy(workorderReference = value) }

    fun onAirframeHoursChange(value: String) = _state.update { it.copy(airframeHours = value) }
    fun onLaunchesChange(value: String) = _state.update { it.copy(launches = value) }

    /** Unchecking clears the concurrent-with-ARC sub-flag — it only means something while this is checked. */
    fun onAnnualInspectionChange(value: Boolean) = _state.update {
        it.copy(annualInspection = value, concurrentWithArc = if (value) it.concurrentWithArc else false)
    }
    fun onConcurrentWithArcChange(value: Boolean) = _state.update { it.copy(concurrentWithArc = value) }

    fun onDocumentationRefAdd(ref: DocumentationRefInput) = _state.update { it.copy(documentationRefs = it.documentationRefs + ref) }
    fun onDocumentationRefRemove(index: Int) = _state.update {
        it.copy(documentationRefs = it.documentationRefs.filterIndexed { i, _ -> i != index })
    }

    fun onPartUsedAdd(part: PartUsedInput) = _state.update { it.copy(partsUsed = it.partsUsed + part) }
    fun onPartUsedRemove(index: Int) = _state.update { it.copy(partsUsed = it.partsUsed.filterIndexed { i, _ -> i != index }) }

    /** Adds straight to the directory — [documentOptions] picks it up reactively, no round trip needed here. */
    fun onCreateDocument(name: String, category: DocumentCategory, revision: String?, link: String?) {
        viewModelScope.launch { documentRepository.create(name, category, revision, link) }
    }

    fun onTaskCompletionToggle(taskId: String) = _state.update {
        it.copy(completedTaskIds = if (taskId in it.completedTaskIds) it.completedTaskIds - taskId else it.completedTaskIds + taskId)
    }

    fun onCatalogueSectionReorder(order: List<String>) {
        viewModelScope.launch { settingsRepository.setCatalogueSectionOrder(order) }
    }

    fun onCatalogueSectionFoldToggle(section: String) {
        val collapsed = section !in collapsedCatalogueSections.value
        viewModelScope.launch { settingsRepository.setCatalogueSectionCollapsed(section, collapsed) }
    }

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
                completedTaskIds = current.completedTaskIds,
                airframeHoursAtWork = current.airframeHours.toDoubleOrNull(),
                launchesAtWork = current.launches.toIntOrNull(),
                workorderIssuerName = current.workorderIssuerName.trim().ifBlank { null },
                workorderDate = current.workorderDate,
                workorderRequestedWork = current.workorderRequestedWork.trim().ifBlank { null },
                workorderReference = current.workorderReference.trim().ifBlank { null },
                annualInspection = current.annualInspection,
                concurrentWithArc = current.concurrentWithArc,
                documentationRefs = current.documentationRefs,
                partsUsed = current.partsUsed,
            )
            _state.update { it.copy(saving = false) }
            initialState = _state.value
            _saved.emit(Unit)
        }
    }
}
