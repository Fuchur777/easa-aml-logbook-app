package nl.part66l.logbook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import nl.part66l.logbook.domain.CertificationBasis
import nl.part66l.logbook.domain.HelperRole
import nl.part66l.logbook.domain.Propulsion
import nl.part66l.logbook.domain.SignatureState
import nl.part66l.logbook.domain.Structure
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    private fun crs(
        id: String,
        number: String,
        sequence: Int,
        state: SignatureState,
        entryId: String = "e1",
        baseNumber: String = number,
        revision: Int = 0,
        completionDate: LocalDate = LocalDate.of(2026, 3, 14),
    ) = CrsEntity(
        id = id,
        entryId = entryId,
        number = number,
        numberNormalised = Identifiers.normalise(number),
        baseNumber = baseNumber,
        revision = revision,
        sequence = sequence,
        year = 2026,
        basis = CertificationBasis.ML_A_801_B2_INDEPENDENT,
        statementVersion = "v1",
        completionDate = completionDate,
        signatureState = state,
        snapshotJson = "{}",
    )

    private fun seedEntry() = runBlocking {
        db.workEntries().insert(
            WorkEntryEntity(
                id = "e1",
                aircraftId = null,
                description = "Annual inspection",
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

    private fun finalize(id: String, signedAt: Instant = Instant.EPOCH) = runBlocking {
        db.crs().finalizeSigned(
            id = id,
            state = SignatureState.SIGNED_LOCAL,
            signedAt = signedAt,
            signedDevice = "Pixel 8",
            signedAuthMethod = "Class 3 biometric",
            signedAppVersion = "1.0",
            signingCertificatePem = "-----BEGIN CERTIFICATE-----\nfake\n-----END CERTIFICATE-----\n",
            signingCertificateFingerprint = "AA:BB:CC",
            pdfLocalPath = "/data/crs/$id.pdf",
            pdfSha256 = "fake-sha",
        )
    }

    @Test
    fun `finalizeSigned writes every signing column and transitions a draft to signed`() = runBlocking {
        seedEntry()
        db.crs().insert(crs("c1", "CRS-2026-0001", 1, SignatureState.DRAFT))

        val changed = finalize("c1", signedAt = Instant.ofEpochSecond(1_700_000_000))

        assertEquals(1, changed)
        val signed = db.crs().byId("c1")!!
        assertEquals(SignatureState.SIGNED_LOCAL, signed.signatureState)
        assertEquals(Instant.ofEpochSecond(1_700_000_000), signed.signedAt)
        assertEquals("Pixel 8", signed.signedDevice)
        assertEquals("Class 3 biometric", signed.signedAuthMethod)
        assertEquals("1.0", signed.signedAppVersion)
        assertEquals("AA:BB:CC", signed.signingCertificateFingerprint)
        assertEquals("/data/crs/c1.pdf", signed.pdfLocalPath)
        assertEquals("fake-sha", signed.pdfSha256)
    }

    @Test
    fun `finalizeSigned is a no-op from any state other than draft`() = runBlocking {
        seedEntry()
        db.crs().insert(crs("c1", "CRS-2026-0001", 1, SignatureState.SIGNED_LOCAL))

        val changed = finalize("c1")

        assertEquals(0, changed)
        assertEquals(null, db.crs().byId("c1")!!.pdfLocalPath)
    }

    @Test
    fun `latestIssued returns only the highest revision per base number, excluding draft and void`() = runBlocking {
        seedEntry()
        db.crs().insert(crs("c1", "CRS-2026-0001", 1, SignatureState.SIGNED_LOCAL, baseNumber = "CRS-2026-0001", revision = 0))
        db.crs().insert(crs("c2", "CRS-2026-0001-rev1", 1, SignatureState.SIGNED_LOCAL, baseNumber = "CRS-2026-0001", revision = 1))
        db.crs().insert(crs("draft", "CRS-2026-0002", 2, SignatureState.DRAFT))
        db.crs().insert(crs("void", "CRS-2026-0003", 3, SignatureState.VOID))

        val rows = db.crs().latestIssued(aircraftId = null, numberQuery = null, helperQuery = null, ascending = false).first()

        assertEquals(listOf("CRS-2026-0001-rev1"), rows.map { it.crs.number })
    }

    @Test
    fun `an abandoned draft revision does not hide an earlier issued revision of the same base number`() = runBlocking {
        // A crash between allocating a new revision's DRAFT row and actually finishing
        // signing it (see CrsRepository.signLocal's own doc comment) would otherwise leave
        // this exact shape: a higher-revision DRAFT sitting on top of a real, issued rev0.
        seedEntry()
        db.crs().insert(crs("c1", "CRS-2026-0001", 1, SignatureState.SIGNED_LOCAL, baseNumber = "CRS-2026-0001", revision = 0))
        db.crs().insert(crs("c2", "CRS-2026-0001-rev1", 1, SignatureState.DRAFT, baseNumber = "CRS-2026-0001", revision = 1))

        val rows = db.crs().latestIssued(aircraftId = null, numberQuery = null, helperQuery = null, ascending = false).first()

        assertEquals(listOf("CRS-2026-0001"), rows.map { it.crs.number })
    }

    @Test
    fun `latestIssued filters by aircraft, CRS name, and an assisted-by name substring, and sorts by completion date`() = runBlocking {
        db.aircraft().insert(
            AircraftEntity(
                id = "ac1", manufacturer = "Schleicher", type = "ASK 21", serialNumber = "21123",
                propulsion = Propulsion.UNPOWERED, structure = Structure.WOOD_AND_FABRIC,
            ),
        )
        db.aircraft().insertRegistration(
            AircraftRegistrationEntity(
                id = "reg1", aircraftId = "ac1", registration = "PH-1234", registrationNormalised = "PH1234",
                validFrom = LocalDate.of(2020, 1, 1), validTo = null,
            ),
        )
        db.workEntries().insert(
            WorkEntryEntity(id = "e1", aircraftId = "ac1", description = "Annual", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
        )
        db.workEntries().insert(
            WorkEntryEntity(id = "e2", aircraftId = null, description = "Bench work", createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
        )
        db.people().insert(PersonEntity(id = "p1", name = "Jan de Vries"))
        db.entryHelpers().insert(EntryHelperEntity(entryId = "e1", personId = "p1", role = HelperRole.ASSISTED))

        db.crs().insert(
            crs(
                "c1", "CRS-2026-0001", 1, SignatureState.SIGNED_LOCAL, entryId = "e1",
                completionDate = LocalDate.of(2026, 1, 1),
            ),
        )
        db.crs().insert(
            crs(
                "c2", "CRS-2026-0002", 2, SignatureState.ISSUED_UNSIGNED_PRINT, entryId = "e2",
                completionDate = LocalDate.of(2026, 6, 1),
            ),
        )

        val all = db.crs().latestIssued(aircraftId = null, numberQuery = null, helperQuery = null, ascending = false).first()
        assertEquals(listOf("CRS-2026-0002", "CRS-2026-0001"), all.map { it.crs.number }) // newest first

        val ascending = db.crs().latestIssued(aircraftId = null, numberQuery = null, helperQuery = null, ascending = true).first()
        assertEquals(listOf("CRS-2026-0001", "CRS-2026-0002"), ascending.map { it.crs.number })

        val byAircraft = db.crs().latestIssued(aircraftId = "ac1", numberQuery = null, helperQuery = null, ascending = false).first()
        assertEquals(listOf("CRS-2026-0001"), byAircraft.map { it.crs.number })
        assertEquals("PH-1234", byAircraft.first().aircraftRegistration)

        val byNumber = db.crs().latestIssued(aircraftId = null, numberQuery = "0002", helperQuery = null, ascending = false).first()
        assertEquals(listOf("CRS-2026-0002"), byNumber.map { it.crs.number })

        val byNumberNoMatch = db.crs().latestIssued(aircraftId = null, numberQuery = "no-such-number", helperQuery = null, ascending = false).first()
        assertEquals(emptyList<String>(), byNumberNoMatch.map { it.crs.number })

        val byHelper = db.crs().latestIssued(aircraftId = null, numberQuery = null, helperQuery = "de vries", ascending = false).first()
        assertEquals(listOf("CRS-2026-0001"), byHelper.map { it.crs.number })
        assertTrue(byHelper.first().helperNames.contains("Jan de Vries"))

        val byHelperNoMatch = db.crs().latestIssued(aircraftId = null, numberQuery = null, helperQuery = "nobody", ascending = false).first()
        assertEquals(emptyList<String>(), byHelperNoMatch.map { it.crs.number })
    }
}
