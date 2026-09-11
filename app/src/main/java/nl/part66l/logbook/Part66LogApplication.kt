package nl.part66l.logbook

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.CatalogueSeeder
import nl.part66l.logbook.di.ApplicationScope

@HiltAndroidApp
class Part66LogApplication : Application(), Configuration.Provider {

    @Inject lateinit var catalogueSeeder: CatalogueSeeder

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
    }
}
