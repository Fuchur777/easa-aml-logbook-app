package nl.part66l.logbook.data

import androidx.paging.PagingSource
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.EntryRole
import nl.part66l.logbook.domain.HelperRole
import nl.part66l.logbook.domain.Provenance

/** One row of the "Documentation used" list (§5.3) — reference plus revision status. */
data class DocumentationRefInput(val reference: String, val revision: String? = null)

/** One row of the "Parts and materials" list (§5.3). */
data class PartUsedInput(
    val partNumber: String,
    val description: String? = null,
    val batchOrSerial: String? = null,
    val formOneRef: String? = null,
    val quantity: String? = null,
)

/** Everything the edit form needs to repopulate itself — the mirror image of [WorkEntryRepository.create]'s parameters. */
data class WorkEntryEditData(
    val aircraftId: String?,
    val description: String,
    val activityTypes: Set<ActivityType>,
    val role: EntryRole,
    val supervisedAnother: Boolean,
    val sessionDate: LocalDate,
    val helperNames: List<String>,
    val completedTaskIds: Set<String>,
    val airframeHoursAtWork: Double?,
    val launchesAtWork: Int?,
    val workorderIssuerName: String?,
    val workorderDate: LocalDate?,
    val workorderRequestedWork: String?,
    val workorderReference: String?,
    val annualInspection: Boolean,
    val concurrentWithArc: Boolean,
    val documentationRefs: List<DocumentationRefInput>,
    val partsUsed: List<PartUsedInput>,
)

interface WorkEntryRepository {
    fun pagedAll(): PagingSource<Int, WorkEntryEntity>

    fun filtered(
        aircraftId: String? = null,
        role: EntryRole? = null,
        annualOnly: Boolean = false,
        provenance: Provenance? = null,
        from: LocalDate? = null,
        to: LocalDate? = null,
    ): PagingSource<Int, WorkEntryEntity>

    /** What the list screen renders — each entry with its latest session date, current aircraft registration, and activity types. */
    fun pagedAllWithDetails(): PagingSource<Int, WorkEntryListRow>

    /** Reassembles an entry and its child rows for the edit form. Null if the entry no longer exists. */
    suspend fun forEdit(id: String): WorkEntryEditData?

    /**
     * Creates an entry and its first work session together — a session-less entry can
     * never feed Route A. [helperNames] are resolved to the person directory by exact
     * name match, creating a new [PersonEntity] for any name not already on file.
     * [completedTaskIds] are snapshotted into [TaskCompletionEntity] rows as they read
     * today — a later catalogue update must never retroactively change what a past
     * completion said. [documentationRefs] and [partsUsed] become their own child rows,
     * same as helpers. [workorderIssuerName], if given, is also resolved into the person
     * directory (same [PersonEntity] table as helpers) so its spelling can be reused —
     * unlike helpers, the entry stores the plain name, not a person id.
     */
    suspend fun create(
        aircraftId: String?,
        description: String,
        activityTypes: Set<ActivityType>,
        role: EntryRole,
        supervisedAnother: Boolean,
        sessionDate: LocalDate,
        helperNames: List<String> = emptyList(),
        completedTaskIds: Set<String> = emptySet(),
        airframeHoursAtWork: Double? = null,
        launchesAtWork: Int? = null,
        workorderIssuerName: String? = null,
        workorderDate: LocalDate? = null,
        workorderRequestedWork: String? = null,
        workorderReference: String? = null,
        annualInspection: Boolean = false,
        concurrentWithArc: Boolean = false,
        documentationRefs: List<DocumentationRefInput> = emptyList(),
        partsUsed: List<PartUsedInput> = emptyList(),
    ): String

    /**
     * Replaces the entry's own fields and every child row (session, activity types, helpers,
     * task completions, documentation, parts) with what's given — a full-form save, not a
     * diff. A removed task completion is genuinely gone (the user is saying it wasn't done
     * after all); a kept one is re-snapshotted as of the edit, same as a fresh completion.
     */
    suspend fun update(
        id: String,
        aircraftId: String?,
        description: String,
        activityTypes: Set<ActivityType>,
        role: EntryRole,
        supervisedAnother: Boolean,
        sessionDate: LocalDate,
        helperNames: List<String> = emptyList(),
        completedTaskIds: Set<String> = emptySet(),
        airframeHoursAtWork: Double? = null,
        launchesAtWork: Int? = null,
        workorderIssuerName: String? = null,
        workorderDate: LocalDate? = null,
        workorderRequestedWork: String? = null,
        workorderReference: String? = null,
        annualInspection: Boolean = false,
        concurrentWithArc: Boolean = false,
        documentationRefs: List<DocumentationRefInput> = emptyList(),
        partsUsed: List<PartUsedInput> = emptyList(),
    )

