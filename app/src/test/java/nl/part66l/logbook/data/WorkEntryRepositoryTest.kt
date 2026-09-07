package nl.part66l.logbook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.EntryRole
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `pagedAll`/`filtered` aren't exercised here — they're thin passthroughs to
 * WorkEntryDao's already-tested queries, and testing PagingSource output
 * meaningfully needs androidx.paging:paging-testing, which belongs with the
 * actual list screen (a later milestone), not this scaffolding pass.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkEntryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: WorkEntryRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkEntryRepositoryImpl(db.workEntries(), db.workSessions())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `create inserts the entry and its first session together`() = runBlocking {
        val id = repository.create(
            aircraftId = null,
            description = "Bench work on a spare altimeter",
            activityType = ActivityType.TROUBLESHOOTING,
            role = EntryRole.CERTIFIED_BY_ME_IN_APP,
            sessionDate = LocalDate.of(2026, 1, 15),
        )

        val entry = db.workEntries().byId(id)
        assertNotNull(entry)
        assertEquals("Bench work on a spare altimeter", entry!!.description)

        val sessions = db.workSessions().forEntry(id)
        assertEquals(1, sessions.size)
        assertEquals(LocalDate.of(2026, 1, 15), sessions.first().date)
    }
}
