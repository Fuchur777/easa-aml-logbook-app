package nl.part66l.logbook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
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
class ProfileRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ProfileRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProfileRepositoryImpl(db.profile())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `get returns null before any profile is recorded`() = runBlocking {
        assertNull(repository.get())
    }

    @Test
    fun `upsert then get round-trips the profile`() = runBlocking {
        val profile = ProfileEntity(
            name = "Test Pilot",
            licenceNumber = "NL.66.00000",
            issuingAuthority = "ILT",
            licenceExpiry = null,
            holdsL1 = true,
        )

        repository.upsert(profile)

        assertEquals(profile, repository.get())
    }

    @Test
    fun `upsert replaces the single profile row rather than adding a second`() = runBlocking {
        repository.upsert(ProfileEntity(name = "First", licenceNumber = null, issuingAuthority = null, licenceExpiry = null))
        repository.upsert(ProfileEntity(name = "Second", licenceNumber = null, issuingAuthority = null, licenceExpiry = null))

        assertEquals("Second", repository.get()!!.name)
    }
}
