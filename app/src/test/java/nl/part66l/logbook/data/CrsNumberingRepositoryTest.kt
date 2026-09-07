package nl.part66l.logbook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.CertificationBasis
import nl.part66l.logbook.domain.CrsNumberFormat
import nl.part66l.logbook.domain.EntryRole
import nl.part66l.logbook.domain.SignatureState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrsNumberingRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: CrsNumberingRepository
    private val format = CrsNumberFormat(template = "{PREFIX}-{YYYY}-{SEQ:4}", prefix = "CRS", annualReset = true)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CrsNumberingRepositoryImpl(db.crs())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun crs(number: String, sequence: Int) = CrsEntity(
        id = number,
        entryId = "e1",
        number = number,
        numberNormalised = Identifiers.normalise(number),
        sequence = sequence,
        year = 2026,
        basis = CertificationBasis.ML_A_801_B2_INDEPENDENT,
        statementVersion = "v1",
        completionDate = LocalDate.of(2026, 3, 14),
        signatureState = SignatureState.SIGNED_LOCAL,
        snapshotJson = "{}",
    )

    private suspend fun seedEntry() = db.workEntries().insert(
        WorkEntryEntity(
            id = "e1", aircraftId = null, description = "Annual inspection",
            activityType = ActivityType.INSPECTION, role = EntryRole.CERTIFIED_BY_ME_IN_APP,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
        ),
    )

    @Test
    fun `nextNumber starts at the format's startAt when nothing has been issued`() = runBlocking {
        assertEquals("CRS-2026-0001", repository.nextNumber(format, year = 2026))
    }

    @Test
    fun `nextNumber increments past what's actually stored`() = runBlocking {
        seedEntry()
        db.crs().insert(crs("CRS-2026-0001", 1))
        db.crs().insert(crs("CRS-2026-0002", 2))

        assertEquals("CRS-2026-0003", repository.nextNumber(format, year = 2026))
    }

    @Test
    fun `collides is true for an issued number and false for an unissued one`() = runBlocking {
        seedEntry()
        db.crs().insert(crs("CRS-2026-0001", 1))

        assertTrue(repository.collides(format, "CRS-2026-0001"))
        assertFalse(repository.collides(format, "CRS-2026-0002"))
    }
}
