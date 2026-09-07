package nl.part66l.logbook.ui.aircraft

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.Structure
import nl.part66l.logbook.domain.Subcategory
import nl.part66l.logbook.fakes.FakeAircraftRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AircraftFormViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `canSave requires manufacturer, type, serial number and registration`() {
        val viewModel = AircraftFormViewModel(FakeAircraftRepository())

        assertFalse(viewModel.state.value.canSave)

        viewModel.onManufacturerChange("Schleicher")
        viewModel.onTypeChange("ASK 21")
        viewModel.onSerialNumberChange("21123")
        assertFalse(viewModel.state.value.canSave) // no registration yet

        viewModel.onRegistrationChange("PH-1234")
        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun `mixed construction requires a manually chosen subcategory before it can save`() {
        val viewModel = AircraftFormViewModel(FakeAircraftRepository())
        viewModel.onManufacturerChange("Custom")
        viewModel.onTypeChange("One-off")
        viewModel.onSerialNumberChange("1")
        viewModel.onRegistrationChange("PH-0001")
        viewModel.onStructureChange(Structure.MIXED)

        assertFalse(viewModel.state.value.canSave)

        viewModel.onSubcategoryOverrideChange(Subcategory.L1)

        assertTrue(viewModel.state.value.canSave)
    }

    @Test
    fun `changing structure away from MIXED clears any chosen override`() {
        val viewModel = AircraftFormViewModel(FakeAircraftRepository())
        viewModel.onStructureChange(Structure.MIXED)
        viewModel.onSubcategoryOverrideChange(Subcategory.L2C)

        viewModel.onStructureChange(Structure.COMPOSITE)

        assertNull(viewModel.state.value.subcategoryOverride)
    }

    @Test
    fun `save creates the aircraft via the repository`() {
        val repository = FakeAircraftRepository()
        val viewModel = AircraftFormViewModel(repository)
        viewModel.onManufacturerChange("Schleicher")
        viewModel.onTypeChange("ASK 21")
        viewModel.onSerialNumberChange("21123")
        viewModel.onPropulsionChange(Propulsion.UNPOWERED)
        viewModel.onRegistrationChange("PH-1234")

        viewModel.save()

        val stored = runBlocking { repository.all().first() }
        assertEquals(1, stored.size)
        assertEquals("Schleicher", stored.first().manufacturer)
    }
}
