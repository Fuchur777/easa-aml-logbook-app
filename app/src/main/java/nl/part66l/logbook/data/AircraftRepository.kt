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
    fun all(): Flow<List<AircraftEntity>>

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

    suspend fun currentRegistration(aircraftId: String, on: LocalDate): String?
}

@Singleton
class AircraftRepositoryImpl @Inject constructor(
    private val aircraftDao: AircraftDao,
) : AircraftRepository {

    override fun all(): Flow<List<AircraftEntity>> = aircraftDao.all()

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

    override suspend fun currentRegistration(aircraftId: String, on: LocalDate): String? =
        aircraftDao.registrationOn(aircraftId, on)
}
