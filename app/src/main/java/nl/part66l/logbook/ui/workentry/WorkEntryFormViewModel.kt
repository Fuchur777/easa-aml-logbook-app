package nl.part66l.logbook.ui.workentry

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.AircraftRepository
import nl.part66l.logbook.data.AircraftWithRegistration
import nl.part66l.logbook.data.CatalogueRepository
import nl.part66l.logbook.data.CatalogueTaskEntity
import nl.part66l.logbook.data.DeferredItemEntity
import nl.part66l.logbook.data.DeferredItemRepository
import nl.part66l.logbook.data.DocumentEntity
import nl.part66l.logbook.data.DocumentRepository
import nl.part66l.logbook.data.DocumentationRefInput
import nl.part66l.logbook.data.PartUsedInput
import nl.part66l.logbook.data.PersonEntity
import nl.part66l.logbook.data.PersonRepository
import nl.part66l.logbook.data.PhotoInput
import nl.part66l.logbook.data.SettingsRepository
import nl.part66l.logbook.data.WorkEntryRepository
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.DocumentCategory
import nl.part66l.logbook.domain.EntryRole
import nl.part66l.logbook.ui.navigation.Destination

/** Loading/saving/deleting are metadata, not pending edits — excluded from the [WorkEntryFormViewModel.isDirty] comparison. */
private fun WorkEntryFormState.normalizedForDirtyCheck() = copy(loading = false, saving = false, deleting = false)

