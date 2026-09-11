package nl.part66l.logbook.fakes

import android.content.Context
import androidx.fragment.app.FragmentActivity
import nl.part66l.logbook.drive.DriveAuthManager

/** Stands in for [nl.part66l.logbook.drive.DriveAuthManagerImpl] — repository-level tests care about what happens around a token, not the real `AuthorizationClient` flow (unavailable outside a real device). */
class FakeDriveAuthManager(
    private val token: String = "fake-access-token",
    /** Configurable per test: null (default) succeeds; set to make every [authorize] call fail. */
    var authorizeFailure: Throwable? = null,
    /** Configurable per test: null (default) succeeds; set to make every [authorizeSilently] call fail. */
    var authorizeSilentlyFailure: Throwable? = null,
) : DriveAuthManager {

    var authorizeCalls: Int = 0
        private set
    var authorizeSilentlyCalls: Int = 0
        private set

    override suspend fun authorize(activity: FragmentActivity): Result<String> {
        authorizeCalls++
        authorizeFailure?.let { return Result.failure(it) }
        return Result.success(token)
    }

    override suspend fun authorizeSilently(context: Context): Result<String> {
        authorizeSilentlyCalls++
        authorizeSilentlyFailure?.let { return Result.failure(it) }
        return Result.success(token)
    }
}