    /** Cascades to every child row. The database itself refuses this once a CRS has been signed against the entry (FK RESTRICT) — there's no UI path to that state yet. */
    suspend fun delete(id: String)
}

@Singleton
class WorkEntryRepositoryImpl @Inject constructor(
    private val workEntryDao: WorkEntryDao,
    private val workSessionDao: WorkSessionDao,
    private val entryHelperDao: EntryHelperDao,
    private val personRepository: PersonRepository,
    private val personDao: PersonDao,
    private val catalogueDao: CatalogueDao,
    private val taskCompletionDao: TaskCompletionDao,
    private val documentationRefDao: DocumentationRefDao,
    private val partUsedDao: PartUsedDao,
) : WorkEntryRepository {

    override fun pagedAll(): PagingSource<Int, WorkEntryEntity> = workEntryDao.pagedAll()

    override fun filtered(
        aircraftId: String?,
        role: EntryRole?,
        annualOnly: Boolean,
        provenance: Provenance?,
        from: LocalDate?,
        to: LocalDate?,
    ): PagingSource<Int, WorkEntryEntity> =
        workEntryDao.filtered(aircraftId, role, annualOnly, provenance, from, to)

    override fun pagedAllWithDetails(): PagingSource<Int, WorkEntryListRow> = workEntryDao.pagedAllWithDetails()

    override suspend fun forEdit(id: String): WorkEntryEditData? {
        val entry = workEntryDao.byId(id) ?: return null
        val activityTypes = workEntryDao.activityTypesForEntry(id).map { it.activityType }.toSet()
        val session = workSessionDao.forEntry(id).firstOrNull()
        val helperNames = entryHelperDao.forEntry(id).mapNotNull { personDao.byId(it.personId)?.name }
        val completedTaskIds = taskCompletionDao.forEntry(id).map { it.taskId }.toSet()
        val documentationRefs = documentationRefDao.forEntry(id).map { DocumentationRefInput(it.reference, it.revision) }
        val partsUsed = partUsedDao.forEntry(id).map {
            PartUsedInput(it.partNumber, it.description, it.batchOrSerial, it.formOneRef, it.quantity)
        }
        return WorkEntryEditData(
            aircraftId = entry.aircraftId,
            description = entry.description,
            activityTypes = activityTypes,
            role = entry.role,
            supervisedAnother = entry.supervisedAnother,
            sessionDate = session?.date ?: LocalDate.now(),
            helperNames = helperNames,
            completedTaskIds = completedTaskIds,
            airframeHoursAtWork = entry.airframeHoursAtWork,
            launchesAtWork = entry.launchesAtWork,
            workorderIssuerName = entry.workorderIssuerName,
            workorderDate = entry.workorderDate,
            workorderRequestedWork = entry.workorderRequestedWork,
            workorderReference = entry.workorderReference,
            annualInspection = entry.annualInspection,
            concurrentWithArc = entry.concurrentWithArc,
            documentationRefs = documentationRefs,
            partsUsed = partsUsed,
        )
    }

    override suspend fun create(
        aircraftId: String?,
        description: String,
        activityTypes: Set<ActivityType>,
        role: EntryRole,
        supervisedAnother: Boolean,
        sessionDate: LocalDate,
        helperNames: List<String>,
        completedTaskIds: Set<String>,
        airframeHoursAtWork: Double?,
        launchesAtWork: Int?,
        workorderIssuerName: String?,
        workorderDate: LocalDate?,
        workorderRequestedWork: String?,
        workorderReference: String?,
        annualInspection: Boolean,
        concurrentWithArc: Boolean,
        documentationRefs: List<DocumentationRefInput>,
        partsUsed: List<PartUsedInput>,
    ): String {
        val entryId = UUID.randomUUID().toString()
        val now = Instant.now()
        workEntryDao.insert(
            WorkEntryEntity(
                id = entryId,
                aircraftId = aircraftId,
                description = description,
                role = role,
                supervisedAnother = supervisedAnother,
                airframeHoursAtWork = airframeHoursAtWork,
                launchesAtWork = launchesAtWork,
                workorderIssuerName = workorderIssuerName,
                workorderDate = workorderDate,
                workorderRequestedWork = workorderRequestedWork,
                workorderReference = workorderReference,
                workorderReferenceNormalised = workorderReference?.let { Identifiers.normalise(it) },
                annualInspection = annualInspection,
                concurrentWithArc = concurrentWithArc,
                createdAt = now,
                updatedAt = now,
            ),
        )
        workSessionDao.insert(
            WorkSessionEntity(id = UUID.randomUUID().toString(), entryId = entryId, date = sessionDate),
        )
        insertChildren(entryId, activityTypes, helperNames, workorderIssuerName, completedTaskIds, documentationRefs, partsUsed)
        return entryId
    }

    override suspend fun update(
        id: String,
        aircraftId: String?,
        description: String,
        activityTypes: Set<ActivityType>,
        role: EntryRole,
        supervisedAnother: Boolean,
        sessionDate: LocalDate,
        helperNames: List<String>,
        completedTaskIds: Set<String>,
        airframeHoursAtWork: Double?,
        launchesAtWork: Int?,
        workorderIssuerName: String?,
        workorderDate: LocalDate?,
        workorderRequestedWork: String?,
        workorderReference: String?,
        annualInspection: Boolean,
        concurrentWithArc: Boolean,
        documentationRefs: List<DocumentationRefInput>,
        partsUsed: List<PartUsedInput>,
    ) {
        val existing = workEntryDao.byId(id) ?: return
        workEntryDao.update(
            existing.copy(
                aircraftId = aircraftId,
                description = description,
                role = role,
                supervisedAnother = supervisedAnother,
                airframeHoursAtWork = airframeHoursAtWork,
                launchesAtWork = launchesAtWork,
                workorderIssuerName = workorderIssuerName,
                workorderDate = workorderDate,
                workorderRequestedWork = workorderRequestedWork,
                workorderReference = workorderReference,
                workorderReferenceNormalised = workorderReference?.let { Identifiers.normalise(it) },
                annualInspection = annualInspection,
                concurrentWithArc = concurrentWithArc,
                updatedAt = Instant.now(),
            ),
        )

        workSessionDao.deleteForEntry(id)
        workSessionDao.insert(WorkSessionEntity(id = UUID.randomUUID().toString(), entryId = id, date = sessionDate))

        workEntryDao.deleteActivityTypesForEntry(id)
        entryHelperDao.deleteForEntry(id)
        taskCompletionDao.deleteForEntry(id)
        documentationRefDao.deleteForEntry(id)
        partUsedDao.deleteForEntry(id)
        insertChildren(id, activityTypes, helperNames, workorderIssuerName, completedTaskIds, documentationRefs, partsUsed)
    }

    override suspend fun delete(id: String) = workEntryDao.delete(id)

    private suspend fun insertChildren(
        entryId: String,
        activityTypes: Set<ActivityType>,
        helperNames: List<String>,
        workorderIssuerName: String?,
        completedTaskIds: Set<String>,
        documentationRefs: List<DocumentationRefInput>,
        partsUsed: List<PartUsedInput>,
    ) {
        workEntryDao.insertActivityTypes(activityTypes.map { WorkEntryActivityTypeEntity(entryId, it) })
        helperNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { name ->
            val personId = personRepository.findOrCreate(name)
            entryHelperDao.insert(EntryHelperEntity(entryId = entryId, personId = personId, role = HelperRole.ASSISTED))
        }
        workorderIssuerName?.trim()?.takeIf { it.isNotEmpty() }?.let { personRepository.findOrCreate(it) }
        catalogueDao.byIds(completedTaskIds.toList()).forEach { task ->
            taskCompletionDao.insert(
                TaskCompletionEntity(
                    id = UUID.randomUUID().toString(),
                    entryId = entryId,
                    taskId = task.id,
                    catalogueVersion = task.catalogueVersion,
                    taskTextSnapshot = task.text,
                ),
            )
        }
        documentationRefs.forEach { ref ->
            documentationRefDao.insert(
                DocumentationRefEntity(
                    id = UUID.randomUUID().toString(),
                    entryId = entryId,
                    reference = ref.reference,
                    referenceNormalised = Identifiers.normalise(ref.reference),
                    revision = ref.revision,
                    revisionDate = null,
                ),
            )
        }
        partsUsed.forEach { part ->
            partUsedDao.insert(
                PartUsedEntity(
                    id = UUID.randomUUID().toString(),
                    entryId = entryId,
                    description = part.description,
                    partNumber = part.partNumber,
                    partNumberNormalised = Identifiers.normalise(part.partNumber),
                    batchOrSerial = part.batchOrSerial,
                    formOneRef = part.formOneRef,
                    quantity = part.quantity,
                ),
            )
        }
    }
}
