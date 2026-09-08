package nl.part66l.logbook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import nl.part66l.logbook.domain.CertificationBasis
import nl.part66l.logbook.domain.EntryRole
import nl.part66l.logbook.domain.SignatureState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CLAUDE.md invariant 1: a signed CRS's certified content is immutable.
 * `transitionUnsigned` is the only UPDATE the DAO layer exposes on the certified
 * fields, and it must be a no-op once the row has left DRAFT/TIMESTAMP_PENDING —
 * enforced here at the query level, not just by the absence of any other update
 * method. `setSignedPhoto` is the one deliberate exception: the hand-signed photo
 * is metadata about the print-and-wet-sign record, not certified content, so it's
 * settable regardless of signature state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrsDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun crs(id: String, number: String, sequence: Int, state: SignatureState) = CrsEntity(
        id = id,
        entryId = "e1",
        number = number,
        numberNormalised = Identifiers.normalise(number),
        sequence = sequence,
        year = 2026,
        basis = CertificationBasis.ML_A_801_B2_INDEPENDENT,
        statementVersion = "v1",
        completionDate = LocalDate.of(2026, 3, 14),
        signatureState = state,
        snapshotJson = "{}",
    )

    private fun seedEntry() = runBlocking {
        db.workEntries().insert(
            WorkEntryEntity(
                id = "e1",
                aircraftId = null,
                description = "Annual inspection",
                role = EntryRole.CERTIFIED_BY_ME_IN_APP,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            ),
        )
    }

    @Test
    fun `transitionUnsigned moves a draft to signed and reports one row changed`() = runBlocking {
        seedEntry()
        db.crs().insert(crs("c1", "CRS-2026-0001", 1, SignatureState.DRAFT))

        val changed = db.crs().transitionUnsigned("c1", SignatureState.SIGNED_LOCAL, reason = null)

        assertEquals(1, changed)
        assertEquals(SignatureState.SIGNED_LOCAL, db.crs().byId("c1")!!.signatureState)
    }

    @Test
    fun `transitionUnsigned is a no-op once the CRS is signed`() = runBlocking {
        seedEntry()
        db.crs().insert(crs("c1", "CRS-2026-0001", 1, SignatureState.SIGNED_LOCAL))

        val changed = db.crs().transitionUnsigned("c1", SignatureState.VOID, reason = "attempted correction")

        assertEquals(0, changed)
        assertEquals(SignatureState.SIGNED_LOCAL, db.crs().byId("c1")!!.signatureState)
    }

    @Test
    fun `highestNumber returns the highest issued number for a prefix`() = runBlocking {
        seedEntry()
        db.crs().insert(crs("c1", "CRS-2026-0001", 1, SignatureState.SIGNED_LOCAL))
        db.crs().insert(crs("c2", "CRS-2026-0002", 2, SignatureState.SIGNED_LOCAL))

        assertEquals("CRS-2026-0002", db.crs().highestNumber("CRS-2026-%"))
    }

    @Test
    fun `setSignedPhoto attaches or clears the photo path regardless of signature state`() = runBlocking {
        seedEntry()
        db.crs().insert(crs("c1", "CRS-2026-0001", 1, SignatureState.SIGNED_LOCAL))

        db.crs().setSignedPhoto("c1", "/data/crs/signed-copy.jpg")
        assertEquals("/data/crs/signed-copy.jpg", db.crs().byId("c1")!!.signedPhotoLocalPath)

        db.crs().setSignedPhoto("c1", null)
        assertEquals(null, db.crs().byId("c1")!!.signedPhotoLocalPath)
    }
}
