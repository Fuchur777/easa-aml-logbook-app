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
    /** One row per contributing work order, rendered on its own page (only when non-empty) since a work order isn't part of the certificate content itself. */
    val workOrders: List<WorkOrderRow>,
    /** Always rendered, even when empty — an absent section reads as an omission (crs-field-mapping.md). */
    val limitations: String,
    val statement: String,              // the certification statement itself, issuer's name NOT yet inlined
    val issuer: String,
    val licenceNumber: String,
    val issuedDate: String,             // already formatted and unambiguous, e.g. "14 March 2026"
    val regulationFooter: String,
    val personnel: List<PersonnelRow>,
    /** Activity types recorded on the contributing entry/entries, e.g. "Inspection", "Repairing". */
    val activities: List<String>,
    /** Appendix II task text, snapshotted at completion time. */
    val completedTasks: List<String>,
    val photos: List<PhotoRow>,
    /**
     * Null renders today's blank ruled "SIGNATURE" line, for print-and-wet-signing.
     * Non-null renders the visible signature annotation instead (crs-field-mapping.md:
     * "a visible annotation naming the method, the signing time and the certificate
     * subject") — populated from [nl.part66l.logbook.signing.SignerDescription] plus the
     * fingerprint, all obtainable without touching the private key, so this is filled in
     * *before* the certificate ever needs to sign anything.
     */
    val signatureBlock: SignatureBlockData? = null,
)

data class DocRow(val reference: String, val category: String, val revision: String, val date: String)
data class PartRow(val partNumber: String, val description: String, val batchOrSerial: String, val releaseDocument: String)
data class PersonnelRow(val name: String, val licenceNumber: String, val role: String)
/**
 * One photo of the work (§8), for the photo appendix. [index] (1-based, stable across the
 * grid and the manifest table below it) is what's printed to identify the photo — never the
 * raw UUID filename, which means nothing to a reader. [localPath] points at the already
 * fully-processed file (downscaled, GPS EXIF stripped) — embedded as-is, never re-encoded, so
 * what's on the page is exactly what [sha256Prefix] was computed over.
 */
data class PhotoRow(val index: Int, val caption: String?, val sha256Prefix: String, val capturedAt: String, val localPath: String)
data class WorkOrderRow(val issuer: String, val date: String, val requestedWork: String, val reference: String)
data class SignatureBlockData(val method: String, val signedAtLabel: String, val certificateSubject: String, val fingerprint: String)
