package nl.part66l.logbook.ui.aircraft

import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.Structure
import nl.part66l.logbook.fakes.FakeAircraftRepository
import nl.part66l.logbook.fakes.FakeSettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AircraftListViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun create(repository: FakeAircraftRepository, manufacturer: String, archived: Boolean = false) = runBlocking {
        val id = repository.create(
            manufacturer = manufacturer, type = "ASK 21", serialNumber = "21123",
            propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
            subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
        )
        if (archived) repository.setArchived(id, true)
        id
    }

    @Test
    fun `reflects aircraft already present, and new ones created afterward`() {
        val repository = FakeAircraftRepository()
        val viewModel = AircraftListViewModel(repository, FakeSettingsRepository())

        assertEquals(0, viewModel.aircraft.value.size)

        create(repository, "Schleicher")

        assertEquals(1, viewModel.aircraft.value.size)
        assertEquals("Schleicher", viewModel.aircraft.value.first().aircraft.manufacturer)
        assertEquals("PH-1234", viewModel.aircraft.value.first().registration)
    }

    @Test
    fun `archived aircraft are hidden unless the setting is on`() {
        val repository = FakeAircraftRepository()
        val settings = FakeSettingsRepository(initialShowArchivedAircraft = false)
        val viewModel = AircraftListViewModel(repository, settings)

        create(repository, "Visible")
        create(repository, "Hidden", archived = true)

        assertEquals(1, viewModel.aircraft.value.size)
        assertEquals("Visible", viewModel.aircraft.value.first().aircraft.manufacturer)

        runBlocking { settings.setShowArchivedAircraft(true) }

        assertEquals(2, viewModel.aircraft.value.size)
        assertTrue(viewModel.aircraft.value.any { it.aircraft.manufacturer == "Hidden" })
    }

    @Test
    fun `the archived toggle on the list drives the same persisted setting`() {
        val repository = FakeAircraftRepository()
        val settings = FakeSettingsRepository()
        val viewModel = AircraftListViewModel(repository, settings)
        create(repository, "Visible")
        create(repository, "Hidden", archived = true)

        assertEquals(1, viewModel.aircraft.value.size)

        viewModel.onShowArchivedChange(true)

        assertTrue(viewModel.showArchived.value)
        assertEquals(2, viewModel.aircraft.value.size)
    }

    @Test
    fun `reorder persists the new order`() {
        val repository = FakeAircraftRepository()
        val viewModel = AircraftListViewModel(repository, FakeSettingsRepository())
        val first = create(repository, "First")
        val second = create(repository, "Second")

        viewModel.reorder(listOf(second, first))

        assertEquals(listOf("Second", "First"), viewModel.aircraft.value.map { it.aircraft.manufacturer })
    }
}
