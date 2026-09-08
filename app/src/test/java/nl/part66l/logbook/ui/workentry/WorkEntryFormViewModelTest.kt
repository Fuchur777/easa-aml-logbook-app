package nl.part66l.logbook.ui.workentry

import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import nl.part66l.logbook.data.CatalogueTaskEntity
import nl.part66l.logbook.data.DocumentEntity
import nl.part66l.logbook.data.DocumentationRefInput
import nl.part66l.logbook.data.PartUsedInput
import nl.part66l.logbook.data.PersonEntity
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.DocumentCategory
import nl.part66l.logbook.domain.EntryRole
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.Structure
import nl.part66l.logbook.fakes.FakeAircraftRepository
import nl.part66l.logbook.fakes.FakeCatalogueRepository
import nl.part66l.logbook.fakes.FakeDocumentRepository
import nl.part66l.logbook.fakes.FakePersonRepository
import nl.part66l.logbook.fakes.FakeSettingsRepository
import nl.part66l.logbook.fakes.FakeWorkEntryRepository
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

    private fun viewModel(
        workEntryRepository: FakeWorkEntryRepository = FakeWorkEntryRepository(),
        aircraftRepository: FakeAircraftRepository = FakeAircraftRepository(),
        personRepository: FakePersonRepository = FakePersonRepository(),
        catalogueRepository: FakeCatalogueRepository = FakeCatalogueRepository(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
        documentRepository: FakeDocumentRepository = FakeDocumentRepository(),
    ) = WorkEntryFormViewModel(workEntryRepository, aircraftRepository, personRepository, catalogueRepository, settingsRepository, documentRepository)

    @Test
    fun `canSave requires a description, an aircraft selection and at least one activity, and defaults to no release claimed`() {
        val viewModel = viewModel()

        assertFalse(viewModel.state.value.canSave)
        assertEquals(AircraftSelection.Unselected, viewModel.state.value.aircraftSelection)
        assertEquals(EntryRole.NO_RELEASE, viewModel.state.value.role)
        assertFalse(viewModel.state.value.supervisedAnother)

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
    fun `save creates the entry with the entered activities, role, supervision flag and helpers`() {
        val repository = FakeWorkEntryRepository()
        val viewModel = viewModel(workEntryRepository = repository)
        viewModel.onAircraftSelectionChange(AircraftSelection.Bench)
        viewModel.onDescriptionChange("Replaced altimeter seal")
        viewModel.onActivityTypeToggle(ActivityType.REPAIRING)
        viewModel.onActivityTypeToggle(ActivityType.INSPECTION)
        viewModel.onRoleChange(EntryRole.CERTIFIED_BY_ME_IN_APP)
        viewModel.onSupervisedAnotherChange(true)
        viewModel.onSessionDateChange(LocalDate.of(2026, 3, 4))
        viewModel.onHelperAdd("Jan de Vries")

        viewModel.save()

        assertEquals(1, repository.created.size)
        val created = repository.created.first()
        assertEquals("Replaced altimeter seal", created.entry.description)
        assertNull(created.entry.aircraftId)
        assertEquals(setOf(ActivityType.REPAIRING, ActivityType.INSPECTION), created.activityTypes)
        assertEquals(EntryRole.CERTIFIED_BY_ME_IN_APP, created.entry.role)
        assertTrue(created.entry.supervisedAnother)
        assertEquals(listOf("Jan de Vries"), created.helperNames)
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

        viewModel.onCreateDocument("AMM", DocumentCategory.MANUAL, "Rev 1", "https://example.com")

        assertEquals(1, viewModel.documentOptions.value.size)
        val created = viewModel.documentOptions.value.first()
        assertEquals("AMM", created.name)
        assertEquals(DocumentCategory.MANUAL, created.category)
        assertEquals("Rev 1", created.revision)
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
        assertFalse(viewModel.isDirty())

        viewModel.onDescriptionChange("Bench-tested altimeter")
        assertTrue(viewModel.isDirty())

        viewModel.onAircraftSelectionChange(AircraftSelection.Bench)
        viewModel.onActivityTypeToggle(ActivityType.TROUBLESHOOTING)
        viewModel.save()
        assertFalse(viewModel.isDirty())
    }
}
