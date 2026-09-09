package nl.part66l.logbook.fakes

import nl.part66l.logbook.signing.LocalKeystoreSigner
import nl.part66l.logbook.signing.SignerDescription

/**
 * Stands in for [nl.part66l.logbook.signing.LocalKeystoreSignerImpl] wherever a test needs a
 * [LocalKeystoreSigner] but not a real AndroidKeyStore — repository- and viewmodel-level tests
 * care about what happens around a signature (state transitions, error mapping), not the CMS
 * bytes themselves (see `CrsCmsBuilderTest` for that).
 */
class FakeLocalKeystoreSigner(
    private val description: SignerDescription = SignerDescription(
        method = "Hardware-backed key, biometric unlock",
        keyStorage = "Android Keystore (TEE)",
        authentication = "Class 3 biometric",
        certificateSubject = "CN=Test Signer",
        qualified = false,
    ),
    private val pem: String = "-----BEGIN CERTIFICATE-----\nfake\n-----END CERTIFICATE-----\n",
    private val fingerprintValue: String = "AA:BB:CC:DD",
    /** Configurable per test: null (default) succeeds; set to make [sign] fail, e.g. simulating a cancelled biometric prompt. */
    var signFailure: Throwable? = null,
) : LocalKeystoreSigner {

    override val id: String = "fake-local-keystore"
    val signCalls = mutableListOf<ByteArray>()

    override suspend fun isAvailable(): Boolean = true

    override suspend fun sign(digest: ByteArray): Result<ByteArray> {
        signCalls += digest
        signFailure?.let { return Result.failure(it) }
        return Result.success(byteArrayOf(1, 2, 3)) // placeholder CMS bytes — not structurally validated by CrsPdfSigningSupport
    }

    override fun describe(): SignerDescription = description

    override fun certificatePem(): String = pem

    override fun fingerprint(): String = fingerprintValue

    override suspend fun rotate(): Result<String> = Result.success(fingerprintValue)
}
