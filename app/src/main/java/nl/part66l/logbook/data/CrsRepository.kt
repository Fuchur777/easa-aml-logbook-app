package nl.part66l.logbook.data

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import nl.part66l.logbook.BuildConfig
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.CertificationBasis
import nl.part66l.logbook.domain.CertificationStatements
import nl.part66l.logbook.domain.CrsNumberFormat
import nl.part66l.logbook.domain.DocumentCategory
import nl.part66l.logbook.domain.HelperRole
import nl.part66l.logbook.domain.SignatureState
import nl.part66l.logbook.pdf.CrsPdfRenderer
import nl.part66l.logbook.pdf.CrsRenderData
import nl.part66l.logbook.pdf.DocRow
import nl.part66l.logbook.pdf.PartRow
import nl.part66l.logbook.pdf.PersonnelRow
import nl.part66l.logbook.pdf.PhotoRow
import nl.part66l.logbook.pdf.SignatureBlockData
import nl.part66l.logbook.pdf.WorkOrderRow
import nl.part66l.logbook.signing.CrsPdfSigningSupport
import nl.part66l.logbook.signing.LocalKeystoreSigner

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
private val SIGNED_AT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm")

interface CrsRepository {
    /** Every CRS issued against this entry, newest first — a correction is a new row, never an edit. */
    fun forEntry(entryId: String): Flow<List<CrsEntity>>

    /** The latest revision of every issued certificate app-wide — see [CrsDao.latestIssued]. */
    fun latestIssued(
        aircraftId: String? = null,
        numberQuery: String? = null,
        helperQuery: String? = null,
        ascending: Boolean = false,
    ): Flow<List<IssuedCrsRow>>

    /**
     * Generates the §9.1 content, allocates the next number (§9.2, at generation
     * time so an abandoned attempt leaves no gap), renders the unsigned
     * print-and-wet-sign PDF (matches [CrsPdfRenderer]'s current layout — no
     * cryptographic signature yet), and records it. Null if the entry no longer exists.
     *
     * [deferredItemDescriptions], when [maintenanceIncomplete] is set, are woven into the
     * printed limitations text as a numbered list (§9.1(f)) — the caller is still responsible
     * for raising them as their own [DeferredItemEntity] rows afterward, against this call's
     * result id, so they're individually closeable later; this only controls what's printed.
     */
    suspend fun generateUnsigned(
        entryId: String,
        limitations: String?,
        maintenanceIncomplete: Boolean,
        deferredItemDescriptions: List<String> = emptyList(),
    ): CrsEntity?

    /** Attaches (or clears, with a null [path]) a photo of the hand-signed paper copy — the print-and-wet-sign path's only record of the actual signature. */
    suspend fun setSignedPhoto(id: String, path: String?)

    /**
     * Generates the same §9.1 content as [generateUnsigned], but signs it on-device (§9.3)
     * with [signer] instead of leaving it for print-and-wet-signing. [signer] must already be
     * authorized for one signature — see [nl.part66l.logbook.signing.BiometricSigningGate] —
     * this call never itself shows a biometric prompt.
     *
     * The number (§9.2) is allocated and committed as soon as a [SignatureState.DRAFT] row is
     * inserted, before anything fallible (rendering, signing) runs — so a failure partway
     * through voids that row with a reason rather than silently losing or reusing the number.
     * Null if the entry no longer exists; [Result.failure] on any other failure.
     */
    suspend fun signLocal(
        entryId: String,
        limitations: String?,
        maintenanceIncomplete: Boolean,
        deferredItemDescriptions: List<String> = emptyList(),
        signer: LocalKeystoreSigner,
    ): Result<CrsEntity>?
}

@Serializable
private data class CrsSnapshot(
    val aircraftRegistration: String?,
    val aircraftManufacturer: String?,
    val aircraftType: String?,
    val aircraftSerialNumber: String?,
    val description: String,
    val explanation: String? = null,
    val documentationRefs: List<SnapshotDocRef>,
    val partsUsed: List<SnapshotPart>,
    val helpers: List<SnapshotHelper>,
    val workorderIssuerName: String?,
    val workorderDate: String?,
    val workorderRequestedWork: String?,
    val workorderReference: String?,
    val limitations: String?,
    val photos: List<SnapshotPhoto>,
)

@Serializable
private data class SnapshotDocRef(val reference: String, val category: String?, val revision: String?, val revisionDate: String?)

@Serializable
private data class SnapshotPart(val partNumber: String, val description: String?, val batchOrSerial: String?, val formOneRef: String?)

