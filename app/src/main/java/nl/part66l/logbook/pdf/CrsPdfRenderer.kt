package nl.part66l.logbook.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB

/**
 * Renders a Certificate of Release to Service, laid out per docs/crs/crs-field-mapping.md
 * and checked against docs/crs/sample-crs.pdf and sample-crs-overflow.pdf — this is a
 * direct port of docs/crs/render_sample.py's layout logic (the sample PDFs' own source),
 * onto PdfBox-Android in place of reportlab.
 *
 * Not yet implemented: the SIGNED_LOCAL/SIGNED_QES signature widget (a visible annotation
 * naming the method, time and certificate subject) and PDF/A output. Both need pieces that
 * don't exist yet — a real [nl.part66l.logbook.signing.CrsSigner] to describe, and PDF/A
 * metadata/output-intent machinery neither this port nor the Python reference attempts.
 * What's here matches the "blank signature area, for print and wet-signing"
 * (`ISSUED_UNSIGNED_PRINT`) case in both samples.
 */
class CrsPdfRenderer {

    /** Renders into [document], returning the page count. Two passes internally for "Page n of m" (see [render]). */
    fun render(document: PDDocument, data: CrsRenderData): Int {
        val counting = PDDocument()
        val total = try {
            build(counting, data, total = null)
        } finally {
            counting.close()
        }
        return build(document, data, total = total)
    }

    private fun build(document: PDDocument, data: CrsRenderData, total: Int?): Int {
        val w = PageWriter(document, data.number, total)

        w.text(L, w.y, "Certificate of Release to Service", HELVETICA_BOLD, 15f, BLACK)
        w.textRight(R, w.y + 1f, data.number, HELVETICA_BOLD, 10f, GREY)
        w.y -= 5f * MM
        w.text(L, w.y, data.basisLabel, HELVETICA, 8f, BLACK)
        w.y -= 3.5f * MM
        w.rule(6f)

        w.heading("Aircraft")
        w.fields(data.aircraft)

        w.heading("Maintenance carried out")
        w.para(data.description)
        w.y -= 2f * MM

        w.heading("Work period")
        w.fields(data.period)

        w.heading("Maintenance data used")
        if (data.documentation.isEmpty()) {
            w.para("None.")
        } else {
            w.rows(
                listOf("Reference", "Category", "Revision", "Date"),
                data.documentation.map { listOf(it.reference, it.category, it.revision, it.date) },
                listOf(55f, 25f, 40f, 40f),
            )
        }

        w.heading("Parts and materials installed")
        if (data.parts.isEmpty()) {
            w.para("None.")
        } else {
            w.rows(
                listOf("Part number", "Description", "Batch / serial", "Release document"),
                data.parts.map { listOf(it.partNumber, it.description, it.batchOrSerial, it.releaseDocument) },
                listOf(40f, 55f, 35f, 35f),
            )
        }

        w.heading("Limitations to airworthiness or operations")
        w.para(data.limitations)

        // -- certification, kept together ---------------------------------
        val statementLine = "${data.issuer}, holder of aircraft maintenance licence ${data.licenceNumber}, ${data.statement}"
        w.need(certificationHeight(w, statementLine, data.regulationFooter))
        w.y -= 4f * MM
        w.rule(6f, weight = 1.0f, dark = true)
        w.text(L, w.y, "CERTIFICATION", HELVETICA_BOLD, 8f, BLACK)
        w.y -= 6f * MM
        w.para(statementLine, size = 9.5f, leadingMm = 4.8f)
        w.y -= 2f * MM
        w.fields(listOf("Licence number" to data.licenceNumber, "Date of issue" to data.issuedDate))
        w.y -= 5f * MM
        w.strokeLine(L, w.y, L + 75f * MM, w.y, RULE_GREY, 0.4f)
        w.y -= 3.5f * MM
        w.text(L, w.y, "SIGNATURE", HELVETICA, 6.5f, GREY)
        w.y -= 5.5f * MM
        w.para(data.regulationFooter, size = 6.8f, leadingMm = 3.4f, color = GREY)

        // -- after the release ----------------------------------------------
        if (data.personnel.isNotEmpty()) {
            w.y -= 4f * MM
            w.heading("Personnel who carried out the work")
            w.need(8f)
            w.text(L, w.y, "Record purposes only — ML.A.801(d). Certification is by the signatory above.", HELVETICA_OBLIQUE, 7f, GREY)
            w.y -= 5.5f * MM
            w.rows(
                listOf("Name", "Licence number", "Role"),
                data.personnel.map { listOf(it.name, it.licenceNumber, it.role) },
                listOf(70f, 50f, 45f),
            )
        }

        if (data.activities.isNotEmpty() || data.completedTasks.isNotEmpty()) {
            w.heading("Activities and tasks")
            if (data.activities.isNotEmpty()) {
                w.para("Activities: " + data.activities.joinToString(", "))
            }
            if (data.completedTasks.isNotEmpty()) {
                w.need(8f)
                w.text(L, w.y, "Appendix II tasks checked:", HELVETICA, 9f, BLACK)
                w.y -= 4.3f * MM
                data.completedTasks.forEach { w.para("- $it") }
            }
        }

        if (data.photos.isNotEmpty()) {
            w.heading("Photographic record, held separately")
            w.rows(
                listOf("File", "SHA-256 (first 16)", "Captured"),
                data.photos.map { listOf(it.fileName, it.sha256Prefix, it.capturedAt) },
                listOf(70f, 60f, 35f),
            )
            w.need(6f)
            w.text(L, w.y, "Photographs are bound to this certificate by the hashes listed above.", HELVETICA, 6.8f, GREY)
            w.y -= 5f * MM
        }

        if (data.workOrders.isNotEmpty()) {
            w.forceNewPage()
            w.heading("Work Order")
            w.rows(
                listOf("Issuer", "Date", "Requested work", "Reference"),
                data.workOrders.map { listOf(it.issuer, it.date, it.requestedWork, it.reference) },
                listOf(40f, 25f, 65f, 35f),
            )
        }

        return w.finish()
    }

