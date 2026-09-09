package nl.part66l.logbook.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [LocalKeystoreSignerImpl][nl.part66l.logbook.signing.LocalKeystoreSignerImpl] itself needs a
 * real AndroidKeyStore and isn't unit-testable — this covers the one piece of its key-history
 * bookkeeping (§9.3/§9.4) that is: the DAO's own retire/current/ordering behaviour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SigningKeyDaoTest {

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

    private fun key(
        id: String,
        fingerprint: String,
        generatedAt: Instant,
        retiredAt: Instant? = null,
        retiredReason: String? = null,
    ) = SigningKeyEntity(
        id = id,
        fingerprint = fingerprint,
        certificatePem = "-----BEGIN CERTIFICATE-----\nfake\n-----END CERTIFICATE-----\n",
        keyStorage = "Android Keystore (TEE)",
        generatedAt = generatedAt,
        retiredAt = retiredAt,
        retiredReason = retiredReason,
    )

    @Test
    fun `current returns the one row with no retiredAt`() = runBlocking {
        db.signingKeys().insert(
            key(
                "k1", "AA:BB", Instant.parse("2026-01-01T00:00:00Z"),
                retiredAt = Instant.parse("2026-02-01T00:00:00Z"), retiredReason = "Rotated by user",
            ),
        )
        db.signingKeys().insert(key("k2", "CC:DD", Instant.parse("2026-02-01T00:00:00Z")))

        val current = db.signingKeys().current()

        assertEquals("k2", current!!.id)
        assertNull(current.retiredAt)
    }

    @Test
    fun `current is null when every key has been retired`() = runBlocking {
        db.signingKeys().insert(
            key(
                "k1", "AA:BB", Instant.parse("2026-01-01T00:00:00Z"),
                retiredAt = Instant.parse("2026-02-01T00:00:00Z"), retiredReason = "Rotated by user",
            ),
        )

        assertNull(db.signingKeys().current())
    }

    @Test
    fun `retireCurrent retires only the one active row, leaving an already-retired row untouched`() = runBlocking {
        val alreadyRetiredAt = Instant.parse("2026-01-15T00:00:00Z")
        db.signingKeys().insert(key("k1", "AA:BB", Instant.parse("2026-01-01T00:00:00Z"), retiredAt = alreadyRetiredAt, retiredReason = "Rotated by user"))
        db.signingKeys().insert(key("k2", "CC:DD", Instant.parse("2026-02-01T00:00:00Z")))

        db.signingKeys().retireCurrent(Instant.parse("2026-03-01T00:00:00Z"), "Invalidated by a change to enrolled biometrics")

        val all = db.signingKeys().observeAll().first()
        val k1 = all.first { it.id == "k1" }
        val k2 = all.first { it.id == "k2" }
        assertEquals(alreadyRetiredAt, k1.retiredAt) // unchanged by the second retire call
        assertEquals("Rotated by user", k1.retiredReason)
        assertEquals(Instant.parse("2026-03-01T00:00:00Z"), k2.retiredAt)
        assertEquals("Invalidated by a change to enrolled biometrics", k2.retiredReason)
        assertNull(db.signingKeys().current())
    }

    @Test
    fun `observeAll orders newest generation first`() = runBlocking {
        db.signingKeys().insert(key("k1", "AA:BB", Instant.parse("2026-01-01T00:00:00Z")))
        db.signingKeys().insert(key("k2", "CC:DD", Instant.parse("2026-02-01T00:00:00Z")))
        db.signingKeys().insert(key("k3", "EE:FF", Instant.parse("2025-12-01T00:00:00Z")))

        val ids = db.signingKeys().observeAll().first().map { it.id }

        assertEquals(listOf("k2", "k1", "k3"), ids)
    }
}
