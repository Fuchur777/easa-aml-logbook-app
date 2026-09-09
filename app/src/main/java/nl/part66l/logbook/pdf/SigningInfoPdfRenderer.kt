package nl.part66l.logbook.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB

/** Everything [SigningInfoPdfRenderer] needs to print — already formatted, like [CrsRenderData]. */
data class SigningInfoRenderData(
    val licenceHolderName: String,
    val licenceNumber: String,
    val issuingAuthority: String,
    val generatedDate: String,
    val method: String,
    val keyStorage: String,
    val authentication: String,
    val certificateSubject: String,
    val fingerprint: String,
    val certificatePem: String,
)

/**
 * A one-page "system description" document (§9.4): what the local signing key is, how it's
 * protected, and its fingerprint. Meant to be submitted to the competent authority once, so
 * the fingerprint printed on a locally signed CRS (§9.3) has an authoritative record to be
 * checked against later — the certificate itself carries no chain of trust on its own (see
 * [nl.part66l.logbook.signing.CrsSigner]'s doc comment: "the competent authority pins this
 * to the licence holder, which is what makes the authority — rather than a trust provider —
 * the anchor of the scheme").
 */
object SigningInfoPdfRenderer {

    private const val MM = 72f / 25.4f
    private val PAGE = PDRectangle.A4
    private const val MARGIN_MM = 20f
    private val L = MARGIN_MM * MM
    private val R = PAGE.width - MARGIN_MM * MM
    private val COLW = R - L
    private val TOP = PAGE.height - MARGIN_MM * MM
    private val BLACK = floatArrayOf(0f, 0f, 0f)
    private val GREY = floatArrayOf(0.45f, 0.45f, 0.45f)
    private val HELVETICA: PDFont = PDType1Font.HELVETICA
    private val HELVETICA_BOLD: PDFont = PDType1Font.HELVETICA_BOLD
    private val COURIER: PDFont = PDType1Font.COURIER

    /**
     * What the receiving authority does with a CRS that carries this key's fingerprint —
     * printed on the document itself since it's the one place guaranteed to reach them
     * alongside the fingerprint it explains.
     */
    private val VERIFICATION_STEPS = listOf(
        "1. Open the signed PDF in a signature-aware reader (Adobe Acrobat Reader is recommended " +
            "— most email and office apps do not check embedded signatures at all).",
        "2. Open the reader's signature panel and select the signature. It reports whether the " +
            "signed content is unchanged since signing (\"signature valid\"), and shows the " +
            "signing certificate.",
        "3. Compare the certificate's SHA-256 fingerprint shown there against the \"Certificate " +
            "fingerprint\" above. A match, together with a valid signature, confirms this exact, " +
            "unmodified document was signed with this specific key.",
        "4. The reader will likely also report the certificate as untrusted or self-signed — that " +
            "is expected, not a fault. This scheme deliberately uses no commercial trust provider; " +
            "this document, once on file with you, is the trust anchor in its place.",
    )

    fun render(document: PDDocument, data: SigningInfoRenderData) {
        val page = PDPage(PAGE)
        document.addPage(page)
        PDPageContentStream(document, page).use { stream ->
            var y = TOP

            fun text(x: Float, yPos: Float, s: String, font: PDFont, size: Float, color: FloatArray) {
                stream.setNonStrokingColorSpace(PDDeviceRGB.INSTANCE)
                stream.setNonStrokingColor(color)
                stream.beginText()
                stream.setFont(font, size)
                stream.newLineAtOffset(x, yPos)
                stream.showText(s)
                stream.endText()
            }

            fun stringWidth(s: String, font: PDFont, size: Float) = font.getStringWidth(s) / 1000f * size

            fun wrap(s: String, font: PDFont, size: Float): List<String> {
                val lines = mutableListOf<String>()
                var line = ""
                for (word in s.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
                    val trial = if (line.isEmpty()) word else "$line $word"
                    if (stringWidth(trial, font, size) > COLW) {
                        if (line.isNotEmpty()) lines += line
                        line = word
                    } else {
                        line = trial
                    }
                }
                if (line.isNotEmpty()) lines += line
                return lines
            }

            fun para(s: String, size: Float = 9f, leadingMm: Float = 4.3f, color: FloatArray = BLACK) {
                wrap(s, HELVETICA, size).forEach { line ->
                    text(L, y, line, HELVETICA, size, color)
                    y -= leadingMm * MM
                }
            }

            fun field(label: String, value: String) {
                text(L, y, label.uppercase(), HELVETICA, 7f, GREY)
                y -= 4.4f * MM
                text(L, y, value, HELVETICA, 10.5f, BLACK)
                y -= 8.5f * MM
            }

            text(L, y, "Digital signing — system description", HELVETICA_BOLD, 15f, BLACK)
            y -= 7f * MM
            text(L, y, "AMC1 ML.A.801(e) — submit this once to your competent authority so the fingerprint", HELVETICA, 8.5f, GREY)
            y -= 4f * MM
            text(L, y, "below has an authoritative record to be checked against.", HELVETICA, 8.5f, GREY)
            y -= 9f * MM

            field("Licence holder", data.licenceHolderName)
            field("Licence number", data.licenceNumber)
            field("Issuing authority", data.issuingAuthority)
            field("Generated", data.generatedDate)
            field("Signing method", "${data.method} — ${data.keyStorage}")
            field("Authentication", data.authentication)
            field("Certificate subject", data.certificateSubject)
            field("Certificate fingerprint (SHA-256)", data.fingerprint)

            y -= 2f * MM
            text(L, y, "HOW TO CHECK A CERTIFICATE SIGNED WITH THIS KEY", HELVETICA_BOLD, 8.5f, BLACK)
            y -= 6f * MM
            VERIFICATION_STEPS.forEach { step ->
                para(step, size = 9f, leadingMm = 4.2f)
                y -= 2.5f * MM
            }

            y -= 3f * MM
            text(L, y, "CERTIFICATE (PEM)", HELVETICA, 7f, GREY)
            y -= 5f * MM
            data.certificatePem.trim().lines().forEach { line ->
                text(L, y, line, COURIER, 7.5f, BLACK)
                y -= 3.6f * MM
            }
        }
    }
}
