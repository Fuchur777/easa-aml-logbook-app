package nl.part66l.logbook.ui.crs

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.part66l.logbook.data.IssuedCrsRow

/**
 * Zips the rendered PDF (always present for an issued certificate) and, when the
 * print-and-wet-sign path attached one, the signed-copy photo too, for every [rows] entry —
 * one export per selection from the CRS Library screen. Built fresh into
 * `context.filesDir/crs-export/` each time (the folder is cleared first, so a stale export
 * never lingers alongside the new one) rather than kept around, since it's a byte-identical
 * repackaging of files that already exist elsewhere.
 */
suspend fun exportCrsZip(context: Context, rows: List<IssuedCrsRow>): File = withContext(Dispatchers.IO) {
    val destDir = File(context.filesDir, "crs-export").apply {
        deleteRecursively()
        mkdirs()
    }
    val zipFile = File(destDir, "CRS-export-${System.currentTimeMillis()}.zip")
    val usedNames = mutableSetOf<String>()

    fun uniqueName(preferred: String): String {
        if (usedNames.add(preferred)) return preferred
        val base = preferred.substringBeforeLast('.', preferred)
        val ext = preferred.substringAfterLast('.', "")
        var index = 1
        var candidate: String
        do {
            candidate = if (ext.isEmpty()) "$base-$index" else "$base-$index.$ext"
            index++
        } while (!usedNames.add(candidate))
        return candidate
    }

    ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
        for (row in rows) {
            row.crs.pdfLocalPath?.let { path ->
                val source = File(path)
                if (source.exists()) {
                    zip.putNextEntry(ZipEntry(uniqueName("${row.crs.number}.pdf")))
                    source.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            row.crs.signedPhotoLocalPath?.let { path ->
                val source = File(path)
                if (source.exists()) {
                    val ext = source.extension.ifBlank { "jpg" }
                    zip.putNextEntry(ZipEntry(uniqueName("${row.crs.number}-signed-copy.$ext")))
                    source.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }
    zipFile
}

/**
 * Hands [zipFile] to whatever app the user picks (Files, Drive, email, ...) via the standard
 * share sheet — same [FileProvider] the app already declares for PDFs and photos. There is no
 * single "save to device" API that works the same way across Android versions and vendors, so
 * sharing is the portable equivalent; the chosen app's own "Save"/"Download" action is then
 * whatever it normally offers. Silently does nothing if no app can handle it.
 */
fun shareCrsZip(context: Context, zipFile: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Export CRS"))
    } catch (e: ActivityNotFoundException) {
        // No app can handle a share of this type — nothing sensible to do from here.
    }
}
