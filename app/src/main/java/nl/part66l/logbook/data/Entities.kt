package nl.part66l.logbook.data

import androidx.room.*
import nl.part66l.logbook.domain.*
import java.time.LocalDate
import java.time.Instant

// ---------------------------------------------------------------------------
// Aircraft
// ---------------------------------------------------------------------------

/**
 * Deliberately thin. Keyed on manufacturer + serial because registrations change
 * and serials do not. Propulsion and structure are the only similarity attributes
 * modelled — see Enums.kt. The app never stores running totals of hours or launches —
 * those are readings recorded on each work entry.
 */
@Entity(
    tableName = "aircraft",
    indices = [Index(value = ["manufacturer", "serialNumber"], unique = true)],
)
data class AircraftEntity(
    @PrimaryKey val id: String,
    val manufacturer: String,
    val type: String,
    val serialNumber: String,
    val propulsion: Propulsion,
    val structure: Structure,
    /** Set once for MIXED construction, which the resolver will not guess. */
    val subcategoryOverride: Subcategory? = null,
    val ownershipRelation: OwnershipRelation = OwnershipRelation.NONE,
    val notes: String? = null,
    val archived: Boolean = false,
    /** Manual display order in the aircraft list — lower shows first. Renumbered as a whole on every drag reorder. */
    val sortOrder: Int = 0,
    /** The aircraft's own Drive folder (§10), created once on first sync. Null means "not yet synced". */
    val driveFolderId: String? = null,
)

