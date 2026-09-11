package nl.part66l.logbook

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.CatalogueSeeder
import nl.part66l.logbook.data.SettingsRepository
import nl.part66l.logbook.di.ApplicationScope
import nl.part66l.logbook.drive.DriveSyncScheduler

@HiltAndroidApp
class Part66LogApplication : Application(), Configuration.Provider {

    @Inject lateinit var catalogueSeeder: CatalogueSeeder

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    /** Lets WorkManager build `DriveSyncWorker` (§10 auto-sync) via Hilt, same as any other `@Inject`-constructed class. */
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        applicationScope.launch { catalogueSeeder.seedIfNeeded() }
        applicationScope.launch {
            // Re-arm auto-sync if the preference says it should be on but WorkManager has no
            // record of it. The two live in different places — the flag in DataStore, the work in
            // WorkManager's own database — so a restore brings the flag back without the job, and
            // the switch would read "on" while nothing ever ran. enqueueUniquePeriodicWork with
            // the UPDATE policy is idempotent, so doing this on every start is harmless.
            if (settingsRepository.driveAutoSyncEnabled.first()) {
                DriveSyncScheduler.schedule(this@Part66LogApplication)
            }
        }
    }
}
