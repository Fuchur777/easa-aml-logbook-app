package nl.part66l.logbook.signing

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.Signature
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Why a biometric prompt didn't produce a signature — [errorCode] is one of [BiometricPrompt]'s `ERROR_*` constants. */
class BiometricSigningException(val errorCode: Int, message: String) : Exception(message)

/**
 * The one place in the app allowed to hold a [FragmentActivity] reference for signing —
 * [nl.part66l.logbook.ui.crs.CrsViewModel] and [nl.part66l.logbook.data.CrsRepository] only
 * ever see the already-authorized [AuthorizedLocalKeystoreSigner] this produces, never an
 * `Activity`. Built fresh at the Compose call site (`LocalContext.current as FragmentActivity`),
 * not injected.
 */
class BiometricSigningGate(private val activity: FragmentActivity) {

    /** Shows the per-use biometric prompt and, on success, returns a [LocalKeystoreSigner] authorized to sign exactly once. */
    suspend fun authorize(baseSigner: LocalKeystoreSignerImpl): Result<AuthorizedLocalKeystoreSigner> = try {
        val signature = baseSigner.newSignatureForAuthorization()
        val cryptoObject = BiometricPrompt.CryptoObject(signature)
        suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(activity)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authorizedSignature = result.cryptoObject?.signature
                    val outcome = if (authorizedSignature == null) {
                        Result.failure(IllegalStateException("Biometric prompt succeeded without a Signature"))
                    } else {
                        Result.success(AuthorizedLocalKeystoreSigner(baseSigner, authorizedSignature))
                    }
                    if (continuation.isActive) continuation.resume(outcome)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (continuation.isActive) continuation.resume(Result.failure(BiometricSigningException(errorCode, errString.toString())))
                }

                override fun onAuthenticationFailed() {
                    // One rejected biometric read (e.g. wrong finger) — the prompt stays open for another attempt, nothing to resume yet.
                }
            }
            val prompt = BiometricPrompt(activity, executor, callback)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Sign certificate")
                .setSubtitle("Confirm your identity to release the signing key")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Cancel")
                .build()
            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
            prompt.authenticate(promptInfo, cryptoObject)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * A [LocalKeystoreSigner] whose key is unlocked for exactly one signature — everything except
 * [sign] delegates straight to [base], which needs no authorization (certificate/fingerprint
 * reads, availability checks, [rotate] all touch the Keystore entry, not the private key itself).
 */
class AuthorizedLocalKeystoreSigner internal constructor(
    private val base: LocalKeystoreSignerImpl,
    private val authorizedSignature: Signature,
) : LocalKeystoreSigner by base {
    override suspend fun sign(digest: ByteArray): Result<ByteArray> = base.signWithAuthorizedSignature(digest, authorizedSignature)
}