    /** Height of the whole certification block, so [PageWriter.need] can keep it together. */
    private fun certificationHeight(w: PageWriter, statementLine: String, regulationFooter: String): Float {
        val lines = w.wrap(statementLine, HELVETICA, 9.5f).size
        val regLines = w.wrap(regulationFooter, HELVETICA, 6.8f).size
        return 4f + 6f + 6f + lines * 4.8f + 2f + 9.5f + 5f + 3.5f + 5.5f + regLines * 3.4f + 3f
    }

    companion object {
        /** 1 mm in PDF points. */
        const val MM = 72f / 25.4f

        private val PAGE: PDRectangle = PDRectangle.A4
        private const val MARGIN_MM = 20f
        val L = MARGIN_MM * MM
        val R = PAGE.width - MARGIN_MM * MM
        val COLW = R - L
        val TOP = PAGE.height - MARGIN_MM * MM
        private const val BOTTOM_MM = 22f
        val BOTTOM = BOTTOM_MM * MM

        // PDPageContentStream colour setters take a plain RGB FloatArray (0..1 per
        // component) here — not java.awt.Color, which resolves to something other
        // than PdfBox-Android's own AWTColor overload and fails to compile.
        val GREY = floatArrayOf(0.45f, 0.45f, 0.45f)
        val RULE_GREY = floatArrayOf(0.75f, 0.75f, 0.75f)
        val BLACK = floatArrayOf(0f, 0f, 0f)

        val HELVETICA: PDFont = PDType1Font.HELVETICA
        val HELVETICA_BOLD: PDFont = PDType1Font.HELVETICA_BOLD
        val HELVETICA_OBLIQUE: PDFont = PDType1Font.HELVETICA_OBLIQUE
    }
}

/**
 * Page/cursor bookkeeping equivalent to render_sample.py's `Doc` class — reportlab's single
 * long-lived `Canvas` becomes explicit PDPage/PDPageContentStream management here, since
 * PdfBox ties a content stream to one page at a time.
 */
