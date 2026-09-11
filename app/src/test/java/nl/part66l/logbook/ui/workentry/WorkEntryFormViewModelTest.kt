package nl.part66l.logbook.ui.workentry

import androidx.lifecycle.SavedStateHandle
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import nl.part66l.logbook.data.CatalogueTaskEntity
import nl.part66l.logbook.data.DocumentEntity
import nl.part66l.logbook.data.DocumentationRefInput
import nl.part66l.logbook.data.PartUsedInput
import nl.part66l.logbook.data.PersonEntity
import nl.part66l.logbook.data.PhotoInput
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.DocumentCategory
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.Structure
import nl.part66l.logbook.fakes.FakeAircraftRepository
import nl.part66l.logbook.fakes.FakeCatalogueRepository
import nl.part66l.logbook.fakes.FakeDeferredItemRepository
import nl.part66l.logbook.fakes.FakeDocumentRepository
import nl.part66l.logbook.fakes.FakePersonRepository
import nl.part66l.logbook.fakes.FakeSettingsRepository
import nl.part66l.logbook.fakes.FakeWorkEntryRepository
import nl.part66l.logbook.ui.navigation.Destination
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkEntryFormViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newState(entryId: String? = null) = SavedStateHandle(
        entryId?.let { mapOf(Destination.WorkEntryEdit.ARG_ENTRY_ID to it) } ?: emptyMap(),
    )

    private fun viewModel(
        savedStateHandle: SavedStateHandle = newState(),
        workEntryRepository: FakeWorkEntryRepository = FakeWorkEntryRepository(),
        aircraftRepository: FakeAircraftRepository = FakeAircraftRepository(),
        personRepository: FakePersonRepository = FakePersonRepository(),
        catalogueRepository: FakeCatalogueRepository = FakeCatalogueRepository(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
        documentRepository: FakeDocumentRepository = FakeDocumentRepository(),
        deferredItemRepository: FakeDeferredItemRepository = FakeDeferredItemRepository(),
    ) = WorkEntryFormViewModel(
        savedStateHandle, workEntryRepository, aircraftRepository, personRepository,
        catalogueRepository, settingsRepository, documentRepository, deferredItemRepository,
    )

    @Test
    fun `canSave requires a description, an aircraft selection and at least one activity`() {
        val viewModel = viewModel()

        assertFalse(viewModel.state.value.canSave)
        assertEquals(AircraftSelection.Unselected, viewModel.state.value.aircraftSelection)

        viewModel.onDescriptionChange("Bench-tested altimeter")
        assertFalse(viewModel.state.value.canSave) // no aircraft selection or activity yet

        viewModel.onAircraftSelectionChange(AircraftSelection.Bench)
        assertFalse(viewModel.state.value.canSave) // still no activity

        viewModel.onActivityTypeToggle(ActivityType.TROUBLESHOOTING)
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun `activity toggle adds and removes from the set`() {
        val viewModel = viewModel()

        viewModel.onActivityTypeToggle(ActivityType.REPAIRING)
        viewModel.onActivityTypeToggle(ActivityType.TROUBLESHOOTING)
        assertEquals(setOf(ActivityType.REPAIRING, ActivityType.TROUBLESHOOTING), viewModel.state.value.activityTypes)

        viewModel.onActivityTypeToggle(ActivityType.REPAIRING)
        assertEquals(setOf(ActivityType.TROUBLESHOOTING), viewModel.state.value.activityTypes)
    }

    @Test
    fun `save creates the entry with the entered activities, supervision flag and helpers`() {
        val repository = FakeWorkEntryRepository()
        val viewModel = viewModel(workEntryRepository = repository)
        viewModel.onAircraftSelectionChange(AircraftSelection.Bench)
        viewModel.onDescriptionChange("Replaced altimeter seal")
        viewModel.onActivityTypeToggle(ActivityType.REPAIRING)
        viewModel.onActivityTypeToggle(ActivityType.INSPECTION)
        viewModel.onHelperAdd("Jan de Vries")

        viewModel.save()

        assertEquals(1, repository.created.size)
        val created = repository.created.first()
        assertEquals("Replaced altimeter seal", created.entry.description)
        assertNull(created.entry.aircraftId)
        assertEquals(setOf(ActivityType.REPAIRING, ActivityType.INSPECTION), created.activityTypes)
        assertTrue(created.entry.supervisedAnother) // derived from having a named helper — there's no separate checkbox any more
        assertEquals(listOf("Jan de Vries"), created.helperNames)
    }

    @Test
    fun `save records no supervision when no helper was added`() {
        val repository = FakeWorkEntryRepository()
        val viewModel = viewModel(workEntryRepository = repository)
        viewModel.onAircraftSelectionChange(AircraftSelection.Bench)
        viewModel.onDescriptionChange("Solo bench work")
        viewModel.onActivityTypeToggle(ActivityType.SERVICING)

        viewModel.save()

        assertFalse(repository.created.first().entry.supervisedAnother)
    }

    @Test
    fun `save resolves a specific aircraft selection to its id`() {
        val repository = FakeWorkEntryRepository()
        val aircraftRepository = FakeAircraftRepository()
        val aircraftId = runBlocking {
            aircraftRepository.create(
                manufacturer = "Schleicher", type = "ASK 21", serialNumber = "1",
                propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
                subcategoryOverride = null, registration = "PH-0001", validFrom = LocalDate.of(2020, 1, 1),
            )
        }
        val viewModel = viewModel(workEntryRepository = repository, aircraftRepository = aircraftRepository)
        viewModel.onAircraftSelectionChange(AircraftSelection.Specific(aircraftId))
        viewModel.onDescriptionChange("Wing inspection")
        viewModel.onActivityTypeToggle(ActivityType.INSPECTION)

        viewModel.save()

        assertEquals(aircraftId, repository.created.first().entry.aircraftId)
    }

    @Test
    fun `research and paperwork is a regular activity type, chosen and saved the same as any other`() {
        val repository = FakeWorkEntryRepository()
        val viewModel = viewModel(workEntryRepository = repository)
        viewModel.onAircraftSelectionChange(AircraftSelection.Bench)
        viewModel.onDescriptionChange("Read the new AD")
        viewModel.onActivityTypeToggle(ActivityType.RESEARCH_AND_PAPERWORK)

        assertTrue(viewModel.state.value.canSave) // it alone satisfies "at least one activity", same as any other type

        viewModel.save()

        assertEquals(setOf(ActivityType.RESEARCH_AND_PAPERWORK), repository.created.first().activityTypes)
    }

    @Test
    fun `documentOptions reflects the document repository`() {
        val document = DocumentEntity(id = "d1", name = "AMM", category = DocumentCategory.MANUAL)
        val viewModel = viewModel(documentRepository = FakeDocumentRepository(initial = listOf(document)))

        assertEquals(listOf(document), viewModel.documentOptions.value)
    }

    @Test
    fun `onCreateDocument adds straight to the document directory, picked up by documentOptions`() {
        val documentRepository = FakeDocumentRepository()
        val viewModel = viewModel(documentRepository = documentRepository)

        viewModel.onCreateDocument("AMM", DocumentCategory.MANUAL, "Rev 1", LocalDate.of(2025, 6, 1), "https://example.com")

        assertEquals(1, viewModel.documentOptions.value.size)
        val created = viewModel.documentOptions.value.first()
        assertEquals("AMM", created.name)
        assertEquals(DocumentCategory.MANUAL, created.category)
        assertEquals("Rev 1", created.revision)
        assertEquals(LocalDate.of(2025, 6, 1), created.revisionDate)
        assertEquals("https://example.com", created.link)
    }

    @Test
    fun `availableTasks reflects the catalogue repository's applicable tasks`() {
        val task = CatalogueTaskEntity(
            id = "T1", catalogueVersion = "2026.1", table = "B", section = "General activities", sectionCode = "GEN",
            text = "Task text", reference = "ref", appliesToL1 = true, appliesToL1C = false, appliesToL2 = false, appliesToL2C = false,
        )
        val viewModel = viewModel(catalogueRepository = FakeCatalogueRepository(applicableTasks = listOf(task)))

        assertEquals(listOf(task), viewModel.availableTasks.value)
    }

    @Test
    fun `task completion toggle adds and removes from the set, and is passed through on save`() {
        val repository = FakeWorkEntryRepository()
        val viewModel = viewModel(workEntryRepository = repository)
        viewModel.onAircraftSelectionChange(AircraftSelection.Bench)
        viewModel.onDescriptionChange("Replaced altimeter seal")
        viewModel.onActivityTypeToggle(ActivityType.REPAIRING)

        viewModel.onTaskCompletionToggle("T1")
        viewModel.onTaskCompletionToggle("T2")
        assertEquals(setOf("T1", "T2"), viewModel.state.value.completedTaskIds)

        viewModel.onTaskCompletionToggle("T1")
        assertEquals(setOf("T2"), viewModel.state.value.completedTaskIds)

        viewModel.save()

        assertEquals(setOf("T2"), repository.created.first().completedTaskIds)
    }

    @Test
    fun `catalogueSectionOrder and collapsedCatalogueSections reflect the settings repository`() {
        val settingsRepository = FakeSettingsRepository(
            initialCatalogueSectionOrder = listOf("Propulsion", "General activities"),
            initialCollapsedCatalogueSections = setOf("Propulsion"),
        )
        val viewModel = viewModel(settingsRepository = settingsRepository)

        assertEquals(listOf("Propulsion", "General activities"), viewModel.catalogueSectionOrder.value)
        assertEquals(setOf("Propulsion"), viewModel.collapsedCatalogueSections.value)
    }

    @Test
    fun `section reorder is persisted to settings`() {
        val settingsRepository = FakeSettingsRepository()
        val viewModel = viewModel(settingsRepository = settingsRepository)

        viewModel.onCatalogueSectionReorder(listOf("Propulsion", "General activities"))

        assertEquals(listOf("Propulsion", "General activities"), viewModel.catalogueSectionOrder.value)
    }

    @Test
    fun `section fold toggle flips the section's collapsed state in settings`() {
        val settingsRepository = FakeSettingsRepository()
        val viewModel = viewModel(settingsRepository = settingsRepository)

        viewModel.onCatalogueSectionFoldToggle("General activities")
        assertEquals(setOf("General activities"), viewModel.collapsedCatalogueSections.value)

        viewModel.onCatalogueSectionFoldToggle("General activities")
        assertEquals(emptySet<String>(), viewModel.collapsedCatalogueSections.value)
    }

    @Test
    fun `airframe hours and launches are optional but must parse when entered`() {
        val viewModel = viewModel()
        viewModel.onAircraftSelectionChange(AircraftSelection.Bench)
        viewModel.onDescriptionChange("Bench work")
        viewModel.onActivityTypeToggle(ActivityType.SERVICING)
        assertTrue(viewModel.state.value.canSave) // both blank — fine

        viewModel.onAirframeHoursChange("not a number")
        assertFalse(viewModel.state.value.canSave)
        assertEquals("Enter a number", viewModel.state.value.airframeHoursError)

        viewModel.onAirframeHoursChange("1234.5")
        assertNull(viewModel.state.value.airframeHoursError)
        assertTrue(viewModel.state.value.canSave)

        viewModel.onLaunchesChange("not a number")
        assertFalse(viewModel.state.value.canSave)
        assertEquals("Enter a whole number", viewModel.state.value.launchesError)

        viewModel.onLaunchesChange("42")
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun `annual inspection toggle clears concurrent-with-ARC when unchecked`() {
        val viewModel = viewModel()

        viewModel.onAnnualInspectionChange(true)
        viewModel.onConcurrentWithArcChange(true)
        assertTrue(viewModel.state.value.concurrentWithArc)

        viewModel.onAnnualInspectionChange(false)
        assertFalse(viewModel.state.value.annualInspection)
        assertFalse(viewModel.state.value.concurrentWithArc)
    }

    @Test
    fun `documentation refs and parts used can be added and removed, and are passed through on save`() {
        val repository = FakeWorkEntryRepository()
        val viewModel = viewModel(workEntryRepository = repository)
        viewModel.onAircraftSelectionChange(AircraftSelection.Bench)
        viewModel.onDescriptionChange("Panel inspection")
        viewModel.onActivityTypeToggle(ActivityType.INSPECTION)

        viewModel.onDocumentationRefAdd(DocumentationRefInput("AMM 12-34", "Rev 5"))
        viewModel.onDocumentationRefAdd(DocumentationRefInput("SB 99-01"))
        viewModel.onPartUsedAdd(PartUsedInput("PN-001", batchOrSerial = "SN-9"))
        assertEquals(2, viewModel.state.value.documentationRefs.size)
        assertEquals(1, viewModel.state.value.partsUsed.size)

        viewModel.onDocumentationRefRemove(0)
        assertEquals(listOf(DocumentationRefInput("SB 99-01")), viewModel.state.value.documentationRefs)

        viewModel.save()

        val created = repository.created.first()
        assertEquals(listOf(DocumentationRefInput("SB 99-01")), created.documentationRefs)
        assertEquals(listOf(PartUsedInput("PN-001", batchOrSerial = "SN-9")), created.partsUsed)
    }

    private fun photo(id: String, caption: String? = null) =
        PhotoInput(id = id, localPath = "/data/attachments/$id.jpg", sha256 = "hash-$id", capturedAt = Instant.now(), caption = caption, bytes = 100)

    @Test
    fun `photos can be added, captioned and removed, and are passed through on save`() {
        val repository = FakeWorkEntryRepository()
        val viewModel = viewModel(workEntryRepository = repository)
        viewModel.onAircraftSelectionChange(AircraftSelection.Bench)
        viewModel.onDescriptionChange("Panel inspection")
        viewModel.onActivityTypeToggle(ActivityType.INSPECTION)

        viewModel.onPhotoAdd(photo("p1"))
        viewModel.onPhotoAdd(photo("p2"))
        assertEquals(2, viewModel.state.value.photos.size)

        viewModel.onPhotoCaptionChange("p1", "Wing spar corrosion")
        assertEquals("Wing spar corrosion", viewModel.state.value.photos.first { it.id == "p1" }.caption)

        viewModel.onPhotoRemove("p2")
        assertEquals(listOf("p1"), viewModel.state.value.photos.map { it.id })

        viewModel.save()

        assertEquals(listOf("p1"), repository.created.first().photos.map { it.id })
        assertEquals("Wing spar corrosion", repository.created.first().photos.first().caption)
    }

    @Test
    fun `a blank caption is stored as null, not an empty string`() {
        val viewModel = viewModel()
        viewModel.onPhotoAdd(photo("p1", caption = "Something"))

        viewModel.onPhotoCaptionChange("p1", "   ")

        assertEquals(null, viewModel.state.value.photos.first().caption)
    }

    @Test
    fun `save passes through workorder fields and the annual inspection flag`() {
        val repository = FakeWorkEntryRepository()
        val viewModel = viewModel(workEntryRepository = repository)
        viewModel.onAircraftSelectionChange(AircraftSelection.Bench)
        viewModel.onDescriptionChange("Annual inspection")
        viewModel.onActivityTypeToggle(ActivityType.INSPECTION)
        viewModel.onWorkorderIssuerNameChange("Piet Bakker")
        viewModel.onWorkorderDateChange(LocalDate.of(2026, 1, 10))
        viewModel.onWorkorderRequestedWorkChange("Annual inspection per programme")
        viewModel.onWorkorderReferenceChange("WO-2026-001")
        viewModel.onAnnualInspectionChange(true)
        viewModel.onConcurrentWithArcChange(true)

        viewModel.save()

        val created = repository.created.first().entry
        assertEquals("Piet Bakker", created.workorderIssuerName)
        assertEquals(LocalDate.of(2026, 1, 10), created.workorderDate)
        assertEquals("Annual inspection per programme", created.workorderRequestedWork)
        assertEquals("WO-2026-001", created.workorderReference)
        assertTrue(created.annualInspection)
        assertTrue(created.concurrentWithArc)
    }

    @Test
    fun `helper add is a no-op for a blank or already-selected name`() {
        val viewModel = viewModel()

        viewModel.onHelperAdd("Jan de Vries")
        viewModel.onHelperAdd("Jan de Vries")
        viewModel.onHelperAdd("")

        assertEquals(listOf("Jan de Vries"), viewModel.state.value.helperNames)
    }

    @Test
    fun `helper add with a licence number persists it to the contact directory`() {
        val personRepository = FakePersonRepository()
        val viewModel = viewModel(personRepository = personRepository)

        viewModel.onHelperAdd("Jan de Vries", "NL.66.11111")

        assertEquals(listOf("Jan de Vries"), viewModel.state.value.helperNames)
        val person = runBlocking { personRepository.observeAll(includeArchived = false).first() }.first()
        assertEquals("Jan de Vries", person.name)
        assertEquals("NL.66.11111", person.licenceNumber)
    }

    @Test
    fun `session dates default to one entry, accumulate, and never drop to zero`() {
        val viewModel = viewModel()

        assertEquals(1, viewModel.state.value.sessionDates.size)

        viewModel.onSessionDateAdd(LocalDate.of(2026, 3, 5))
        viewModel.onSessionDateAdd(LocalDate.of(2026, 3, 6))
        assertEquals(3, viewModel.state.value.sessionDates.size)

        viewModel.onSessionDateAdd(LocalDate.of(2026, 3, 5)) // duplicate — no-op
        assertEquals(3, viewModel.state.value.sessionDates.size)

        viewModel.onSessionDateRemove(LocalDate.of(2026, 3, 5))
        viewModel.onSessionDateRemove(LocalDate.of(2026, 3, 6))
        assertEquals(1, viewModel.state.value.sessionDates.size)

        viewModel.onSessionDateRemove(viewModel.state.value.sessionDates.first())
        assertEquals(1, viewModel.state.value.sessionDates.size) // the last date can't be removed
    }

    @Test
    fun `days worked override must parse as a whole number when entered, and is passed through on save`() {
        val repository = FakeWorkEntryRepository()
        val viewModel = viewModel(workEntryRepository = repository)
        viewModel.onAircraftSelectionChange(AircraftSelection.Bench)
        viewModel.onDescriptionChange("Multi-day annual")
        viewModel.onActivityTypeToggle(ActivityType.INSPECTION)
        assertTrue(viewModel.state.value.canSave) // blank override is fine

        viewModel.onDaysWorkedOverrideChange("not a number")
        assertFalse(viewModel.state.value.canSave)
        assertEquals("Enter a whole number", viewModel.state.value.daysWorkedOverrideError)

        viewModel.onDaysWorkedOverrideChange("5")
        assertTrue(viewModel.state.value.canSave)

        viewModel.save()

        assertEquals(5, repository.created.first().entry.daysWorkedOverride)
    }

    @Test
    fun `aircraftOptions reflects the aircraft repository, excluding archived by default`() {
        val aircraftRepository = FakeAircraftRepository()
        val visibleId = runBlocking {
            aircraftRepository.create(
                manufacturer = "Schleicher", type = "ASK 21", serialNumber = "1",
                propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
                subcategoryOverride = null, registration = "PH-0001", validFrom = LocalDate.of(2020, 1, 1),
            )
        }
        runBlocking {
            val archivedId = aircraftRepository.create(
                manufacturer = "Grob", type = "G103", serialNumber = "2",
                propulsion = Propulsion.UNPOWERED, structure = Structure.COMPOSITE,
                subcategoryOverride = null, registration = "PH-0002", validFrom = LocalDate.of(2020, 1, 1),
            )
            aircraftRepository.setArchived(archivedId, true)
        }

        val viewModel = viewModel(aircraftRepository = aircraftRepository)

        assertEquals(1, viewModel.aircraftOptions.value.size)
        assertEquals(visibleId, viewModel.aircraftOptions.value.first().aircraft.id)
    }

    @Test
    fun `knownHelperNames reflects the person directory`() {
        val personRepository = FakePersonRepository(initial = listOf(PersonEntity(id = "p1", name = "Jan de Vries")))

        val viewModel = viewModel(personRepository = personRepository)

        assertEquals(listOf("Jan de Vries"), viewModel.knownHelperNames.value)
    }

    @Test
    fun `isDirty is false until a field changes, and false again after saving`() {
        val viewModel = viewModel()
        assertFalse(viewModel.isDirty.value)

        viewModel.onDescriptionChange("Bench-tested altimeter")
        assertTrue(viewModel.isDirty.value)

        viewModel.onAircraftSelectionChange(AircraftSelection.Bench)
        viewModel.onActivityTypeToggle(ActivityType.TROUBLESHOOTING)
        viewModel.save()
        assertFalse(viewModel.isDirty.value)
    }

    @Test
    fun `canDelete is false for a new entry, true once an existing one has loaded`() {
        val newViewModel = viewModel()
        assertFalse(newViewModel.state.value.canDelete)

        val repository = FakeWorkEntryRepository()
        val id = runBlocking {
            repository.create(
                aircraftId = null, description = "Bench work", activityTypes = setOf(ActivityType.SERVICING),
                supervisedAnother = false, sessionDates = listOf(LocalDate.of(2026, 1, 15)),
            )
        }
        val editViewModel = viewModel(savedStateHandle = newState(id), workEntryRepository = repository)
        assertTrue(editViewModel.state.value.canDelete)
    }

    @Test
    fun `loading an existing entry populates the form from every child row`() {
        val repository = FakeWorkEntryRepository()
        val id = runBlocking {
            repository.create(
                aircraftId = null,
                description = "Annual inspection",
                explanation = "Full airframe and engine inspection per the approved maintenance programme.",
                activityTypes = setOf(ActivityType.INSPECTION, ActivityType.SERVICING),
                supervisedAnother = true,
                sessionDates = listOf(LocalDate.of(2026, 1, 15)),
                helperNames = listOf("Jan de Vries"),
                workorderIssuerName = "Piet Bakker",
                workorderReference = "WO-2026-001",
                annualInspection = true,
                concurrentWithArc = true,
                documentationRefs = listOf(DocumentationRefInput("AMM 12-34", "Rev 5")),
                partsUsed = listOf(PartUsedInput("PN-001", batchOrSerial = "SN-9")),
            )
        }

        val viewModel = viewModel(savedStateHandle = newState(id), workEntryRepository = repository)

        assertFalse(viewModel.state.value.loading)
        assertEquals("Annual inspection", viewModel.state.value.description)
        assertEquals("Full airframe and engine inspection per the approved maintenance programme.", viewModel.state.value.explanation)
        assertEquals(AircraftSelection.Bench, viewModel.state.value.aircraftSelection)
        assertEquals(setOf(ActivityType.INSPECTION, ActivityType.SERVICING), viewModel.state.value.activityTypes)
        assertEquals(listOf(LocalDate.of(2026, 1, 15)), viewModel.state.value.sessionDates)
        assertEquals(listOf("Jan de Vries"), viewModel.state.value.helperNames)
        assertEquals("Piet Bakker", viewModel.state.value.workorderIssuerName)
        assertEquals("WO-2026-001", viewModel.state.value.workorderReference)
        assertTrue(viewModel.state.value.annualInspection)
        assertTrue(viewModel.state.value.concurrentWithArc)
        assertEquals(listOf(DocumentationRefInput("AMM 12-34", "Rev 5")), viewModel.state.value.documentationRefs)
        assertEquals(listOf(PartUsedInput("PN-001", batchOrSerial = "SN-9")), viewModel.state.value.partsUsed)
        assertFalse(viewModel.isDirty.value) // freshly loaded, nothing edited yet
    }

    @Test
    fun `save updates the existing entry, rather than creating a new one, once editing`() {
        val repository = FakeWorkEntryRepository()
        val id = runBlocking {
            repository.create(
                aircraftId = null, description = "Bench work", activityTypes = setOf(ActivityType.SERVICING),
                supervisedAnother = false, sessionDates = listOf(LocalDate.of(2026, 1, 15)),
            )
        }

        val viewModel = viewModel(savedStateHandle = newState(id), workEntryRepository = repository)
        viewModel.onDescriptionChange("Bench work, corrected")

        viewModel.save()

        assertEquals(1, repository.created.size) // unchanged — save() didn't create a second entry
        assertEquals(1, repository.updated.size)
        assertEquals("Bench work, corrected", repository.updated.first().entry.description)
        assertFalse(viewModel.isDirty.value)
    }

    @Test
    fun `openDeferredItems reflects the deferred item repository`() {
        val deferredItemRepository = FakeDeferredItemRepository()
        val itemId = runBlocking { deferredItemRepository.raise("crs-1", "Transponder recal outstanding") }

        val viewModel = viewModel(deferredItemRepository = deferredItemRepository)

        assertEquals(listOf(itemId), viewModel.openDeferredItems.value.map { it.id })
    }

    @Test
    fun `selecting a deferred item closes it on save, dated to the entry's own last session`() {
        val workEntryRepository = FakeWorkEntryRepository()
        val deferredItemRepository = FakeDeferredItemRepository()
        val itemId = runBlocking { deferredItemRepository.raise("crs-1", "Transponder recal outstanding") }
        val viewModel = viewModel(workEntryRepository = workEntryRepository, deferredItemRepository = deferredItemRepository)
        viewModel.onAircraftSelectionChange(AircraftSelection.Bench)
        viewModel.onDescriptionChange("Recalibrated transponder")
        viewModel.onActivityTypeToggle(ActivityType.REPAIRING)
        viewModel.onSessionDateAdd(LocalDate.of(2026, 4, 2))
        viewModel.onClosesDeferredItemToggle(itemId)

        viewModel.save()

        assertEquals(emptyList<String>(), viewModel.openDeferredItems.value.map { it.id })
    }

    @Test
    fun `selecting several deferred items closes all of them on save`() {
        val workEntryRepository = FakeWorkEntryRepository()
        val deferredItemRepository = FakeDeferredItemRepository()
        val itemId1 = runBlocking { deferredItemRepository.raise("crs-1", "Transponder recal outstanding") }
        val itemId2 = runBlocking { deferredItemRepository.raise("crs-1", "Placard replacement outstanding") }
        val viewModel = viewModel(workEntryRepository = workEntryRepository, deferredItemRepository = deferredItemRepository)
        viewModel.onAircraftSelectionChange(AircraftSelection.Bench)
        viewModel.onDescriptionChange("Recalibrated transponder and replaced placard")
        viewModel.onActivityTypeToggle(ActivityType.REPAIRING)
        viewModel.onSessionDateAdd(LocalDate.of(2026, 4, 2))
        viewModel.onClosesDeferredItemToggle(itemId1)
        viewModel.onClosesDeferredItemToggle(itemId2)
        assertEquals(setOf(itemId1, itemId2), viewModel.state.value.closesDeferredItemIds)

        viewModel.onClosesDeferredItemToggle(itemId1) // toggling again deselects it
        assertEquals(setOf(itemId2), viewModel.state.value.closesDeferredItemIds)

        viewModel.onClosesDeferredItemToggle(itemId1) // reselect, so both close on save
        viewModel.save()

        assertEquals(emptyList<String>(), viewModel.openDeferredItems.value.map { it.id })
    }

    @Test
    fun `no deferred item is closed when none was selected`() {
        val workEntryRepository = FakeWorkEntryRepository()
        val deferredItemRepository = FakeDeferredItemRepository()
        runBlocking { deferredItemRepository.raise("crs-1", "Transponder recal outstanding") }
        val viewModel = viewModel(workEntryRepository = workEntryRepository, deferredItemRepository = deferredItemRepository)
        viewModel.onAircraftSelectionChange(AircraftSelection.Bench)
        viewModel.onDescriptionChange("Unrelated work")
        viewModel.onActivityTypeToggle(ActivityType.SERVICING)

        viewModel.save()

        assertEquals(1, viewModel.openDeferredItems.value.size)
    }

    @Test
    fun `save on a brand-new entry reveals editing in place, without navigating away`() {
        val repository = FakeWorkEntryRepository()
        val viewModel = viewModel(workEntryRepository = repository)
        viewModel.onAircraftSelectionChange(AircraftSelection.Bench)
        viewModel.onDescriptionChange("Bench work")
        viewModel.onActivityTypeToggle(ActivityType.SERVICING)

        viewModel.save()

        assertTrue("expected isEditing to flip true so the certificate section appears", viewModel.state.value.isEditing)
        assertEquals(repository.created.first().entry.id, viewModel.state.value.entryId)
        assertFalse("no navigation event exists any more — the screen just stays as it is", viewModel.isDirty.value)
    }

    @Test
    fun `re-saving an already-existing entry updates it in place and clears isDirty`() {
        val repository = FakeWorkEntryRepository()
        val id = runBlocking {
            repository.create(
                aircraftId = null, description = "Bench work", activityTypes = setOf(ActivityType.SERVICING),
                supervisedAnother = false, sessionDates = listOf(LocalDate.of(2026, 1, 15)),
            )
        }
        val viewModel = viewModel(savedStateHandle = newState(id), workEntryRepository = repository)
        viewModel.onDescriptionChange("Bench work, corrected")
        assertTrue(viewModel.isDirty.value)

        viewModel.save()

        assertEquals("Bench work, corrected", repository.updated.first().entry.description)
        assertFalse("a save should leave nothing pending — the button reads \"Saved\", not \"Save\"", viewModel.isDirty.value)
    }

    @Test
    fun `editing again after a save is recognized as dirty, so the button leaves Saved for Save`() {
        val repository = FakeWorkEntryRepository()
        val id = runBlocking {
            repository.create(
                aircraftId = null, description = "Bench work", activityTypes = setOf(ActivityType.SERVICING),
                supervisedAnother = false, sessionDates = listOf(LocalDate.of(2026, 1, 15)),
            )
        }
        val viewModel = viewModel(savedStateHandle = newState(id), workEntryRepository = repository)
        viewModel.onDescriptionChange("First correction")
        viewModel.save()
        assertFalse(viewModel.isDirty.value)

        viewModel.onDescriptionChange("Second correction, after the save completed")

        assertTrue("a further edit after a completed save must show up as dirty again", viewModel.isDirty.value)
    }

    @Test
    fun `an edit that lands while a save is still in flight is not silently treated as saved`() = runBlocking {
        val repository = FakeWorkEntryRepository()
        val id = repository.create(
            aircraftId = null, description = "Bench work", activityTypes = setOf(ActivityType.SERVICING),
            supervisedAnother = false, sessionDates = listOf(LocalDate.of(2026, 1, 15)),
        )
        val viewModel = viewModel(savedStateHandle = newState(id), workEntryRepository = repository)
        viewModel.onDescriptionChange("First correction")

        // Simulates the real race: an edit lands (onDescriptionChange, below) after save() has
        // captured its snapshot but before its suspend repository call has actually resumed —
        // performSave's own baseline must come from what it persisted, not from re-reading
        // state after the fact, or this second edit would be silently treated as already saved.
        viewModel.save()
        viewModel.onDescriptionChange("Second correction, landed mid-save")

        assertTrue("the second edit was never part of any repository call — it must still show as dirty", viewModel.isDirty.value)
        assertEquals("First correction", repository.updated.first().entry.description)
    }

    @Test
    fun `saveAndAwaitCompletion suspends until the save has actually finished`() = runBlocking {
        val repository = FakeWorkEntryRepository()
        val viewModel = viewModel(workEntryRepository = repository)
        viewModel.onAircraftSelectionChange(AircraftSelection.Bench)
        viewModel.onDescriptionChange("Bench work")
        viewModel.onActivityTypeToggle(ActivityType.SERVICING)

        viewModel.saveAndAwaitCompletion()

        // By the time this line runs (not just "eventually"), the save is done — that's the
        // whole point of this method over the fire-and-forget save().
        assertEquals(1, repository.created.size)
        assertFalse(viewModel.isDirty.value)
    }

    @Test
    fun `delete removes the entry via the repository and emits deleted`() {
        val repository = FakeWorkEntryRepository()
        val id = runBlocking {
            repository.create(
                aircraftId = null, description = "Bench work", activityTypes = setOf(ActivityType.SERVICING),
                supervisedAnother = false, sessionDates = listOf(LocalDate.of(2026, 1, 15)),
            )
        }

        val viewModel = viewModel(savedStateHandle = newState(id), workEntryRepository = repository)
        viewModel.delete()

        assertEquals(listOf(id), repository.deletedIds)
    }
}