@Serializable
private data class SnapshotHelper(val name: String, val licenceNumber: String?, val role: String)

/** [id] is the attachment id — the frozen counterpart to [PhotoRow.index]'s printed position, so the record can be tied back to a specific file even if it's later renamed or moved. */
@Serializable
private data class SnapshotPhoto(val id: String, val caption: String?, val sha256: String, val capturedAt: String?)

/** What [CrsRepositoryImpl.buildDraft] resolved for this call: either a freshly allocated number, or the next revision of an existing one. */
private data class NumberAllocation(
    val number: String,
    val baseNumber: String,
    val sequence: Int,
    val year: Int?,
    val revision: Int,
    val supersedes: String?,
)

/**
 * Everything [CrsRepositoryImpl.generateUnsigned] and [CrsRepositoryImpl.signLocal] share:
 * the §9.1 content, gathered and number-allocated once. [renderData] has no
 * [nl.part66l.logbook.pdf.SignatureBlockData] yet — [generateUnsigned] renders it as-is,
 * [signLocal] adds one (from its authorized signer's own description/fingerprint, obtainable
 * without touching the private key) before rendering.
 */
private data class CrsDraft(
    val allocation: NumberAllocation,
    val year: Int?,
    val basis: CertificationBasis,
    val completionDate: LocalDate,
    val renderData: CrsRenderData,
    val snapshotJson: String,
)

private fun HelperRole.displayLabel(): String = when (this) {
    HelperRole.ASSISTED -> "Assisted / On the job training"
    HelperRole.INDEPENDENT_INSPECTION -> "Independent inspection"
}

private fun DocumentCategory.displayLabel(): String = when (this) {
    DocumentCategory.MANUAL -> "Manual"
    DocumentCategory.TCDS -> "TCDS"
    DocumentCategory.AD -> "AD"
    DocumentCategory.SD -> "SD"
    DocumentCategory.REGULATION -> "Regulation"
}

private fun ActivityType.displayLabel(): String = when (this) {
    ActivityType.SERVICING -> "Servicing"
    ActivityType.INSPECTION -> "Inspection"
    ActivityType.OPERATIONAL_AND_FUNCTIONAL_TESTING -> "Operational & functional testing"
    ActivityType.TROUBLESHOOTING -> "Troubleshooting"
    ActivityType.REPAIRING -> "Repairing"
    ActivityType.MODIFYING -> "Modifying"
    ActivityType.CHANGING_COMPONENT -> "Changing a component"
    ActivityType.SUPERVISING -> "Supervising"
    ActivityType.RELEASING_TO_SERVICE -> "Releasing to service"
    ActivityType.RESEARCH_AND_PAPERWORK -> "Research & paperwork"
}

