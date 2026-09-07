package nl.part66l.logbook.ui.aircraft

import androidx.lifecycle.SavedStateHandle
import java.time.LocalDate
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
import nl.part66l.logbook.ui.navigation.Destination
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

    private fun newState(aircraftId: String? = null) = SavedStateHandle(
        aircraftId?.let { mapOf(Destination.AircraftEdit.ARG_AIRCRAFT_ID to it) } ?: emptyMap(),
    )

    @Test
    fun `canSave requires manufacturer, type, serial number and registration`() {
        val viewModel = AircraftFormViewModel(newState(), FakeAircraftRepository())

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
        val viewModel = AircraftFormViewModel(newState(), FakeAircraftRepository())
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
        val viewModel = AircraftFormViewModel(newState(), FakeAircraftRepository())
        viewModel.onStructureChange(Structure.MIXED)
        viewModel.onSubcategoryOverrideChange(Subcategory.L2C)

        viewModel.onStructureChange(Structure.COMPOSITE)

        assertNull(viewModel.state.value.subcategoryOverride)
    }

    @Test
    fun `save creates the aircraft via the repository`() {
        val repository = FakeAircraftRepository()
        val viewModel = AircraftFormViewModel(newState(), repository)
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

    @Test
    fun `editing an existing aircraft loads its current fields and registration`() {
        val repository = FakeAircraftRepository()
        val id = runBlocking {
            repository.create(
                manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
                propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
                subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
            )
        }

        val viewModel = AircraftFormViewModel(newState(id), repository)

        assertEquals("Schleicher", viewModel.state.value.manufacturer)
        assertEquals("PH-1234", viewModel.state.value.registration)
        assertTrue(viewModel.state.value.isEditing)
    }

    @Test
    fun `saving an edit with an unchanged registration does not create a new registration record`() {
        val repository = FakeAircraftRepository()
        val id = runBlocking {
            repository.create(
                manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
                propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
                subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
            )
        }
        val viewModel = AircraftFormViewModel(newState(id), repository)

        viewModel.onManufacturerChange("Schleicher (updated)")
        viewModel.save()

        val stored = runBlocking { repository.byId(id) }
        assertEquals("Schleicher (updated)", stored?.manufacturer)
        assertEquals("PH-1234", runBlocking { repository.currentRegistration(id, LocalDate.now()) })
    }

    @Test
    fun `saving an edit with a changed registration preserves history for old CRS`() {
        val repository = FakeAircraftRepository()
        val id = runBlocking {
            repository.create(
                manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
                propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
                subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
            )
        }
        val viewModel = AircraftFormViewModel(newState(id), repository)

        viewModel.onRegistrationChange("PH-9999")
        viewModel.onValidFromChange(LocalDate.of(2026, 6, 1))
        viewModel.save()

        assertEquals("PH-1234", runBlocking { repository.currentRegistration(id, LocalDate.of(2026, 5, 31)) })
        assertEquals("PH-9999", runBlocking { repository.currentRegistration(id, LocalDate.of(2026, 6, 1)) })
    }

    @Test
    fun `an aircraft with work history cannot be deleted`() {
        val repository = FakeAircraftRepository()
        val id = runBlocking {
            repository.create(
                manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
                propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
                subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
            )
        }
        repository.workHistory += id
        val viewModel = AircraftFormViewModel(newState(id), repository)

        assertTrue(viewModel.state.value.hasWorkHistory)
        assertFalse(viewModel.state.value.canDelete)
    }

    @Test
    fun `deleting an aircraft with no work history removes it`() {
        val repository = FakeAircraftRepository()
        val id = runBlocking {
            repository.create(
                manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
                propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
                subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
            )
        }
        val viewModel = AircraftFormViewModel(newState(id), repository)

        assertTrue(viewModel.state.value.canDelete)
        viewModel.delete()

        assertNull(runBlocking { repository.byId(id) })
    }

    @Test
    fun `isDirty is false for a fresh add-aircraft form until a field changes`() {
        val viewModel = AircraftFormViewModel(newState(), FakeAircraftRepository())
        assertFalse(viewModel.isDirty())

        viewModel.onManufacturerChange("Schleicher")

        assertTrue(viewModel.isDirty())
    }

    @Test
    fun `isDirty compares against the loaded aircraft, and archiving does not count as a pending edit`() {
        val repository = FakeAircraftRepository()
        val id = runBlocking {
            repository.create(
                manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
                propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
                subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
            )
        }
        val viewModel = AircraftFormViewModel(newState(id), repository)

        assertFalse(viewModel.isDirty())

        viewModel.onArchiveToggle()
        assertFalse(viewModel.isDirty())

        viewModel.onTypeChange("ASK 21 (updated)")
        assertTrue(viewModel.isDirty())

        viewModel.save()
        assertFalse(viewModel.isDirty())
    }

    @Test
    fun `archive toggle flips the archived flag`() {
        val repository = FakeAircraftRepository()
        val id = runBlocking {
            repository.create(
                manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
                propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
                subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
            )
        }
        val viewModel = AircraftFormViewModel(newState(id), repository)

        viewModel.onArchiveToggle()

        assertTrue(viewModel.state.value.archived)
        assertTrue(runBlocking { repository.byId(id) }?.archived == true)
    }
}
