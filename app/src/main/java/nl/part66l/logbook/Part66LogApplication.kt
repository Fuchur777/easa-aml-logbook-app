package nl.part66l.logbook

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.CatalogueSeeder
import nl.part66l.logbook.di.ApplicationScope

@HiltAndroidApp
class Part66LogApplication : Application() {

    @Inject lateinit var catalogueSeeder: CatalogueSeeder

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        applicationScope.launch { catalogueSeeder.seedIfNeeded() }
    }
}
