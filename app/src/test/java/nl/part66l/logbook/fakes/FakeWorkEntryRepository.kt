package nl.part66l.logbook.fakes

import androidx.paging.PagingSource
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import nl.part66l.logbook.data.DocumentationRefInput
import nl.part66l.logbook.data.PartUsedInput
import nl.part66l.logbook.data.PhotoInput
import nl.part66l.logbook.data.WorkEntryEditData
import nl.part66l.logbook.data.WorkEntryEntity
import nl.part66l.logbook.data.WorkEntryListRow
import nl.part66l.logbook.data.WorkEntryRepository
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.Provenance

data class CreatedWorkEntry(
    val entry: WorkEntryEntity,
    val activityTypes: Set<ActivityType>,
    val sessionDates: List<LocalDate>,
    val helperNames: List<String>,
    val completedTaskIds: Set<String>,
    val documentationRefs: List<DocumentationRefInput>,
    val partsUsed: List<PartUsedInput>,
    val photos: List<PhotoInput> = emptyList(),
)

/**
 * In-memory stand-in for ViewModel tests — no Room, no Robolectric.
 *
 * The three PagingSource-returning methods aren't faked: no ViewModel test
 * needs them (WorkEntryListViewModel is a thin Pager wrapper with nothing of
 * its own to verify — [pagedAllWithDetails]'s real join/order behavior is
 * covered against real Room in WorkEntryRepositoryTest instead).
 */
class FakeWorkEntryRepository : WorkEntryRepository {
    /** Every create() call, in order — kept even after a later update()/delete() touches the same id. */
    val created = mutableListOf<CreatedWorkEntry>()

    /** Every update() call, in order. */
    val updated = mutableListOf<CreatedWorkEntry>()

    val deletedIds = mutableListOf<String>()

    /** Current state per entry — what [forEdit] reads from. Seed directly for a test that starts already-editing. */
    val entries = mutableMapOf<String, CreatedWorkEntry>()

    override fun pagedAll(): PagingSource<Int, WorkEntryEntity> =
        throw UnsupportedOperationException("not faked — no ViewModel test needs this")

    override fun filtered(
        aircraftId: String?,
        annualOnly: Boolean,
        provenance: Provenance?,
        from: LocalDate?,
        to: LocalDate?,
    ): PagingSource<Int, WorkEntryEntity> =
        throw UnsupportedOperationException("not faked — no ViewModel test needs this")

    override fun pagedAllWithDetails(aircraftId: String?, ascending: Boolean): PagingSource<Int, WorkEntryListRow> =
        throw UnsupportedOperationException("not faked — no ViewModel test needs this")

    override suspend fun forEdit(id: String): WorkEntryEditData? {
        val e = entries[id] ?: return null
        return WorkEntryEditData(
            aircraftId = e.entry.aircraftId,
            description = e.entry.description,
            explanation = e.entry.explanation,
            activityTypes = e.activityTypes,
            supervisedAnother = e.entry.supervisedAnother,
            sessionDates = e.sessionDates,
            daysWorkedOverride = e.entry.daysWorkedOverride,
            helperNames = e.helperNames,
            completedTaskIds = e.completedTaskIds,
            airframeHoursAtWork = e.entry.airframeHoursAtWork,
            launchesAtWork = e.entry.launchesAtWork,
            workorderIssuerName = e.entry.workorderIssuerName,
            workorderDate = e.entry.workorderDate,
            workorderRequestedWork = e.entry.workorderRequestedWork,
            workorderReference = e.entry.workorderReference,
            annualInspection = e.entry.annualInspection,
            concurrentWithArc = e.entry.concurrentWithArc,
            documentationRefs = e.documentationRefs,
            partsUsed = e.partsUsed,
            photos = e.photos,
        )
    }

    override suspend fun create(
        aircraftId: String?,
        description: String,
        explanation: String?,
        activityTypes: Set<ActivityType>,
        supervisedAnother: Boolean,
        sessionDates: List<LocalDate>,
        daysWorkedOverride: Int?,
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
        photos: List<PhotoInput>,
    ): String {
        val id = UUID.randomUUID().toString()
        val entry = CreatedWorkEntry(
            entry = WorkEntryEntity(
                id = id, aircraftId = aircraftId, description = description, explanation = explanation,
                supervisedAnother = supervisedAnother,
                airframeHoursAtWork = airframeHoursAtWork, launchesAtWork = launchesAtWork,
                workorderIssuerName = workorderIssuerName, workorderDate = workorderDate,
                workorderRequestedWork = workorderRequestedWork, workorderReference = workorderReference,
                annualInspection = annualInspection, concurrentWithArc = concurrentWithArc,
                daysWorkedOverride = daysWorkedOverride,
                createdAt = Instant.now(), updatedAt = Instant.now(),
            ),
            activityTypes = activityTypes,
            sessionDates = sessionDates,
            helperNames = helperNames,
            completedTaskIds = completedTaskIds,
            documentationRefs = documentationRefs,
            partsUsed = partsUsed,
            photos = photos,
        )
        created += entry
        entries[id] = entry
        return id
    }

    override suspend fun update(
        id: String,
        aircraftId: String?,
        description: String,
        explanation: String?,
        activityTypes: Set<ActivityType>,
        supervisedAnother: Boolean,
        sessionDates: List<LocalDate>,
        daysWorkedOverride: Int?,
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
        photos: List<PhotoInput>,
    ) {
        val existing = entries[id] ?: return
        val updatedEntry = existing.copy(
            entry = existing.entry.copy(
                aircraftId = aircraftId, description = description, explanation = explanation, supervisedAnother = supervisedAnother,
                airframeHoursAtWork = airframeHoursAtWork, launchesAtWork = launchesAtWork,
                workorderIssuerName = workorderIssuerName, workorderDate = workorderDate,
                workorderRequestedWork = workorderRequestedWork, workorderReference = workorderReference,
                annualInspection = annualInspection, concurrentWithArc = concurrentWithArc,
                daysWorkedOverride = daysWorkedOverride,
            ),
            activityTypes = activityTypes,
            sessionDates = sessionDates,
            helperNames = helperNames,
            completedTaskIds = completedTaskIds,
            documentationRefs = documentationRefs,
            partsUsed = partsUsed,
            photos = photos,
        )
        updated += updatedEntry
        entries[id] = updatedEntry
    }

    override suspend fun delete(id: String) {
        deletedIds += id
        entries.remove(id)
    }
}
