package nl.part66l.logbook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogueRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: CatalogueRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CatalogueRepositoryImpl(db.catalogue(), db.profile())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun profile(holdsL1: Boolean = false, holdsL2: Boolean = false) = ProfileEntity(
        name = "Test Pilot", licenceNumber = "L-123", issuingAuthority = "ILT", licenceExpiry = null,
        holdsL1 = holdsL1, holdsL2 = holdsL2,
    )

    private fun task(id: String, l1: Boolean, l2: Boolean) = CatalogueTaskEntity(
        id = id, catalogueVersion = "2026.1", table = "B", section = "General activities", sectionCode = "GEN",
        text = "Task $id", reference = "ref", appliesToL1 = l1, appliesToL1C = false, appliesToL2 = l2, appliesToL2C = false,
    )

    @Test
    fun `returns only tasks applicable to the profile's held subcategories`() = runBlocking {
        db.profile().upsert(profile(holdsL1 = true))
        db.catalogue().upsertAll(listOf(
            task("T1", l1 = true, l2 = false),
            task("T2", l1 = false, l2 = true),
        ))

        val applicable = repository.applicableTasksForProfile()

        assertEquals(listOf("T1"), applicable.map { it.id })
    }

    @Test
    fun `returns nothing when there is no profile`() = runBlocking {
        db.catalogue().upsertAll(listOf(task("T1", l1 = true, l2 = false)))

        assertTrue(repository.applicableTasksForProfile().isEmpty())
    }

    @Test
    fun `returns nothing when no catalogue has been seeded`() = runBlocking {
        db.profile().upsert(profile(holdsL1 = true))

        assertTrue(repository.applicableTasksForProfile().isEmpty())
    }
}
