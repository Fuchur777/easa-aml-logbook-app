package nl.part66l.logbook.signing

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import androidx.biometric.BiometricManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

private const val ALIAS = "part66l-crs-signing-key"
private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

/**
 * Owns the Android Keystore key backing the app's local signer (§9.3). The key never
 * leaves the Keystore — [sign] on this class is deliberately unusable, since a per-use
 * biometric-gated key needs a [Signature] that only [BiometricSigningGate]'s prompt can
 * unlock. Real signing goes through [signWithAuthorizedSignature], called by the
 * [AuthorizedLocalKeystoreSigner] the gate hands back on success.
 */
@Singleton
class LocalKeystoreSignerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyStore: KeyStore,
) : LocalKeystoreSigner {

    private val keyLock = Any()

    override val id: String = "local-keystore"

    override suspend fun isAvailable(): Boolean {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Unusable by design: signing needs a [Signature] unlocked by a per-use biometric
     * prompt, which only [BiometricSigningGate] can obtain. Call [signWithAuthorizedSignature]
     * (via [AuthorizedLocalKeystoreSigner]) instead.
     */
    override suspend fun sign(digest: ByteArray): Result<ByteArray> = Result.failure(
        UnsupportedOperationException(
            "LocalKeystoreSignerImpl.sign() has no authorized Signature. " +
                "Obtain one via BiometricSigningGate.authorize() first.",
        ),
    )

    override fun describe(): SignerDescription = SignerDescription(
        method = "Hardware-backed key, biometric unlock",
        keyStorage = if (isStrongBoxBacked()) "Android Keystore (StrongBox)" else "Android Keystore (TEE)",
        authentication = "Class 3 biometric",
        certificateSubject = certificateOrNull()?.subjectX500Principal?.name,
        qualified = false,
    )

    override fun certificatePem(): String {
        ensureKeyExists()
        val encoded = Base64.encodeToString(certificate().encoded, Base64.NO_WRAP)
        val wrapped = encoded.chunked(64).joinToString("\n")
        return "-----BEGIN CERTIFICATE-----\n$wrapped\n-----END CERTIFICATE-----\n"
    }

    override fun fingerprint(): String {
        ensureKeyExists()
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(certificate().encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    override suspend fun rotate(): Result<String> = try {
        synchronized(keyLock) {
            if (keyStore.containsAlias(ALIAS)) keyStore.deleteEntry(ALIAS)
            ensureKeyExists()
        }
        Result.success(fingerprint())
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** An unlocked [Signature] for this key, ready to be wrapped in a [android.hardware.biometrics.BiometricPrompt.CryptoObject] — never returns the private key itself. */
    internal fun newSignatureForAuthorization(): Signature {
        ensureKeyExists()
        val privateKey = keyStore.getKey(ALIAS, null) as PrivateKey
        return Signature.getInstance(SIGNATURE_ALGORITHM).apply { initSign(privateKey) }
    }

    /**
     * Builds the CMS/PKCS#7 detached signature over [digest] (the already-computed SHA-256
     * of the PDF byte range — never re-hashed here) using [authorizedSignature], a
     * [Signature] the biometric prompt has already unlocked for exactly one use.
     *
     * BouncyCastle's [org.bouncycastle.cms.CMSSignedDataGenerator] normally computes its own
     * content digest; since Android Keystore never exposes the raw content for double-hashing
     * concerns here (and [CrsSigner.sign] only ever receives a digest, not raw content), the
     * digest is instead injected directly as the CMS `messageDigest` signed attribute via
     * [PrecomputedDigestAttributeTableGenerator], with [org.bouncycastle.cms.CMSAbsentContent]
     * standing in for the (deliberately not re-supplied) original content.
     */
    internal fun signWithAuthorizedSignature(digest: ByteArray, authorizedSignature: Signature): Result<ByteArray> = try {
        ensureKeyExists()
        Result.success(CrsCmsBuilder.build(digest, authorizedSignature, certificate()))
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun certificate(): X509Certificate = certificateOrNull()
        ?: error("Signing key $ALIAS has no certificate — ensureKeyExists() should have generated one")

    private fun certificateOrNull(): X509Certificate? =
        if (keyStore.containsAlias(ALIAS)) keyStore.getCertificate(ALIAS) as? X509Certificate else null

    private fun isStrongBoxBacked(): Boolean = try {
        val privateKey = (if (keyStore.containsAlias(ALIAS)) keyStore.getKey(ALIAS, null) else null) as? PrivateKey
        if (privateKey == null) {
            false
        } else {
            val factory = KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
            val keyInfo = factory.getKeySpec(privateKey, KeyInfo::class.java)
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
        }
    } catch (e: Exception) {
        false
    }

    private fun ensureKeyExists() {
        if (keyStore.containsAlias(ALIAS)) return
        synchronized(keyLock) {
            if (keyStore.containsAlias(ALIAS)) return
            try {
                generateKey(useStrongBox = true)
            } catch (e: StrongBoxUnavailableException) {
                generateKey(useStrongBox = false)
            }
        }
    }

    /**
     * The self-signed certificate these four `setCertificate*` calls produce (readable
     * afterward via `keyStore.getCertificate(alias)`) *is* the real, usable certificate —
     * Android Keystore does not need a separate BouncyCastle cert-building step on top.
     */
    private fun generateKey(useStrongBox: Boolean) {
        val notBefore = Date()
        val notAfter = Date.from(Instant.now().plus(30L * 365, ChronoUnit.DAYS))
        val builder = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .setCertificateSubject(javax.security.auth.x500.X500Principal("CN=Part-66L Logbook local signer"))
            .setCertificateSerialNumber(BigInteger.ONE)
            .setCertificateNotBefore(notBefore)
            .setCertificateNotAfter(notAfter)
        if (useStrongBox) builder.setIsStrongBoxBacked(true)

        val keyPairGenerator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        keyPairGenerator.initialize(builder.build())
        keyPairGenerator.generateKeyPair()
    }
}
