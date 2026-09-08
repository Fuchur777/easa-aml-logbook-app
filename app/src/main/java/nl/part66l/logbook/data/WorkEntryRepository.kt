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

    /**
     * Creates an entry and its first work session together — a session-less entry can
     * never feed Route A. [helperNames] are resolved to the person directory by exact
     * name match, creating a new [PersonEntity] for any name not already on file.
     * [completedTaskIds] are snapshotted into [TaskCompletionEntity] rows as they read
     * today — a later catalogue update must never retroactively change what a past
     * completion said.
     */
    suspend fun create(
        aircraftId: String?,
        description: String,
        activityTypes: Set<ActivityType>,
        role: EntryRole,
        supervisedAnother: Boolean,
        sessionDate: LocalDate,
        helperNames: List<String> = emptyList(),
        researchAndPaperwork: Boolean = false,
        completedTaskIds: Set<String> = emptySet(),
    ): String
}

@Singleton
class WorkEntryRepositoryImpl @Inject constructor(
    private val workEntryDao: WorkEntryDao,
    private val workSessionDao: WorkSessionDao,
    private val entryHelperDao: EntryHelperDao,
    private val personRepository: PersonRepository,
    private val catalogueDao: CatalogueDao,
    private val taskCompletionDao: TaskCompletionDao,
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

    override suspend fun create(
        aircraftId: String?,
        description: String,
        activityTypes: Set<ActivityType>,
        role: EntryRole,
        supervisedAnother: Boolean,
        sessionDate: LocalDate,
        helperNames: List<String>,
        researchAndPaperwork: Boolean,
        completedTaskIds: Set<String>,
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
                researchAndPaperwork = researchAndPaperwork,
                createdAt = now,
                updatedAt = now,
            ),
        )
        workEntryDao.insertActivityTypes(activityTypes.map { WorkEntryActivityTypeEntity(entryId, it) })
        workSessionDao.insert(
            WorkSessionEntity(id = UUID.randomUUID().toString(), entryId = entryId, date = sessionDate),
        )
        helperNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { name ->
            val personId = personRepository.findOrCreate(name)
            entryHelperDao.insert(EntryHelperEntity(entryId = entryId, personId = personId, role = HelperRole.ASSISTED))
        }
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
        return entryId
    }
}
