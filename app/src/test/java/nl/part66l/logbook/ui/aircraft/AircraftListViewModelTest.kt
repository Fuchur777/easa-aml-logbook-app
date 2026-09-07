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
import org.junit.After
import org.junit.Assert.assertEquals
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

    @Test
    fun `reflects aircraft already present, and new ones created afterward`() {
        val repository = FakeAircraftRepository()
        val viewModel = AircraftListViewModel(repository)

        assertEquals(0, viewModel.aircraft.value.size)

        runBlocking {
            repository.create(
                manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
                propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
                subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
            )
        }

        assertEquals(1, viewModel.aircraft.value.size)
        assertEquals("Schleicher", viewModel.aircraft.value.first().manufacturer)
    }
}
