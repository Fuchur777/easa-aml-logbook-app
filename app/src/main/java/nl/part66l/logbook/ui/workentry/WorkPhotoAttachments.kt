package nl.part66l.logbook.ui.workentry

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.part66l.logbook.data.PhotoInput

private const val MAX_DIMENSION_PX = 1600
private const val JPEG_QUALITY = 85

/**
 * Turns a picked or freshly-captured photo into a [PhotoInput], per §8: downscaled by default,
 * GPS EXIF stripped by default (timestamp read out and kept separately, in [PhotoInput.capturedAt]
 * — not preserved in the file's own EXIF, since [Bitmap.compress] drops every EXIF tag when it
 * re-encodes, which is what actually does the stripping here, GPS included, rather than picking
 * out individual tags), hashed once after downscaling and never touched again, UUID filename.
 * The original file at [uri] (camera-staged or gallery-picked) is never modified or kept —
 * only this processed copy exists afterward. Null if [uri] couldn't be read as an image.
 */
suspend fun processAndStorePhoto(context: Context, uri: Uri, caption: String? = null): PhotoInput? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver

    val (orientation, capturedAt) = resolver.openInputStream(uri)?.use { input ->
        val exif = ExifInterface(input)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val capturedAt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let(::parseExifDate) ?: Instant.now()
        orientation to capturedAt
    } ?: (ExifInterface.ORIENTATION_NORMAL to Instant.now())

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

    val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION_PX)
    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    } ?: return@withContext null

    val rotated = applyExifRotation(decoded, orientation)
    val scaled = downscaleIfNeeded(rotated, MAX_DIMENSION_PX)

    val bytes = ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        out.toByteArray()
    }
    if (rotated !== decoded) decoded.recycle()
    if (scaled !== rotated) rotated.recycle()
    scaled.recycle()

    val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val id = UUID.randomUUID().toString()
    val destDir = File(context.filesDir, "attachments").apply { mkdirs() }
    val destFile = File(destDir, "$id.jpg")
    FileOutputStream(destFile).use { it.write(bytes) }

    PhotoInput(
        id = id,
        localPath = destFile.absolutePath,
        sha256 = sha256,
        capturedAt = capturedAt,
        caption = caption?.trim()?.takeIf { it.isNotBlank() },
        bytes = bytes.size.toLong(),
    )
}

/** Builds a fresh camera-capture destination under the same directory processed photos end up in, via the app's existing FileProvider. */
fun newCameraCaptureUri(context: Context): Uri {
    val destDir = File(context.filesDir, "attachments").apply { mkdirs() }
    val stagingFile = File(destDir, "capture-${UUID.randomUUID()}.jpg")
    return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", stagingFile)
}

private fun parseExifDate(value: String): Instant? =
    runCatching { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(value)?.toInstant() }.getOrNull()

/** Android's own documented pattern (developer.android.com/topic/performance/graphics/load-bitmap): a coarse, memory-safe power-of-two pre-scale; [downscaleIfNeeded] does the exact final resize. */
private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sampleSize = 1
    if (height > maxDimension || width > maxDimension) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / sampleSize >= maxDimension && halfWidth / sampleSize >= maxDimension) {
            sampleSize *= 2
        }
    }
    return sampleSize
}

private fun applyExifRotation(bitmap: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun downscaleIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val longestEdge = maxOf(bitmap.width, bitmap.height)
    if (longestEdge <= maxDimension) return bitmap
    val scale = maxDimension.toFloat() / longestEdge
    val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, width, height, true)
}
