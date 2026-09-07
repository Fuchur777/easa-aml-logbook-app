package nl.part66l.logbook.fakes

import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import nl.part66l.logbook.data.AircraftEntity
import nl.part66l.logbook.data.AircraftRepository
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.Structure
import nl.part66l.logbook.domain.Subcategory

/** In-memory stand-in for ViewModel tests — no Room, no Robolectric. */
class FakeAircraftRepository : AircraftRepository {
    private val aircraft = MutableStateFlow<List<AircraftEntity>>(emptyList())
    private val registrations = mutableMapOf<String, String>()

    override fun all(): Flow<List<AircraftEntity>> = aircraft

    override suspend fun create(
        manufacturer: String,
        type: String,
        serialNumber: String,
        propulsion: Propulsion,
        structure: Structure,
        subcategoryOverride: Subcategory?,
        registration: String,
        validFrom: LocalDate,
    ): String {
        val id = UUID.randomUUID().toString()
        aircraft.value = aircraft.value + AircraftEntity(
            id = id, manufacturer = manufacturer, type = type, serialNumber = serialNumber,
            propulsion = propulsion, structure = structure, subcategoryOverride = subcategoryOverride,
        )
        registrations[id] = registration
        return id
    }

    override suspend fun currentRegistration(aircraftId: String, on: LocalDate): String? = registrations[aircraftId]
}
