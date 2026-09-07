package nl.part66l.logbook.data

import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.Structure
import nl.part66l.logbook.domain.Subcategory

interface AircraftRepository {
    /** Every aircraft, archived or not — for contexts (like this repository's own tests) that don't care about the list-screen filter. */
    fun all(): Flow<List<AircraftEntity>>

    /** Ordered for display; [includeArchived] backs the "show archived aircraft" setting. */
    fun observeAll(includeArchived: Boolean): Flow<List<AircraftEntity>>

    /** Same ordering and filter as [observeAll], joined with each aircraft's current registration — what the list screen renders. */
    fun observeAllWithRegistration(includeArchived: Boolean): Flow<List<AircraftWithRegistration>>

    suspend fun byId(id: String): AircraftEntity?

    /** Creates an aircraft with its first registration together — an aircraft with no registration has no tail number to show. */
    suspend fun create(
        manufacturer: String,
        type: String,
        serialNumber: String,
        propulsion: Propulsion,
        structure: Structure,
        subcategoryOverride: Subcategory?,
        registration: String,
        validFrom: LocalDate,
    ): String

    /**
     * Updates the aircraft's own attributes in place. The registration is different: if
     * [registration] differs from what's currently on file, the existing registration row is
     * closed out (its validTo set to the day before) and a new one is inserted from
     * [registrationValidFrom] — a genuine re-registration, not a correction, so old CRS
     * documents keep printing what was actually valid when they were signed. If the text is
     * unchanged, the registration history is left untouched.
     */
    suspend fun update(
        id: String,
        manufacturer: String,
        type: String,
        serialNumber: String,
        propulsion: Propulsion,
        structure: Structure,
        subcategoryOverride: Subcategory?,
        registration: String,
        registrationValidFrom: LocalDate,
    )

    suspend fun setArchived(id: String, archived: Boolean)

    /** Whether any work entry references this aircraft — the database itself also refuses the delete (FK RESTRICT), this is the UI-side check to explain why upfront. */
    suspend fun hasWorkHistory(id: String): Boolean

    suspend fun delete(id: String)

    /** Persists a full manual re-ordering — [orderedIds] is the complete, new top-to-bottom order. */
    suspend fun reorder(orderedIds: List<String>)

    suspend fun currentRegistration(aircraftId: String, on: LocalDate): String?
}

@Singleton
class AircraftRepositoryImpl @Inject constructor(
    private val aircraftDao: AircraftDao,
) : AircraftRepository {

    override fun all(): Flow<List<AircraftEntity>> = aircraftDao.observeAll(includeArchived = true)

    override fun observeAll(includeArchived: Boolean): Flow<List<AircraftEntity>> =
        aircraftDao.observeAll(includeArchived)

    override fun observeAllWithRegistration(includeArchived: Boolean): Flow<List<AircraftWithRegistration>> =
        aircraftDao.observeAllWithRegistration(includeArchived)

    override suspend fun byId(id: String): AircraftEntity? = aircraftDao.byId(id)

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
        val aircraftId = UUID.randomUUID().toString()
        aircraftDao.insert(
            AircraftEntity(
                id = aircraftId,
                manufacturer = manufacturer,
                type = type,
                serialNumber = serialNumber,
                propulsion = propulsion,
                structure = structure,
                subcategoryOverride = subcategoryOverride,
                sortOrder = aircraftDao.maxSortOrder() + 1,
            ),
        )
        aircraftDao.insertRegistration(
            AircraftRegistrationEntity(
                id = UUID.randomUUID().toString(),
                aircraftId = aircraftId,
                registration = registration,
                registrationNormalised = Identifiers.normalise(registration),
                validFrom = validFrom,
                validTo = null,
            ),
        )
        return aircraftId
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
        val existing = aircraftDao.byId(id) ?: return
        aircraftDao.update(
            existing.copy(
                manufacturer = manufacturer,
                type = type,
                serialNumber = serialNumber,
                propulsion = propulsion,
                structure = structure,
                subcategoryOverride = subcategoryOverride,
            ),
        )

        val normalisedNew = Identifiers.normalise(registration)
        val current = aircraftDao.currentRegistrationRow(id)
        if (current != null && current.registrationNormalised == normalisedNew) return

        current?.let { aircraftDao.closeRegistration(it.id, validTo = registrationValidFrom.minusDays(1)) }
        aircraftDao.insertRegistration(
            AircraftRegistrationEntity(
                id = UUID.randomUUID().toString(),
                aircraftId = id,
                registration = registration,
                registrationNormalised = normalisedNew,
                validFrom = registrationValidFrom,
                validTo = null,
            ),
        )
    }

    override suspend fun setArchived(id: String, archived: Boolean) = aircraftDao.setArchived(id, archived)

    override suspend fun hasWorkHistory(id: String): Boolean = aircraftDao.hasWorkHistory(id)

    override suspend fun delete(id: String) = aircraftDao.delete(id)

    override suspend fun reorder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> aircraftDao.updateSortOrder(id, index) }
    }

    override suspend fun currentRegistration(aircraftId: String, on: LocalDate): String? =
        aircraftDao.registrationOn(aircraftId, on)
}
