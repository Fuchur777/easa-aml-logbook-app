package nl.part66l.logbook.fakes

import androidx.paging.PagingSource
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import nl.part66l.logbook.data.DocumentationRefInput
import nl.part66l.logbook.data.PartUsedInput
import nl.part66l.logbook.data.WorkEntryEntity
import nl.part66l.logbook.data.WorkEntryListRow
import nl.part66l.logbook.data.WorkEntryRepository
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.EntryRole
import nl.part66l.logbook.domain.Provenance

data class CreatedWorkEntry(
    val entry: WorkEntryEntity,
    val activityTypes: Set<ActivityType>,
    val helperNames: List<String>,
    val completedTaskIds: Set<String>,
    val documentationRefs: List<DocumentationRefInput>,
    val partsUsed: List<PartUsedInput>,
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
    val created = mutableListOf<CreatedWorkEntry>()

    override fun pagedAll(): PagingSource<Int, WorkEntryEntity> =
        throw UnsupportedOperationException("not faked — no ViewModel test needs this")

    override fun filtered(
        aircraftId: String?,
        role: EntryRole?,
        annualOnly: Boolean,
        provenance: Provenance?,
        from: LocalDate?,
        to: LocalDate?,
    ): PagingSource<Int, WorkEntryEntity> =
        throw UnsupportedOperationException("not faked — no ViewModel test needs this")

    override fun pagedAllWithDetails(): PagingSource<Int, WorkEntryListRow> =
        throw UnsupportedOperationException("not faked — no ViewModel test needs this")

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
        val id = UUID.randomUUID().toString()
        created += CreatedWorkEntry(
            entry = WorkEntryEntity(
                id = id, aircraftId = aircraftId, description = description, role = role,
                supervisedAnother = supervisedAnother,
                airframeHoursAtWork = airframeHoursAtWork, launchesAtWork = launchesAtWork,
                workorderIssuerName = workorderIssuerName, workorderDate = workorderDate,
                workorderRequestedWork = workorderRequestedWork, workorderReference = workorderReference,
                annualInspection = annualInspection, concurrentWithArc = concurrentWithArc,
                createdAt = Instant.now(), updatedAt = Instant.now(),
            ),
            activityTypes = activityTypes,
            helperNames = helperNames,
            completedTaskIds = completedTaskIds,
            documentationRefs = documentationRefs,
            partsUsed = partsUsed,
        )
        return id
    }
}
