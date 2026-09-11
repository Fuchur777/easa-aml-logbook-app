package nl.part66l.logbook.drive

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import nl.part66l.logbook.data.DriveSyncRepository

/**
 * Background auto-sync (§10) — runs [DriveSyncRepository.syncNowSilently] on WorkManager's
 * schedule (see [DriveSyncScheduler]). Always reports success: a failed authorization (access
 * revoked, or genuinely never granted) or a per-item upload failure both just leave work pending
 * for the next run — a `Result.retry()`/backoff storm would only hammer Google's auth endpoint
 * for something only the user, from inside the app, can actually fix.
 */
@HiltWorker
class DriveSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val driveSyncRepository: DriveSyncRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        driveSyncRepository.syncNowSilently(applicationContext)
        return Result.success()
    }
}