@Singleton
class CrsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workEntryDao: WorkEntryDao,
    private val workSessionDao: WorkSessionDao,
    private val aircraftDao: AircraftDao,
    private val profileDao: ProfileDao,
    private val entryHelperDao: EntryHelperDao,
    private val personDao: PersonDao,
    private val documentationRefDao: DocumentationRefDao,
    private val partUsedDao: PartUsedDao,
    private val taskCompletionDao: TaskCompletionDao,
    private val attachmentDao: AttachmentDao,
    private val crsDao: CrsDao,
    private val settingsRepository: SettingsRepository,
) : CrsRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun forEntry(entryId: String): Flow<List<CrsEntity>> = crsDao.forEntry(entryId)

    override fun latestIssued(aircraftId: String?, numberQuery: String?, helperQuery: String?, ascending: Boolean): Flow<List<IssuedCrsRow>> =
        crsDao.latestIssued(aircraftId, numberQuery?.trim()?.ifBlank { null }, helperQuery?.trim()?.ifBlank { null }, ascending)

    override suspend fun generateUnsigned(
        entryId: String,
        limitations: String?,
        maintenanceIncomplete: Boolean,
        deferredItemDescriptions: List<String>,
    ): CrsEntity? {
        val draft = buildDraft(entryId, limitations, maintenanceIncomplete, deferredItemDescriptions) ?: return null
        val (pdfFile, pdfSha256) = renderAndSave(draft.renderData)
        val crs = CrsEntity(
            id = UUID.randomUUID().toString(),
            entryId = entryId,
            number = draft.allocation.number,
            numberNormalised = Identifiers.normalise(draft.allocation.number),
            baseNumber = draft.allocation.baseNumber,
            revision = draft.allocation.revision,
            sequence = draft.allocation.sequence,
            year = draft.year,
            basis = draft.basis,
            statementVersion = CertificationStatements.VERSION,
            completionDate = draft.completionDate,
            limitations = limitations,
            maintenanceIncomplete = maintenanceIncomplete,
            supersedesCrsId = draft.allocation.supersedes,
            signatureState = SignatureState.ISSUED_UNSIGNED_PRINT,
            snapshotJson = draft.snapshotJson,
            pdfLocalPath = pdfFile.absolutePath,
            pdfSha256 = pdfSha256,
        )
        crsDao.insert(crs)
        return crs
    }

    override suspend fun signLocal(
        entryId: String,
        limitations: String?,
        maintenanceIncomplete: Boolean,
        deferredItemDescriptions: List<String>,
        signer: LocalKeystoreSigner,
    ): Result<CrsEntity>? {
        val draft = buildDraft(entryId, limitations, maintenanceIncomplete, deferredItemDescriptions) ?: return null
        val id = UUID.randomUUID().toString()
        crsDao.insert(
            CrsEntity(
                id = id,
                entryId = entryId,
                number = draft.allocation.number,
                numberNormalised = Identifiers.normalise(draft.allocation.number),
                baseNumber = draft.allocation.baseNumber,
                revision = draft.allocation.revision,
                sequence = draft.allocation.sequence,
                year = draft.year,
                basis = draft.basis,
                statementVersion = CertificationStatements.VERSION,
                completionDate = draft.completionDate,
                limitations = limitations,
                maintenanceIncomplete = maintenanceIncomplete,
                supersedesCrsId = draft.allocation.supersedes,
                signatureState = SignatureState.DRAFT,
                snapshotJson = draft.snapshotJson,
            ),
        )

        return try {
            val description = signer.describe()
            val fingerprint = signer.fingerprint()
            val signedAt = Instant.now()
            val signedRenderData = draft.renderData.copy(
                signatureBlock = SignatureBlockData(
                    method = "${description.method} — ${description.keyStorage}",
                    signedAtLabel = signedAt.atZone(ZoneId.systemDefault()).format(SIGNED_AT_FORMAT),
                    certificateSubject = description.certificateSubject.orEmpty(),
                    fingerprint = fingerprint,
                ),
            )
            // The render/save digest is discarded: signInPlace appends signature bytes to
            // pdfFile in place afterward, so only a digest taken after that call is final.
            val (pdfFile, _) = renderAndSave(signedRenderData)
            CrsPdfSigningSupport.signInPlace(pdfFile, signer)
            val signedPdfSha256 = MessageDigest.getInstance("SHA-256").digest(pdfFile.readBytes()).joinToString("") { "%02x".format(it) }

            val rows = crsDao.finalizeSigned(
                id = id,
                state = SignatureState.SIGNED_LOCAL,
                signedAt = signedAt,
                signedDevice = android.os.Build.MODEL ?: "unknown device",
                signedAuthMethod = description.authentication,
                signedAppVersion = BuildConfig.VERSION_NAME,
                signingCertificatePem = signer.certificatePem(),
                signingCertificateFingerprint = fingerprint,
                pdfLocalPath = pdfFile.absolutePath,
                pdfSha256 = signedPdfSha256,
            )
            check(rows == 1) { "CRS draft $id was not in DRAFT state when finalizing the signature" }
            Result.success(requireNotNull(crsDao.byId(id)))
        } catch (e: Exception) {
            crsDao.transitionUnsigned(id, SignatureState.VOID, reason = e.message ?: e::class.simpleName)
            Result.failure(e)
        }
    }

    private fun renderAndSave(renderData: CrsRenderData): Pair<File, String> {
        val document = PDDocument()
        val pdfFile: File
        try {
            CrsPdfRenderer().render(document, renderData, context)
            val destDir = File(context.filesDir, "crs").apply { mkdirs() }
            pdfFile = File(destDir, "${UUID.randomUUID()}.pdf")
            document.save(pdfFile)
        } finally {
            document.close()
        }
        val pdfSha256 = MessageDigest.getInstance("SHA-256").digest(pdfFile.readBytes()).joinToString("") { "%02x".format(it) }
        return pdfFile to pdfSha256
    }

    private suspend fun buildDraft(
        entryId: String,
        limitations: String?,
        maintenanceIncomplete: Boolean,
        deferredItemDescriptions: List<String>,
    ): CrsDraft? {
        val entry = workEntryDao.byId(entryId) ?: return null
        val profile = profileDao.get()
        val sessions = workSessionDao.forEntry(entryId)
        val documentationRefs = documentationRefDao.forEntry(entryId)
        val partsUsed = partUsedDao.forEntry(entryId)
        val helpers = entryHelperDao.forEntry(entryId).mapNotNull { helper ->
            personDao.byId(helper.personId)?.let { person -> Triple(person.name, person.licenceNumber, helper.role) }
        }
        val activityTypes = workEntryDao.activityTypesForEntry(entryId).map { it.activityType }
        val completedTasks = taskCompletionDao.forEntry(entryId).map { it.taskTextSnapshot }
        // Capture order, not attach order — a stable, meaningful sequence for the printed index.
        val photos = attachmentDao.forEntry(entryId).filter { it.kind == "PHOTO" }.sortedBy { it.capturedAt }

        val aircraft = entry.aircraftId?.let { aircraftDao.byId(it) }
        val registration = entry.aircraftId?.let { aircraftDao.registrationOn(it, LocalDate.now()) }

        val completionDate = sessions.maxOfOrNull { it.date } ?: LocalDate.now()
        val workStarted = sessions.minOfOrNull { it.date } ?: completionDate
        val daysWorked = entry.daysWorkedOverride ?: sessions.map { it.date }.distinct().size

        // A re-generation for an entry that already has a CRS is a revision of the same
        // certificate, not a new one (crs-field-mapping.md: "a correction is a new row,
        // never an edit") — it keeps the original's base number and year, and gets the
        // next "-revN" suffix, rather than consuming a fresh number from the sequence.
        val previousForEntry = crsDao.forEntry(entryId).first()
        val allocation = if (previousForEntry.isEmpty()) {
            // Numbers are allocated here, at generation time, not when a draft is
            // first opened — an abandoned attempt must leave no gap (§9.2).
            val format = CrsNumberFormat(
                template = settingsRepository.crsNumberTemplate.first(),
                annualReset = settingsRepository.crsAnnualReset.first(),
                startAt = settingsRepository.crsStartAt.first(),
            )
            val year = completionDate.year
            // {REG} resolves to the normalised registration, or NOREG for component/bench work — the
            // sequence itself stays shared across every registration, so {REG} only varies the printed number.
            val registrationToken = registration?.let { Identifiers.normalise(it) } ?: "NOREG"
            val sequence = format.nextSequence(crsDao.allNumbers(), year)
            val baseNumber = format.format(sequence, year, registrationToken)
            NumberAllocation(number = baseNumber, baseNumber = baseNumber, sequence = sequence, year = year, revision = 0, supersedes = null)
        } else {
            val previous = previousForEntry.maxByOrNull { it.revision }!!
            val revision = previous.revision + 1
            NumberAllocation(
                number = "${previous.baseNumber}-rev$revision",
                baseNumber = previous.baseNumber,
                sequence = previous.sequence,
                year = previous.year,
                revision = revision,
                supersedes = previous.id,
            )
        }
        val number = allocation.number
        val year = allocation.year

        val basis = CertificationBasis.ML_A_801_B2_INDEPENDENT
        val issuer = profile?.name.orEmpty()
        val licenceNumber = profile?.licenceNumber.orEmpty()
        // Opt-in (Settings) — the certifying staff's phone/email are personal data, not printed by default.
        val showContact = settingsRepository.crsShowCertifyingStaffContact.first()
        val issuerPhone = profile?.phoneNumber?.takeIf { showContact }
        val issuerEmail = profile?.email?.takeIf { showContact }

        val hoursLaunches = if (entry.airframeHoursAtWork != null || entry.launchesAtWork != null) {
            "Hours / launches" to "${entry.airframeHoursAtWork?.let { "$it h" } ?: "—"} / ${entry.launchesAtWork ?: "—"}"
        } else {
            null
        }

        // §9.1(f): where maintenance could not be completed, the CRS says so, inside the
        // limitations block rather than as a separate field (crs-field-mapping.md) — so the
        // checkbox has to actually change what's printed, not just sit on the record unseen.
        // Deferred items raised alongside it are numbered into the same paragraph — each one
        // is also its own DeferredItemEntity (raised by the caller against this call's result),
        // so the printed list and the closeable record always agree.
        val limitationsText = buildString {
            limitations?.trim()?.takeIf { it.isNotBlank() }?.let { append(it) }
            if (maintenanceIncomplete) {
                if (isNotEmpty()) append(" ")
                append("Maintenance could not be completed in full.")
            }
            if (deferredItemDescriptions.isNotEmpty()) {
                if (isNotEmpty()) append(" ")
                append("The following items remain outstanding and are deferred: ")
                append(deferredItemDescriptions.mapIndexed { i, item -> "(${i + 1}) $item" }.joinToString("; "))
                append(".")
            }
        }.ifBlank { "None." }

        val renderData = CrsRenderData(
            number = number,
            basisLabel = CertificationStatements.basisLabel(basis),
            aircraft = listOfNotNull(
                registration?.let { "Registration" to it },
                aircraft?.let { "Manufacturer" to it.manufacturer },
                aircraft?.let { "Type" to it.type },
                aircraft?.let { "Serial number" to it.serialNumber },
                hoursLaunches,
            ).ifEmpty { listOf("Scope" to "Bench / component work") },
            description = entry.description,
            explanation = entry.explanation,
            period = listOfNotNull(
                "Start" to workStarted.format(DATE_FORMAT),
                "End" to completionDate.format(DATE_FORMAT),
                "Days worked" to daysWorked.toString(),
            ),
            documentation = documentationRefs.map {
                DocRow(it.reference, it.category?.displayLabel().orEmpty(), it.revision.orEmpty(), it.revisionDate?.format(DATE_FORMAT).orEmpty())
            },
            parts = partsUsed.map { PartRow(it.partNumber, it.description.orEmpty(), it.batchOrSerial.orEmpty(), it.formOneRef.orEmpty()) },
            workOrders = listOfNotNull(
                if (entry.workorderReference != null || entry.workorderIssuerName != null) {
                    WorkOrderRow(
                        issuer = entry.workorderIssuerName.orEmpty(),
                        date = entry.workorderDate?.format(DATE_FORMAT).orEmpty(),
                        requestedWork = entry.workorderRequestedWork.orEmpty(),
                        reference = entry.workorderReference.orEmpty(),
                    )
                } else {
                    null
                },
            ),
            limitations = limitationsText,
            statement = CertificationStatements.statement(basis),
            issuer = issuer,
            licenceNumber = licenceNumber,
            issuerPhone = issuerPhone,
            issuerEmail = issuerEmail,
            issuedDate = completionDate.format(DATE_FORMAT),
            regulationFooter = CertificationStatements.regulationFooter(basis),
            personnel = listOfNotNull(
                profile?.let { PersonnelRow(it.name, it.licenceNumber.orEmpty(), "Certifying staff") },
            ) + helpers.map { (name, licence, role) -> PersonnelRow(name, licence.orEmpty(), role.displayLabel()) },
            activities = activityTypes.map { it.displayLabel() },
            completedTasks = completedTasks,
            photos = photos.mapIndexed { i, attachment ->
                PhotoRow(
                    index = i + 1,
                    caption = attachment.caption,
                    sha256Prefix = attachment.sha256.take(16),
                    capturedAt = (attachment.capturedAt ?: Instant.EPOCH).atZone(ZoneId.systemDefault()).format(SIGNED_AT_FORMAT),
                    localPath = attachment.localPath,
                )
            },
        )

        val snapshot = CrsSnapshot(
            aircraftRegistration = registration,
            aircraftManufacturer = aircraft?.manufacturer,
            aircraftType = aircraft?.type,
            aircraftSerialNumber = aircraft?.serialNumber,
            description = entry.description,
            explanation = entry.explanation,
            documentationRefs = documentationRefs.map {
                SnapshotDocRef(it.reference, it.category?.name, it.revision, it.revisionDate?.toString())
            },
            partsUsed = partsUsed.map { SnapshotPart(it.partNumber, it.description, it.batchOrSerial, it.formOneRef) },
            helpers = helpers.map { (name, licence, role) -> SnapshotHelper(name, licence, role.name) },
            workorderIssuerName = entry.workorderIssuerName,
            workorderDate = entry.workorderDate?.toString(),
            workorderRequestedWork = entry.workorderRequestedWork,
            workorderReference = entry.workorderReference,
            limitations = limitations,
            photos = photos.map { SnapshotPhoto(it.id, it.caption, it.sha256, it.capturedAt?.toString()) },
        )

        return CrsDraft(
            allocation = allocation,
            year = year,
            basis = basis,
            completionDate = completionDate,
            renderData = renderData,
            snapshotJson = json.encodeToString(snapshot),
        )
    }

    override suspend fun setSignedPhoto(id: String, path: String?) = crsDao.setSignedPhoto(id, path)
}