private class PageWriter(
    private val document: PDDocument,
    private val number: String,
    private val total: Int?,
) {
    var page = 1
    var y: Float = CrsPdfRenderer.TOP

    private var stream: PDPageContentStream = openPage()

    private fun openPage(): PDPageContentStream {
        val pdPage = PDPage(PDRectangle.A4)
        document.addPage(pdPage)
        return PDPageContentStream(document, pdPage)
    }

    fun text(x: Float, y: Float, string: String, font: PDFont, size: Float, color: FloatArray) {
        // The deprecated FloatArray colour setter doesn't assume a colour space —
        // it must be set explicitly first, every time, or it throws.
        stream.setNonStrokingColorSpace(PDDeviceRGB.INSTANCE)
        stream.setNonStrokingColor(color)
        stream.beginText()
        stream.setFont(font, size)
        stream.newLineAtOffset(x, y)
        stream.showText(string)
        stream.endText()
    }

    fun textRight(x: Float, y: Float, string: String, font: PDFont, size: Float, color: FloatArray) {
        text(x - stringWidth(string, font, size), y, string, font, size, color)
    }

    fun stringWidth(text: String, font: PDFont, size: Float): Float =
        font.getStringWidth(text) / 1000f * size

    fun strokeLine(x0: Float, y0: Float, x1: Float, y1: Float, color: FloatArray, weight: Float) {
        stream.setStrokingColorSpace(PDDeviceRGB.INSTANCE)
        stream.setStrokingColor(color)
        stream.setLineWidth(weight)
        stream.moveTo(x0, y0)
        stream.lineTo(x1, y1)
        stream.stroke()
    }

    fun rule(gapMm: Float = 3f, weight: Float = 0.4f, dark: Boolean = false) {
        strokeLine(CrsPdfRenderer.L, y, CrsPdfRenderer.R, y, if (dark) CrsPdfRenderer.BLACK else CrsPdfRenderer.RULE_GREY, weight)
        y -= gapMm * CrsPdfRenderer.MM
    }

    fun footer() {
        text(CrsPdfRenderer.L, 14f * CrsPdfRenderer.MM, number, CrsPdfRenderer.HELVETICA, 6.5f, CrsPdfRenderer.GREY)
        val label = if (total != null) "Page $page of $total" else "Page $page"
        textRight(CrsPdfRenderer.R, 14f * CrsPdfRenderer.MM, label, CrsPdfRenderer.HELVETICA, 6.5f, CrsPdfRenderer.GREY)
    }

    private fun newPage() {
        footer()
        stream.close()
        page += 1
        stream = openPage()
        y = CrsPdfRenderer.TOP
        text(CrsPdfRenderer.L, y, "Certificate of Release to Service — continued", CrsPdfRenderer.HELVETICA_BOLD, 9f, CrsPdfRenderer.GREY)
        textRight(CrsPdfRenderer.R, y, number, CrsPdfRenderer.HELVETICA_BOLD, 9f, CrsPdfRenderer.GREY)
        y -= 4f * CrsPdfRenderer.MM
        rule(6f)
    }

    /** Breaks to a new page unless [heightMm] of content still fits above the content floor. */
    fun need(heightMm: Float) {
        if (y - heightMm * CrsPdfRenderer.MM < CrsPdfRenderer.BOTTOM) newPage()
    }

    /** Unconditional page break — for a section that must start its own page (e.g. Work Order) regardless of remaining space. */
    fun forceNewPage() = newPage()

    fun heading(title: String, keep: Float = 12f) {
        need(keep)
        y -= 1.5f * CrsPdfRenderer.MM
        text(CrsPdfRenderer.L, y, title.uppercase(), CrsPdfRenderer.HELVETICA_BOLD, 7.5f, CrsPdfRenderer.GREY)
        y -= 1.8f * CrsPdfRenderer.MM
        rule(4f)
    }

    fun fields(pairs: List<Pair<String, String>>) {
        if (pairs.isEmpty()) return
        need(11f)
        val w = CrsPdfRenderer.COLW / pairs.size
        pairs.forEachIndexed { i, (label, value) ->
            val x = CrsPdfRenderer.L + i * w
            text(x, y, label.uppercase(), CrsPdfRenderer.HELVETICA, 6.5f, CrsPdfRenderer.GREY)
            text(x, y - 4.4f * CrsPdfRenderer.MM, value, CrsPdfRenderer.HELVETICA, 9.5f, CrsPdfRenderer.BLACK)
        }
        y -= 9.5f * CrsPdfRenderer.MM
    }

    fun wrap(text: String, font: PDFont, size: Float): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        for (word in text.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val trial = if (line.isEmpty()) word else "$line $word"
            if (stringWidth(trial, font, size) > CrsPdfRenderer.COLW) {
                if (line.isNotEmpty()) lines += line
                line = word
            } else {
                line = trial
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    fun para(
        text: String,
        font: PDFont = CrsPdfRenderer.HELVETICA,
        size: Float = 9f,
        leadingMm: Float = 4.3f,
        color: FloatArray = CrsPdfRenderer.BLACK,
    ) {
        for (line in wrap(text, font, size)) {
            need(leadingMm + 2f)
            text(CrsPdfRenderer.L, y, line, font, size, color)
            y -= leadingMm * CrsPdfRenderer.MM
        }
    }

    fun tableHeader(cols: List<String>, widthsMm: List<Float>) {
        var x = CrsPdfRenderer.L
        cols.forEachIndexed { i, head ->
            text(x, y, head.uppercase(), CrsPdfRenderer.HELVETICA_BOLD, 6.5f, CrsPdfRenderer.GREY)
            x += widthsMm[i] * CrsPdfRenderer.MM
        }
        y -= 4f * CrsPdfRenderer.MM
    }

    fun rows(cols: List<String>, data: List<List<String>>, widthsMm: List<Float>) {
        if (data.isEmpty()) return
        need(12f)
        tableHeader(cols, widthsMm)
        for (row in data) {
            if (y - 4.3f * CrsPdfRenderer.MM < CrsPdfRenderer.BOTTOM) {
                newPage()
                tableHeader(cols, widthsMm)
            }
            var x = CrsPdfRenderer.L
            row.forEachIndexed { i, cell ->
                text(x, y, cell, CrsPdfRenderer.HELVETICA, 8.5f, CrsPdfRenderer.BLACK)
                x += widthsMm[i] * CrsPdfRenderer.MM
            }
            y -= 4.3f * CrsPdfRenderer.MM
        }
        y -= 1.5f * CrsPdfRenderer.MM
    }

    fun finish(): Int {
        footer()
        stream.close()
        return page
    }
}
