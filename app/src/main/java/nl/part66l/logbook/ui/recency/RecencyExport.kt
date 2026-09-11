package nl.part66l.logbook.ui.recency

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.part66l.logbook.data.RecencyEvidenceRow

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * One row per [RecencyEvidenceRow] — the raw records behind the Recency screen's own numbers,
 * not just the on-screen summary, so a reader can check the app's "current until" claim
 * against the actual underlying entries. Built fresh into `context.filesDir/recency-export/`
 * each time (cleared first, so a stale export never lingers) rather than kept around, since
 * it's a formatted repackaging of data that already exists in the database.
 */
suspend fun exportRecencyCsv(context: Context, rows: List<RecencyEvidenceRow>): File = withContext(Dispatchers.IO) {
    val destDir = File(context.filesDir, "recency-export").apply {
        deleteRecursively()
        mkdirs()
    }
    val csvFile = File(destDir, "Recency-export-${System.currentTimeMillis()}.csv")

    csvFile.bufferedWriter().use { writer ->
        writer.appendLine(listOf("Subcategory", "Route", "Date", "Aircraft", "Detail").joinToString(",") { csvField(it) })
        for (row in rows) {
            writer.appendLine(
                listOf(
                    row.subcategory.name,
                    row.route.displayLabel,
                    row.date.format(DATE_FORMAT),
                    row.aircraftRegistration ?: "Bench / component work",
                    row.detail,
                ).joinToString(",") { csvField(it) },
            )
        }
    }
    csvFile
}

/** Quotes a field whenever it contains a comma, quote or newline — RFC 4180's own escaping rule. */
private fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }

/**
 * Hands [csvFile] to whatever app the user picks (Files, Drive, email, a spreadsheet app, ...)
 * via the standard share sheet — same [FileProvider] the app already declares for its other
 * exports. Silently does nothing if no app can handle it.
 */
fun shareRecencyCsv(context: Context, csvFile: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Export recency data"))
    } catch (e: ActivityNotFoundException) {
        // No app can handle a share of this type — nothing sensible to do from here.
    }
}
