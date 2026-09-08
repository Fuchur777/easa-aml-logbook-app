package nl.part66l.logbook.ui.crs

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies a picked photo of the hand-signed paper copy into app-private storage immediately,
 * rather than holding onto [uri] — a `GetContent` result carries no persistable permission, so
 * the source could become unreadable the moment this call returns. Same [uri]-to-file approach
 * as `copyPdfToInternalStorage`, sharing the "crs" directory since both are CRS record files.
 * Returns the new file's absolute path, or null if the copy failed.
 */
suspend fun copySignedPhotoToInternalStorage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    val destDir = File(context.filesDir, "crs").apply { mkdirs() }
    val destFile = File(destDir, "${UUID.randomUUID()}.jpg")

    val copied = context.contentResolver.openInputStream(uri)?.use { input ->
        destFile.outputStream().use { output -> input.copyTo(output) }
        true
    } ?: false

    if (copied) destFile.absolutePath else null
}

/**
 * Opens a signed-copy photo previously saved by [copySignedPhotoToInternalStorage] in whatever
 * viewer the device has, via the same [FileProvider] the app already declares for CRS PDFs.
 * Silently does nothing if no app can handle it, rather than crashing.
 */
fun openSignedPhoto(context: Context, photoPath: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(photoPath))
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // No image viewer installed — nothing sensible to do from here.
    }
}
