package nl.part66l.logbook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.Structure
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AircraftRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: AircraftRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AircraftRepositoryImpl(db.aircraft())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `create inserts the aircraft and its first registration together`() = runBlocking {
        val id = repository.create(
            manufacturer = "Schleicher",
            type = "ASK 21",
            serialNumber = "21123",
            propulsion = Propulsion.UNPOWERED,
            structure = Structure.WOOD_AND_FABRIC,
            subcategoryOverride = null,
            registration = "PH-1234",
            validFrom = LocalDate.of(2020, 1, 1),
        )

        val all = repository.all().first()
        assertEquals(1, all.size)
        assertEquals(id, all.first().id)
        assertEquals("PH-1234", repository.currentRegistration(id, on = LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun `currentRegistration is null before that date`() = runBlocking {
        val id = repository.create(
            manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
            propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
            subcategoryOverride = null, registration = "PH-1234", validFrom = LocalDate.of(2020, 1, 1),
        )

        assertNull(repository.currentRegistration(id, on = LocalDate.of(2019, 1, 1)))
    }
}
