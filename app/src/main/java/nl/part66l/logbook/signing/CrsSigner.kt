package nl.part66l.logbook.signing

/**
 * The seam.
 *
 * AMC1 ML.A.801(e) requires the person issuing a CRS to use their normal signature
 * except where a computer release-to-service system is used, in which case the
 * competent authority must be satisfied that only that particular person may
 * electronically issue the CRS. The bar is sole control, not eIDAS — the AMC's own
 * example of an acceptable method is a personal card plus a PIN.
 *
 * The PDF plumbing is identical whichever implementation is used: prepare the
 * document with a signature placeholder, compute the digest, obtain a CMS blob,
 * inject it, then timestamp. Only the source of the CMS differs. Keeping that
 * behind this interface is what makes a qualified signature a later addition
 * rather than a rewrite.
 */
interface CrsSigner {

    val id: String

    /** Whether this signer can currently produce a signature (key present, network up, etc.). */
    suspend fun isAvailable(): Boolean

    /**
     * @param digest the message digest of the prepared PDF byte range
     * @return a CMS/PKCS#7 detached signature suitable for embedding
     */
    suspend fun sign(digest: ByteArray): Result<ByteArray>

    /** Describes the signer for the signature intent record and the system description PDF. */
    fun describe(): SignerDescription
}

data class SignerDescription(
    val method: String,          // e.g. "Hardware-backed key, biometric unlock"
    val keyStorage: String,      // e.g. "Android Keystore (StrongBox)"
    val authentication: String,  // e.g. "Class 3 biometric"
    val certificateSubject: String?,
    val qualified: Boolean,
)

/**
 * Timestamping is not optional. A CRS outlives the certificate that signed it, so
 * without an RFC-3161 timestamp the signature becomes unverifiable exactly when an
 * inspector looks at it. Where the hangar has no signal, the CRS is stored
 * TIMESTAMP_PENDING and completed when connectivity returns — the signature itself
 * is already valid; only long-term verifiability is deferred.
 */
interface TimestampAuthority {
    suspend fun timestamp(digest: ByteArray): Result<ByteArray>
}

// ---------------------------------------------------------------------------
// The v1 implementation
// ---------------------------------------------------------------------------

/**
 * Hardware-backed key in the Android Keystore, wrapped in a self-signed
 * certificate. No third-party trust provider is involved, deliberately: a
 * sixty-year logbook should not depend on a commercial QTSP remaining in business,
 * and AMC1 ML.A.801(e) asks for sole control rather than eIDAS status.
 *
 * Key generation parameters that matter:
 *  - `setIsStrongBoxBacked(true)` where available, falling back to the TEE
 *  - `setUserAuthenticationRequired(true)` with a per-use biometric prompt
 *  - `setInvalidatedByBiometricEnrolment(true)` — adding a new fingerprint to the
 *    device destroys the key, which is the correct behaviour: a key that survives
 *    an enrolment change is no longer under the sole control of one person
 *  - validity thirty years; with no CA there is no renewal path, and expiry would
 *    only break verification of certificates already issued
 *
 * The key cannot be exported or backed up. That is the point, and it has a
 * consequence: a lost device loses the key. Certificates already issued must still
 * verify, so the certificate is archived with every CRS record and in the export
 * bundle — never only inside the PDF.
 */
interface LocalKeystoreSigner : CrsSigner {

    /** PEM of the self-signed certificate, archived with each CRS. */
    fun certificatePem(): String

    /**
     * SHA-256 fingerprint, printed in the system description document. The
     * competent authority pins this to the licence holder, which is what makes the
     * authority — rather than a trust provider — the anchor of the scheme.
     */
    fun fingerprint(): String

    /** Generates a replacement key on a new device. Historical certificates are retained. */
    suspend fun rotate(): Result<String>
}

/**
 * Best-effort timestamping against a public RFC-3161 authority. No account or
 * contract required.
 *
 * Never blocking. A CRS signed without connectivity is valid and stored
 * TIMESTAMP_PENDING; a background worker completes it later. Configure several
 * authorities in order — a free TSA is unlikely to outlive the logbook — and record
 * which one answered, so a future reader knows where the countersignature came
 * from.
 */
interface TimestampPolicy {
    val authorities: List<String>
    suspend fun apply(digest: ByteArray): TimestampResult
}

sealed interface TimestampResult {
    data class Applied(val token: ByteArray, val authority: String) : TimestampResult
    data class Deferred(val reason: String) : TimestampResult
}
