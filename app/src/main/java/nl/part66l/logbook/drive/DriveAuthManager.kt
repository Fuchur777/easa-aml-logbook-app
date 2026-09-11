package nl.part66l.logbook.drive

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
private const val SCOPE_DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
/** Needed so the granted token can also call the userinfo endpoint — [DriveApiClient.fetchAccountEmail] resolves the connected account's email to show on the Drive backup screen. */
private const val SCOPE_USERINFO_EMAIL = "https://www.googleapis.com/auth/userinfo.email"

private val REQUESTED_SCOPES = listOf(Scope(SCOPE_DRIVE_FILE), Scope(SCOPE_DRIVE_APPDATA), Scope(SCOPE_USERINFO_EMAIL))

/** Thrown by [DriveAuthManager.authorizeSilently] when Play Services would need to show a consent screen — impossible from a background worker; the caller should just skip this run. */
class NeedsInteractiveAuthException : Exception("Google Drive needs you to reconnect — open the app to continue syncing")

/**
 * Google Drive backup (§10) authorization. No refresh token is ever stored — [authorize] is
 * called fresh at the start of every "Sync now" run; once the user has granted access, Play
 * Services returns a fresh access token silently, so this one function serves both the
 * first-time consent flow and every later re-authorization.
 */
interface DriveAuthManager {
    suspend fun authorize(activity: FragmentActivity): Result<String>

    /**
     * As [authorize], but never shows UI — for the background auto-sync worker, which has no
     * [FragmentActivity] to launch a consent screen from. Fails with [NeedsInteractiveAuthException]
     * if [AuthorizationResult.hasResolution] would be true (access isn't already granted, or was
     * revoked) rather than attempting to resolve it.
     */
    suspend fun authorizeSilently(context: Context): Result<String>
}

/** Wraps [AuthorizationClient] — see [DriveAuthManager]'s doc for the overall strategy. */
class DriveAuthManagerImpl @Inject constructor() : DriveAuthManager {

    override suspend fun authorize(activity: FragmentActivity): Result<String> = try {
        val client = Identity.getAuthorizationClient(activity)
        val request = AuthorizationRequest.builder().setRequestedScopes(REQUESTED_SCOPES).build()
        val initial = requestAuthorization(client, request)
        val resolved = if (initial.hasResolution()) {
            resolveConsent(activity, client, initial) ?: return Result.failure(IllegalStateException("Google account authorization was cancelled"))
        } else {
            initial
        }
        val token = resolved.accessToken
        if (token != null) Result.success(token) else Result.failure(IllegalStateException("Google authorization succeeded without an access token"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun authorizeSilently(context: Context): Result<String> = try {
        val client = Identity.getAuthorizationClient(context)
        val request = AuthorizationRequest.builder().setRequestedScopes(REQUESTED_SCOPES).build()
        val result = requestAuthorization(client, request)
        if (result.hasResolution()) {
            Result.failure(NeedsInteractiveAuthException())
        } else {
            val token = result.accessToken
            if (token != null) Result.success(token) else Result.failure(IllegalStateException("Google authorization succeeded without an access token"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun requestAuthorization(client: AuthorizationClient, request: AuthorizationRequest): AuthorizationResult =
        suspendCancellableCoroutine { continuation ->
            client.authorize(request)
                .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                .addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
        }

    /**
     * Shows the account/consent picker when [initial] isn't already an outright grant — resolved
     * via [Activity.RESULT_OK] on the returned [IntentSenderRequest].
     *
     * The launcher is registered against this activity's own registry under a one-shot key, so it
     * cannot survive the activity being recreated. If that happens while the Play Services sheet
     * is up — a rotation, or a low-memory kill — the result is delivered to a registry that has no
     * callback under that key and is dropped. The coroutine, however, lives in viewModelScope and
     * outlives the activity, so it would simply never resume: the Drive screen kept a spinner and
     * every button disabled until the process was killed.
     *
     * Watching the lifecycle turns that into an ordinary failure. The flow really was interrupted
     * and cannot be completed, so reporting it as such and letting the user tap again is the
     * honest outcome.
     */
    private suspend fun resolveConsent(
        activity: FragmentActivity,
        client: AuthorizationClient,
        initial: AuthorizationResult,
    ): AuthorizationResult? = suspendCancellableCoroutine { continuation ->
        val pendingIntent = initial.pendingIntent
        if (pendingIntent == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        var launcher: ActivityResultLauncher<IntentSenderRequest>? = null
        var observer: DefaultLifecycleObserver? = null

        fun cleanUp() {
            launcher?.unregister()
            observer?.let { activity.lifecycle.removeObserver(it) }
        }

        observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                cleanUp()
                if (continuation.isActive) continuation.resume(null)
            }
        }

        launcher = activity.activityResultRegistry.register(
            "drive_authorize_${UUID.randomUUID()}",
            ActivityResultContracts.StartIntentSenderForResult(),
            ActivityResultCallback { result ->
                cleanUp()
                val data = result.data
                val resolved = if (result.resultCode == Activity.RESULT_OK && data != null) {
                    try {
                        client.getAuthorizationResultFromIntent(data)
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
                if (continuation.isActive) continuation.resume(resolved)
            },
        )
        activity.lifecycle.addObserver(observer)
        continuation.invokeOnCancellation { cleanUp() }
        launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
    }
}