@HiltViewModel
class WorkEntryFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workEntryRepository: WorkEntryRepository,
    aircraftRepository: AircraftRepository,
    private val personRepository: PersonRepository,
    private val catalogueRepository: CatalogueRepository,
    private val settingsRepository: SettingsRepository,
    private val documentRepository: DocumentRepository,
    private val deferredItemRepository: DeferredItemRepository,
) : ViewModel() {

    private val entryId: String? = savedStateHandle[Destination.WorkEntryEdit.ARG_ENTRY_ID]

    private val _state = MutableStateFlow(WorkEntryFormState(entryId = entryId, loading = entryId != null))
    val state: StateFlow<WorkEntryFormState> = _state.asStateFlow()

    /** Baseline to detect unsaved changes against — updated right after every save. */
    private val _initialState = MutableStateFlow(_state.value)

    /**
     * Whether the form differs from its last-saved state — gates the unsaved-changes prompt and
     * drives the Save button's own label. Exposed as a [StateFlow], not a plain function, so the
     * Compose screen can `collectAsStateWithLifecycle()` it like [state] itself: a plain function
     * reading [_state]/[_initialState] directly is invisible to Compose's snapshot system, so a
     * screen that calls it once and holds the result in a local `val` never sees it change again.
     */
    val isDirty: StateFlow<Boolean> = combine(_state, _initialState) { current, initial ->
        current.normalizedForDirtyCheck() != initial.normalizedForDirtyCheck()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Archived aircraft aren't offered for new work — matches the Aircraft list's default visibility. */
    val aircraftOptions: StateFlow<List<AircraftWithRegistration>> = aircraftRepository.observeAllWithRegistration(includeArchived = false)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Non-archived contacts — backs the helper picker's search-as-you-type suggestions. */
    private val knownHelperContacts: StateFlow<List<PersonEntity>> = personRepository.observeAll(includeArchived = false)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val knownHelperNames: StateFlow<List<String>> = knownHelperContacts
        .map { people -> people.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** So the picker can show a matched contact's licence number alongside their name. */
    val knownHelperLicenceNumbers: StateFlow<Map<String, String?>> = knownHelperContacts
        .map { people -> people.associate { it.name to it.licenceNumber } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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

    /** Still-open notes from past incomplete-maintenance CRSs — backs the "Closes deferred item" picker. */
    val openDeferredItems: StateFlow<List<DeferredItemEntity>> = deferredItemRepository.open()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _deleted = MutableSharedFlow<Unit>()
    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()

    init {
        viewModelScope.launch {
            _availableTasks.value = catalogueRepository.applicableTasksForProfile()
        }
        entryId?.let { id ->
            viewModelScope.launch {
                val edit = workEntryRepository.forEdit(id)
                if (edit != null) {
                    _state.update {
                        it.copy(
                            aircraftSelection = edit.aircraftId?.let { aircraftId -> AircraftSelection.Specific(aircraftId) }
                                ?: AircraftSelection.Bench,
                            description = edit.description,
                            activityTypes = edit.activityTypes,
                            role = edit.role,
                            supervisedAnother = edit.supervisedAnother,
                            sessionDates = edit.sessionDates,
                            daysWorkedOverride = edit.daysWorkedOverride?.toString().orEmpty(),
                            helperNames = edit.helperNames,
                            completedTaskIds = edit.completedTaskIds,
                            workorderIssuerName = edit.workorderIssuerName.orEmpty(),
                            workorderDate = edit.workorderDate,
                            workorderRequestedWork = edit.workorderRequestedWork.orEmpty(),
                            workorderReference = edit.workorderReference.orEmpty(),
                            airframeHours = edit.airframeHoursAtWork?.toString().orEmpty(),
                            launches = edit.launchesAtWork?.toString().orEmpty(),
                            annualInspection = edit.annualInspection,
                            concurrentWithArc = edit.concurrentWithArc,
                            documentationRefs = edit.documentationRefs,
                            partsUsed = edit.partsUsed,
                            photos = edit.photos,
                            loading = false,
                        )
                    }
                } else {
                    _state.update { it.copy(loading = false) }
                }
                _initialState.value = _state.value
            }
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
    /** One day spans several sessions — this adds another rather than replacing the current one. Duplicate dates are harmless but pointless, so skipped. */
    fun onSessionDateAdd(date: LocalDate) = _state.update {
        if (date in it.sessionDates) it else it.copy(sessionDates = it.sessionDates + date)
    }

    /** At least one session is required, so a lone date can't be removed — the form has nothing sensible to fall back to. */
    fun onSessionDateRemove(date: LocalDate) = _state.update {
        if (it.sessionDates.size <= 1) it else it.copy(sessionDates = it.sessionDates - date)
    }

    fun onDaysWorkedOverrideChange(value: String) = _state.update { it.copy(daysWorkedOverride = value) }

    /**
     * [licenceNumber] is only ever non-null when the picker's inline "add new" flow captured
     * one — persisted straight to the contact directory, same as [onCreateDocument], so it's
     * remembered next time this person is picked rather than only living on this one entry.
     */
    fun onHelperAdd(name: String, licenceNumber: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        _state.update { if (trimmed in it.helperNames) it else it.copy(helperNames = it.helperNames + trimmed) }
        licenceNumber?.trim()?.takeIf { it.isNotBlank() }?.let { licence ->
            viewModelScope.launch { personRepository.findOrCreate(trimmed, licence) }
        }
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

    /** [photo] arrives already fully processed (§8) — see [processAndStorePhoto] — this just adds it to the form. */
    fun onPhotoAdd(photo: PhotoInput) = _state.update { it.copy(photos = it.photos + photo) }

    fun onPhotoCaptionChange(id: String, caption: String) = _state.update {
        it.copy(photos = it.photos.map { photo -> if (photo.id == id) photo.copy(caption = caption.ifBlank { null }) else photo })
    }

    /** Only drops it from the form — the processed file on disk is untouched, same as removing a CRS's signed-copy photo. */
    fun onPhotoRemove(id: String) = _state.update { it.copy(photos = it.photos.filterNot { photo -> photo.id == id }) }

    fun onClosesDeferredItemChange(id: String?) = _state.update { it.copy(closesDeferredItemId = id) }

    /** Adds straight to the directory — [documentOptions] picks it up reactively, no round trip needed here. */
    fun onCreateDocument(name: String, category: DocumentCategory, revision: String?, revisionDate: LocalDate?, link: String?) {
        viewModelScope.launch {
            documentRepository.create(name = name, category = category, revision = revision, revisionDate = revisionDate, link = link)
        }
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

    /** Fire-and-forget — the screen stays put either way; [WorkEntryFormState.isEditing]/[isDirty] drive the Save button's own label. */
    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch { performSave(current) }
    }

    /**
     * For a caller that needs to know the save has actually finished before doing something
     * else — e.g. the unsaved-changes dialog's own "Save" option, which then has to navigate
     * to wherever the user was originally trying to go. A no-op, returning immediately, if
     * there's nothing valid to save.
     */
    suspend fun saveAndAwaitCompletion() {
        val current = _state.value
        if (!current.canSave) return
        performSave(current)
    }

    private suspend fun performSave(current: WorkEntryFormState) {
        _state.update { it.copy(saving = true) }
        val id = current.entryId
        val savedEntryId = if (id == null) {
            workEntryRepository.create(
                aircraftId = (current.aircraftSelection as? AircraftSelection.Specific)?.aircraftId,
                description = current.description.trim(),
                activityTypes = current.activityTypes,
                role = current.role,
                supervisedAnother = current.supervisedAnother,
                sessionDates = current.sessionDates,
                daysWorkedOverride = current.daysWorkedOverride.toIntOrNull(),
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
                photos = current.photos,
            )
        } else {
            workEntryRepository.update(
                id = id,
                aircraftId = (current.aircraftSelection as? AircraftSelection.Specific)?.aircraftId,
                description = current.description.trim(),
                activityTypes = current.activityTypes,
                role = current.role,
                supervisedAnother = current.supervisedAnother,
                sessionDates = current.sessionDates,
                daysWorkedOverride = current.daysWorkedOverride.toIntOrNull(),
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
                photos = current.photos,
            )
            id
        }
        // The work that resolved it happened by the entry's own last session date, not today —
        // matches how the CRS derives its own "completed" date from the same sessions.
        current.closesDeferredItemId?.let { itemId ->
            deferredItemRepository.close(itemId, savedEntryId, current.sessionDates.max())
        }
        // entryId flips state.isEditing to true on a brand-new entry's first save, revealing
        // the certificate section immediately rather than closing and making the user reopen
        // it to find it.
        _state.update { it.copy(saving = false, entryId = savedEntryId) }
        // The new baseline is what was actually persisted above (current, the snapshot save()
        // captured) — not _state.value read now. Those can differ: if the user edited something
        // else while this suspend call was in flight (a real possibility — the repository call
        // above is a genuine thread hop), _state.value already reflects that edit, but the
        // database write just above does not. Baselining on _state.value here would make
        // isDirty silently treat that edit as saved when it was never persisted.
        _initialState.value = current.copy(saving = false, entryId = savedEntryId)
    }

    fun delete() {
        val current = _state.value
        if (!current.canDelete) return
        val id = current.entryId ?: return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true) }
            workEntryRepository.delete(id)
            _state.update { it.copy(deleting = false) }
            _deleted.emit(Unit)
        }
    }
}
