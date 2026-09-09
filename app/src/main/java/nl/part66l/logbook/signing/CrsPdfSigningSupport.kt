package nl.part66l.logbook.signing

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Calendar

/**
 * Embeds a [CrsSigner]'s CMS signature into an already-fully-rendered CRS PDF, in
 * place. The visible signature-block text (method/time/subject/fingerprint, drawn by
 * [nl.part66l.logbook.pdf.CrsPdfRenderer] before this ever runs) is never touched —
 * PDFBox's incremental save only appends a new trailer, xref and signature dictionary
 * after the file's existing bytes; the digest is computed over that already-final
 * byte range.
 */
object CrsPdfSigningSupport {

    suspend fun signInPlace(pdfFile: File, signer: CrsSigner) {
        val tempFile = File.createTempFile("crs-sign-", ".pdf", pdfFile.parentFile)
        try {
            // document must be closed before pdfFile is replaced below — on Windows a file
            // still open via PDDocument.load() cannot be deleted or overwritten.
            val document = PDDocument.load(pdfFile)
            try {
                val signature = PDSignature()
                signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
                signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED)
                signature.setName(signer.describe().certificateSubject.orEmpty())
                signature.setSignDate(Calendar.getInstance())
                document.addSignature(signature)

                FileOutputStream(tempFile).use { out ->
                    val externalSigning = document.saveIncrementalForExternalSigning(out)
                    val digest = MessageDigest.getInstance("SHA-256").also { md ->
                        externalSigning.content.use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                md.update(buffer, 0, read)
                            }
                        }
                    }.digest()

                    val cms = signer.sign(digest).getOrThrow()
                    externalSigning.setSignature(cms)
                }
            } finally {
                document.close()
            }

            if (!pdfFile.delete() || !tempFile.renameTo(pdfFile)) {
                throw IllegalStateException("Could not replace $pdfFile with its signed content")
            }
        } finally {
            tempFile.delete()
        }
    }
}
