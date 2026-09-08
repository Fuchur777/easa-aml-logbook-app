package nl.part66l.logbook.ui.documents

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies a picked PDF into app-private storage immediately, rather than holding onto
 * [uri] — a `GetContent` result carries no persistable permission, so the source could
 * become unreadable the moment this call returns. Returns the new file's absolute path
 * and the original display name, or null if the copy failed.
 */
suspend fun copyPdfToInternalStorage(context: Context, uri: Uri): Pair<String, String>? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val fileName = resolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
    } ?: "document.pdf"

    val destDir = File(context.filesDir, "documents").apply { mkdirs() }
    val destFile = File(destDir, "${UUID.randomUUID()}.pdf")

    val copied = resolver.openInputStream(uri)?.use { input ->
        destFile.outputStream().use { output -> input.copyTo(output) }
        true
    } ?: false

    if (copied) destFile.absolutePath to fileName else null
}

/**
 * Opens a PDF previously saved by [copyPdfToInternalStorage] in whatever viewer the
 * device has — a raw `file://` Uri would throw `FileUriExposedException` on a modern
 * target SDK, so this goes through the [FileProvider] declared in the manifest instead.
 * Silently does nothing if no app can handle it, rather than crashing.
 */
fun openPdf(context: Context, pdfPath: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(pdfPath))
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No PDF viewer installed — nothing sensible to do from here.
    }
}
