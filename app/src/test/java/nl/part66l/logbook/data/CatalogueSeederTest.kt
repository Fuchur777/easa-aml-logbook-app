package nl.part66l.logbook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import nl.part66l.logbook.domain.RuleStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogueSeederTest {

    private lateinit var db: AppDatabase
    private lateinit var seeder: CatalogueSeeder

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seeder = CatalogueSeeder(ApplicationProvider.getApplicationContext(), db.catalogue())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `seeds the real bundled catalogue asset on first run`() = runBlocking {
        seeder.seedIfNeeded()

        // 102 tasks total, per spec §6's stated denominators (L1 49, L1C 47, L2 97, L2C 95).
        assertEquals(102, db.catalogue().count())
    }

    @Test
    fun `a seeded row carries the catalogue's IN_FORCE status`() = runBlocking {
        seeder.seedIfNeeded()

        val tasks = db.catalogue().applicableTasks(version = "2026.1", l1 = true, l1c = true, l2 = true, l2c = true)
        assertEquals(true, tasks.isNotEmpty())
        assertEquals(true, tasks.all { it.status == RuleStatus.IN_FORCE })
    }

    @Test
    fun `does nothing on a later run once the catalogue is already populated`() = runBlocking {
        // A sentinel row inserted directly, bypassing the seeder — if seedIfNeeded
        // reseeds anyway, this would be replaced/joined by the real 102-row asset.
        db.catalogue().upsertAll(listOf(
            CatalogueTaskEntity(
                id = "SENTINEL", catalogueVersion = "0.0", table = "B", section = "x", sectionCode = "X",
                text = "sentinel", reference = "sentinel",
                appliesToL1 = false, appliesToL1C = false, appliesToL2 = false, appliesToL2C = false,
            ),
        ))

        seeder.seedIfNeeded()

        assertEquals(1, db.catalogue().count())
    }
}
