package nl.part66l.logbook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import nl.part66l.logbook.domain.CertificationBasis
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
class DeferredItemRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: DeferredItemRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DeferredItemRepositoryImpl(db.deferredItems())
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** A deferred item's raisedByCrsId is a real foreign key — needs an actual CrsEntity (and its own work entry) on file. */
    private suspend fun seedCrs(id: String = "crs-1"): String {
        db.workEntries().insert(
            WorkEntryEntity(
                id = "e1", aircraftId = null, description = "Annual inspection",
                role = EntryRole.CERTIFIED_BY_ME_IN_APP, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
            ),
        )
        db.crs().insert(
            CrsEntity(
                id = id, entryId = "e1", number = "CRS-2026-0001", numberNormalised = "CRS20260001",
                sequence = 1, year = 2026, basis = CertificationBasis.ML_A_801_B2_INDEPENDENT, statementVersion = "v1",
                completionDate = LocalDate.of(2026, 3, 14), signatureState = SignatureState.ISSUED_UNSIGNED_PRINT, snapshotJson = "{}",
            ),
        )
        return id
    }

    @Test
    fun `raise creates an open item against the given CRS`() = runBlocking {
        val crsId = seedCrs()

        val id = repository.raise(crsId, "Transponder recal outstanding")

        val open = repository.open().first()
        assertEquals(1, open.size)
        assertEquals(id, open.first().id)
        assertEquals(crsId, open.first().raisedByCrsId)
        assertEquals("Transponder recal outstanding", open.first().description)
        assertFalse(open.first().closed)
    }

    @Test
    fun `close marks the item closed and removes it from open, but is a no-op once already closed`() = runBlocking {
        val crsId = seedCrs()
        val id = repository.raise(crsId, "Transponder recal outstanding")

        val firstClose = repository.close(id, closedByEntryId = "e2", closedDate = LocalDate.of(2026, 4, 2))
        assertTrue(firstClose)
        assertEquals(emptyList<DeferredItemEntity>(), repository.open().first())

        val secondClose = repository.close(id, closedByEntryId = "e3", closedDate = LocalDate.of(2026, 5, 1))
        assertFalse(secondClose)
    }

    @Test
    fun `close returns false for an unknown id`() = runBlocking {
        assertFalse(repository.close("does-not-exist", closedByEntryId = "e2", closedDate = LocalDate.of(2026, 4, 2)))
    }
}
