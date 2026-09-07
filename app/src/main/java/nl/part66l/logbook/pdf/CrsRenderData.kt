package nl.part66l.logbook.pdf

/**
 * Everything [CrsPdfRenderer] needs to draw one certificate. Deliberately not
 * [nl.part66l.logbook.data.CrsEntity] itself: the entity stores a frozen JSON
 * snapshot (§5.6), and mapping that snapshot to render-ready strings — resolving
 * the certification-basis-keyed statement text, formatting dates as
 * "14 March 2026", computing the work period's day count — is a separate,
 * not-yet-built concern. This is purely what appears on the page, already formatted.
 *
 * [statement] and [regulationFooter] are passed in rather than hardcoded: per
 * invariant 3, the prescribed statement is catalogue data keyed by certification
 * basis, never a string baked into rendering code.
 */
data class CrsRenderData(
    val number: String,
    val basisLabel: String,             // e.g. "Independent certifying staff — ML.A.801(b)(2)"
    val aircraft: List<Pair<String, String>>,
    val description: String,
    val period: List<Pair<String, String>>,
    val documentation: List<DocRow>,
    val parts: List<PartRow>,
    /** Always rendered, even when empty — an absent section reads as an omission (crs-field-mapping.md). */
    val limitations: String,
    val statement: String,              // the certification statement itself, issuer's name NOT yet inlined
    val issuer: String,
    val licenceNumber: String,
    val issuedDate: String,             // already formatted and unambiguous, e.g. "14 March 2026"
    val regulationFooter: String,
    val personnel: List<PersonnelRow>,
    val photos: List<PhotoRow>,
)

data class DocRow(val reference: String, val revision: String, val date: String)
data class PartRow(val partNumber: String, val batchOrSerial: String, val releaseDocument: String)
data class PersonnelRow(val name: String, val licenceNumber: String, val role: String)
data class PhotoRow(val fileName: String, val sha256Prefix: String, val capturedAt: String)
