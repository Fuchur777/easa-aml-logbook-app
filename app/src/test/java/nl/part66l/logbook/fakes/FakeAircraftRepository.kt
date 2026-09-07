package nl.part66l.logbook.fakes

import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import nl.part66l.logbook.data.AircraftEntity
import nl.part66l.logbook.data.AircraftRepository
import nl.part66l.logbook.data.AircraftWithRegistration
import nl.part66l.logbook.data.Identifiers
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.Structure
import nl.part66l.logbook.domain.Subcategory

private data class FakeRegistration(val text: String, val normalised: String, val validFrom: LocalDate, var validTo: LocalDate?)

/** In-memory stand-in for ViewModel tests — no Room, no Robolectric. */
class FakeAircraftRepository : AircraftRepository {
    private val aircraft = MutableStateFlow<List<AircraftEntity>>(emptyList())
    private val registrations = mutableMapOf<String, MutableList<FakeRegistration>>()
    val workHistory = mutableSetOf<String>()

    override fun all(): Flow<List<AircraftEntity>> = aircraft

    override fun observeAll(includeArchived: Boolean): Flow<List<AircraftEntity>> =
        aircraft.map { list -> list.filter { includeArchived || !it.archived }.sortedBy { it.sortOrder } }

    override fun observeAllWithRegistration(includeArchived: Boolean): Flow<List<AircraftWithRegistration>> =
        aircraft.map { list ->
            list.filter { includeArchived || !it.archived }.sortedBy { it.sortOrder }.map {
                AircraftWithRegistration(it, registrations[it.id]?.find { r -> r.validTo == null }?.text)
            }
        }

    override suspend fun byId(id: String): AircraftEntity? = aircraft.value.find { it.id == id }

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
        // Registrations first: the aircraft.value write below triggers a reactive
        // re-read of `registrations` in observeAllWithRegistration, so it must
        // already hold this aircraft's entry by the time that happens.
        registrations[id] = mutableListOf(
            FakeRegistration(registration, Identifiers.normalise(registration), validFrom, null),
        )
        aircraft.value = aircraft.value + AircraftEntity(
            id = id, manufacturer = manufacturer, type = type, serialNumber = serialNumber,
            propulsion = propulsion, structure = structure, subcategoryOverride = subcategoryOverride,
            sortOrder = aircraft.value.size,
        )
        return id
    }

    override suspend fun update(
        id: String,
        manufacturer: String,
        type: String,
        serialNumber: String,
        propulsion: Propulsion,
        structure: Structure,
        subcategoryOverride: Subcategory?,
        registration: String,
        registrationValidFrom: LocalDate,
    ) {
        // Registrations first — see the comment in create() for why.
        val normalisedNew = Identifiers.normalise(registration)
        val history = registrations.getOrPut(id) { mutableListOf() }
        val current = history.find { it.validTo == null }
        if (current == null || current.normalised != normalisedNew) {
            current?.validTo = registrationValidFrom.minusDays(1)
            history += FakeRegistration(registration, normalisedNew, registrationValidFrom, null)
        }

        aircraft.value = aircraft.value.map {
            if (it.id == id) {
                it.copy(
                    manufacturer = manufacturer, type = type, serialNumber = serialNumber,
                    propulsion = propulsion, structure = structure, subcategoryOverride = subcategoryOverride,
                )
            } else {
                it
            }
        }
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        aircraft.value = aircraft.value.map { if (it.id == id) it.copy(archived = archived) else it }
    }

    override suspend fun hasWorkHistory(id: String): Boolean = id in workHistory

    override suspend fun delete(id: String) {
        aircraft.value = aircraft.value.filterNot { it.id == id }
        registrations.remove(id)
    }

    override suspend fun reorder(orderedIds: List<String>) {
        val order = orderedIds.withIndex().associate { (index, id) -> id to index }
        aircraft.value = aircraft.value.map { it.copy(sortOrder = order[it.id] ?: it.sortOrder) }
    }

    override suspend fun currentRegistration(aircraftId: String, on: LocalDate): String? =
        registrations[aircraftId]?.find { it.validFrom <= on && (it.validTo == null || it.validTo!! >= on) }?.text
}
