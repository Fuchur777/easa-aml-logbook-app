package nl.part66l.logbook

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/** PdfBox-Android loads its bundled font metrics through this — required once, before any PDF work. */
class Part66LogApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