/** Registration is a dated attribute, so an old CRS still prints what it said at the time. */
@Entity(
    tableName = "aircraft_registration",
    foreignKeys = [ForeignKey(
        entity = AircraftEntity::class,
        parentColumns = ["id"],
        childColumns = ["aircraftId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("aircraftId"), Index("registrationNormalised")],
)
data class AircraftRegistrationEntity(
    @PrimaryKey val id: String,
    val aircraftId: String,
    val registration: String,
    /** Uppercased, punctuation stripped. Backs prefix search: "PH-1234" -> "PH1234". */
    val registrationNormalised: String,
    val validFrom: LocalDate,
    val validTo: LocalDate?,
)

/** An aircraft plus its current (validTo IS NULL) registration, if any — what the list screen actually needs to render a row. */
data class AircraftWithRegistration(
    @Embedded val aircraft: AircraftEntity,
    val registration: String?,
)

// ---------------------------------------------------------------------------
// People
// ---------------------------------------------------------------------------

/** Directory of helpers and workorder issuers. Minimal by design — no contacts access. */
@Entity(tableName = "person")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val licenceNumber: String? = null,
    val email: String? = null,
    val archived: Boolean = false,
)

// ---------------------------------------------------------------------------
// Work
// ---------------------------------------------------------------------------

/**
 * The atomic unit of the logbook. A CRS is one possible outcome, not the purpose.
 * Aircraft is nullable: bench and component work is still maintenance experience.
 */
@Entity(
    tableName = "work_entry",
    foreignKeys = [ForeignKey(
        entity = AircraftEntity::class,
        parentColumns = ["id"],
        childColumns = ["aircraftId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("aircraftId"), Index("provenance"), Index("workorderReferenceNormalised")],
)
data class WorkEntryEntity(
    @PrimaryKey val id: String,
    val aircraftId: String?,
    /** Short line for headers, list rows and the CRS's own header line. */
    val description: String,
    /** Optional multiline "explanation of work done" — printed under [description] on the CRS. */
    val explanation: String? = null,
    val supervisedAnother: Boolean = false,

    // Readings at the time of work — not counters.
    val airframeHoursAtWork: Double? = null,
    val launchesAtWork: Int? = null,

    // Workorder as a value object (§5.4). Free text plus an optional attachment.
    val workorderIssuerName: String? = null,
    val workorderDate: LocalDate? = null,
    val workorderRequestedWork: String? = null,
    /** Job/work order number. AMC1 ML.A.801(e)(d) wants a unique cross-reference to the work pack. */
    val workorderReference: String? = null,
    val workorderReferenceNormalised: String? = null,
    val workorderAttachmentId: String? = null,

    /**
     * Annual inspection including follow-up maintenance. Entry metadata, never
     * printed on the CRS, so a mis-tick stays correctable after signing.
     */
    val annualInspection: Boolean = false,

    /** Annual inspection performed concurrently with an airworthiness review. */
    val concurrentWithArc: Boolean = false,

    val provenance: Provenance = Provenance.NATIVE,
    val externalId: String? = null,
    /** Overrides the distinct-session-date count when set — e.g. two sessions on one calendar day that should still count as two. Null means "use the calculated value". */
    val daysWorkedOverride: Int? = null,
    val createdAt: Instant,
    val updatedAt: Instant,

    /** This entry's own session folder in Drive (§10), created once on first sync. Null means "not yet synced". */
    val driveFolderId: String? = null,
    /** The nested "photos" subfolder under [driveFolderId] — created lazily, only once a photo on this entry actually needs uploading. */
    val drivePhotosFolderId: String? = null,
)

/**
 * Time log. One job spans days; one day covers several aircraft. Duration is
 * recorded in days or partial-days per AMC 66.A.20(b)(2) and is never required —
 * hours do not enter the compliance calculation. Route A counts distinct dates.
 */
@Entity(
    tableName = "work_session",
    foreignKeys = [ForeignKey(
        entity = WorkEntryEntity::class,
        parentColumns = ["id"],
        childColumns = ["entryId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("entryId"), Index("date")],
)
data class WorkSessionEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val date: LocalDate,
    val partialDays: Double? = null,
    val hours: Double? = null,
)

/** One entry can genuinely be more than one activity type at once (e.g. troubleshooting AND repairing). */
@Entity(
    tableName = "work_entry_activity_type",
    primaryKeys = ["entryId", "activityType"],
    foreignKeys = [ForeignKey(WorkEntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entryId")],
)
data class WorkEntryActivityTypeEntity(
    val entryId: String,
    val activityType: ActivityType,
)

/**
 * An entry plus its latest session date, current aircraft registration, and its
 * activity types (comma-joined — Room can't project a one-to-many relation as a
 * List column directly) — what the list screen renders.
 */
data class WorkEntryListRow(
    @Embedded val entry: WorkEntryEntity,
    val workDate: LocalDate?,
    val aircraftRegistration: String?,
    val activityTypesCsv: String?,
) {
    val activityTypes: List<ActivityType>
        get() = activityTypesCsv?.split(",")?.filter { it.isNotBlank() }?.map(ActivityType::valueOf) ?: emptyList()
}

/** Helpers named on the entry and, later, in the record block of the CRS. */
@Entity(
    tableName = "entry_helper",
    primaryKeys = ["entryId", "personId"],
    foreignKeys = [
        ForeignKey(WorkEntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(PersonEntity::class, ["id"], ["personId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("personId")],
)
data class EntryHelperEntity(
    val entryId: String,
    val personId: String,
    val role: HelperRole,
)

/**
 * Maintenance data used. AMC1 ML.A.801(e)(b) requires the revision status to be
 * indicated, so revision is not optional in practice.
 */
@Entity(
    tableName = "documentation_ref",
    foreignKeys = [ForeignKey(WorkEntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entryId"), Index("referenceNormalised")],
)
data class DocumentationRefEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val reference: String,
    val referenceNormalised: String,
    val category: DocumentCategory? = null,
    val revision: String?,
    val revisionDate: LocalDate?,
)

@Entity(
    tableName = "part_used",
    foreignKeys = [ForeignKey(WorkEntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entryId"), Index("partNumberNormalised")],
)
data class PartUsedEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val partNumber: String,
    val partNumberNormalised: String,
    val description: String? = null,
    val batchOrSerial: String? = null,
    val formOneRef: String? = null,
    val quantity: String? = null,
)

/**
 * Master directory of documentation (§5.3's "documentation used") — a Manual, TCDS, AD,
 * SD or Regulation the user refers to repeatedly, so its reference and revision don't need
 * retyping on every entry. Picking one on an entry snapshots its name/revision at that
 * moment into [DocumentationRefEntity], same as [CatalogueTaskEntity] snapshots into
 * [TaskCompletionEntity] — archiving or editing a document here never rewrites a past entry.
 */
@Entity(tableName = "document")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: DocumentCategory,
    val revision: String? = null,
    val revisionDate: LocalDate? = null,
    /** Optional link to the document itself (a cloud file, manufacturer page, etc.). */
    val link: String? = null,
    /** Absolute path of a PDF copied into app-private storage. PDF only, for now. */
    val pdfPath: String? = null,
    /** Original filename, for display — [pdfPath] itself is a generated UUID name. */
    val pdfFileName: String? = null,
    val archived: Boolean = false,
    /** Google Drive backup (§10), created once on first sync. Null means "not yet synced". */
    val driveFileId: String? = null,
)

// ---------------------------------------------------------------------------
// Attachments
// ---------------------------------------------------------------------------

/**
 * Photos and scanned workorders. Hashed once, after any downscaling, and never
 * re-encoded afterwards — a later re-encode silently breaks every CRS manifest
 * that references the file.
 */
@Entity(
    tableName = "attachment",
    foreignKeys = [ForeignKey(WorkEntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entryId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,          // UUID; also the on-disk and Drive filename
    val entryId: String,
    val kind: String,                    // "PHOTO" | "WORKORDER"
    val caption: String? = null,
    val sha256: String,
    val capturedAt: Instant?,
    val localPath: String,
    val driveFileId: String? = null,
    val bytes: Long,
)

// ---------------------------------------------------------------------------
// Certificate of release to service
// ---------------------------------------------------------------------------

/**
 * Immutable once signed. The signed PDF bytes are the record; snapshotJson freezes
 * everything the document asserted at the moment of signing. Corrections are made
 * by issuing a new CRS that references the old one — never by editing.
 */
@Entity(
    tableName = "crs",
    foreignKeys = [ForeignKey(WorkEntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["number"], unique = true), Index("entryId"), Index("numberNormalised"), Index("sequence")],
)
data class CrsEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val number: String,
    val numberNormalised: String,
    /** The number without any "-revN" suffix — shared by every revision of the same certificate, so a later regeneration can find it. */
    val baseNumber: String = number,
    /** 0 for the original; 1+ for each reprint of the same base number ("-rev1", "-rev2", ...). Never re-allocates the underlying sequence. */
    val revision: Int = 0,
    /** Sequence part, stored separately so "CRS 7" finds CRS-2026-0007 despite leading zeros. */
    val sequence: Int,
    val year: Int?,
    val basis: CertificationBasis,
    val statementVersion: String,
    val completionDate: LocalDate,
    val limitations: String? = null,
    val maintenanceIncomplete: Boolean = false,
    val signatureState: SignatureState,
    val supersedesCrsId: String? = null,
    val voidReason: String? = null,

    /** Frozen snapshot of everything the document asserted. */
    val snapshotJson: String,

    /** Signature intent: who, when, device, auth method, app version. */
    val signedAt: Instant? = null,
    val signedDevice: String? = null,
    val signedAuthMethod: String? = null,
    val signedAppVersion: String? = null,

    /**
     * The signing certificate, archived with the record. Hardware keys cannot be
     * backed up, so a lost phone means a lost key — but every CRS it signed must
     * still verify. The certificate therefore lives in the record and the export
     * bundle, not only inside the PDF.
     */
    val signingCertificatePem: String? = null,
    val signingCertificateFingerprint: String? = null,

    /** Which time-stamping authority was used, and when. Null while pending. */
    val timestampAuthority: String? = null,
    val timestampedAt: Instant? = null,

    val pdfLocalPath: String? = null,
    val pdfSha256: String? = null,
    val driveFileId: String? = null,

    /** A photo of the hand-signed paper copy, for the print-and-wet-sign path — not part of the certified PDF content itself. */
    val signedPhotoLocalPath: String? = null,
)

/**
 * The latest revision of one issued certificate (§5.6: "a correction is a new row, never an
 * edit" — this is what that later revision superseded), across every work entry app-wide —
 * what the "CRS Library" screen renders. Helper names are comma-joined, same reasoning as
 * [WorkEntryListRow.activityTypesCsv] — Room can't project a one-to-many relation as a List
 * column directly.
 */
data class IssuedCrsRow(
    @Embedded val crs: CrsEntity,
    val aircraftId: String?,
    val aircraftRegistration: String?,
    val helperNamesCsv: String?,
) {
    val helperNames: List<String>
        get() = helperNamesCsv?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
}

/**
 * One generation of the local signing key (§9.3) — a durable history the Keystore itself
 * doesn't keep: once a key is deleted (rotated, or destroyed by Android after a biometric
 * enrolment change), it's gone from the Keystore for good, but this row survives, so
 * "was fingerprint X valid on this date" stays answerable long after the key it names is
 * gone. [certificatePem] is a frozen copy for the same reason — [CrsEntity] already keeps
 * its own copy per certificate too, so nothing about an already-signed CRS depends on this
 * table, but this table is the one place that knows the key's own generated/retired dates
 * rather than just "some certificate that happened to sign one particular CRS."
 */
@Entity(tableName = "signing_key")
data class SigningKeyEntity(
    @PrimaryKey val id: String,
    val fingerprint: String,
    val certificatePem: String,
    val keyStorage: String,
    val generatedAt: Instant,
    /** Null while this is the active key. */
    val retiredAt: Instant? = null,
    /** e.g. "Rotated by user", "Invalidated by a change to enrolled biometrics". Null while active. */
    val retiredReason: String? = null,
)

/**
 * A note in the engineer's record — never a statement about the aircraft's
 * airworthiness. No due dates, no reminders, no computed status.
 */
@Entity(
    tableName = "deferred_item",
    foreignKeys = [ForeignKey(CrsEntity::class, ["id"], ["raisedByCrsId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("raisedByCrsId")],
)
data class DeferredItemEntity(
    @PrimaryKey val id: String,
    val raisedByCrsId: String,
    val description: String,
    val userNote: String? = null,       // free text only; nothing is computed from it
    val closed: Boolean = false,
    val closedByEntryId: String? = null,
    val closedDate: LocalDate? = null,
)

// ---------------------------------------------------------------------------
// Catalogue and proficiency
// ---------------------------------------------------------------------------

/**
 * A task from Appendix II to AMC to Annex III. IDs are section-scoped and
 * explicitly assigned: "Weighing, weight & balance sheet" appears twice in
 * Table B under different sections, so text hashing is not a valid ID scheme.
 */
@Entity(
    tableName = "catalogue_task",
    indices = [Index("catalogueVersion"), Index("section"), Index("sectionCode")],
)
data class CatalogueTaskEntity(
    @PrimaryKey val id: String,
    val catalogueVersion: String,
    val table: String,                   // "B" or "A_ENGINE"
    val section: String,
    /** Short section identifier ("GEN", "WFAB", ...) — what Route B's per-section coverage groups on. */
    val sectionCode: String,
    val text: String,
    /** §6: "the reference is displayed, not just recorded" — printed on the task list and in the recency report. */
    val reference: String,
    val appliesToL1: Boolean,
    val appliesToL1C: Boolean,
    val appliesToL2: Boolean,
    val appliesToL2C: Boolean,
    val supersedes: String? = null,
    val status: RuleStatus = RuleStatus.IN_FORCE,
)

/**
 * Completion of a catalogue task, linked to the work entry that evidenced it —
 * which is exactly the column set the ILT and BAZL logbooks use. Stores a snapshot
 * of the task text so a later rewording never breaks history.
 */
@Entity(
    tableName = "task_completion",
    foreignKeys = [ForeignKey(WorkEntryEntity::class, ["id"], ["entryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("entryId"), Index("taskId")],
)
data class TaskCompletionEntity(
    @PrimaryKey val id: String,
    val entryId: String,
    val taskId: String,
    val catalogueVersion: String,
    val taskTextSnapshot: String,
    /** Substitute task per AMC 66.A.45(h): relevant tasks may replace listed ones. */
    val substituteText: String? = null,
    val substituteJustification: String? = null,
)

// ---------------------------------------------------------------------------
// The user
// ---------------------------------------------------------------------------

/** Single profile per installation. Enforced, and stated in the EULA. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: String = "self",
    val name: String,
    val phoneNumber: String? = null,
    val email: String? = null,
    val licenceNumber: String?,
    val issuingAuthority: String?,
    val licenceValidFrom: LocalDate? = null,
    val licenceExpiry: LocalDate?,
    /** Starts the 24-month recency grace period — see RecencyEvaluator.routeInitialGrace. */
    val initialCertificationDate: LocalDate? = null,
    val holdsL1: Boolean = false,
    val holdsL1C: Boolean = false,
    val holdsL2: Boolean = false,
    val holdsL2C: Boolean = false,
    val limitationsAndRatings: String? = null,
    val pilotLicenceNumber: String? = null,
    val preferredBasis: CertificationBasis? = null,

    /**
     * 50% reduction of the 100-day requirement, which AMC 66.A.20(b)(2) permits
     * only when agreed in advance by the competent authority. Default false,
     * never inferred, and mutually exclusive with the 20% substitution allowance.
     */
    val recencyReductionGranted: Boolean = false,
    val recencyReductionAuthority: String? = null,
    val recencyReductionReference: String? = null,
    val recencyReductionDate: LocalDate? = null,

    /**
     * An entry whose only [ActivityType] is [ActivityType.RESEARCH_AND_PAPERWORK] has no
     * basis in AMC 66.A.20(b)(2)'s activity list, so by default its session dates don't
     * feed Route A. Default false, never inferred — same posture as [recencyReductionGranted].
     */
    val researchCountsTowardRecency: Boolean = false,
)
