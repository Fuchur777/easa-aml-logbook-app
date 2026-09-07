package nl.part66l.logbook.data

import androidx.paging.PagingSource
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.EntryRole
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

    /** Creates an entry and its first work session together — a session-less entry can never feed Route A. */
    suspend fun create(
        aircraftId: String?,
        description: String,
        activityType: ActivityType,
        role: EntryRole,
        sessionDate: LocalDate,
    ): String
}

@Singleton
class WorkEntryRepositoryImpl @Inject constructor(
    private val workEntryDao: WorkEntryDao,
    private val workSessionDao: WorkSessionDao,
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

    override suspend fun create(
        aircraftId: String?,
        description: String,
        activityType: ActivityType,
        role: EntryRole,
        sessionDate: LocalDate,
    ): String {
        val entryId = UUID.randomUUID().toString()
        val now = Instant.now()
        workEntryDao.insert(
            WorkEntryEntity(
                id = entryId,
                aircraftId = aircraftId,
                description = description,
                activityType = activityType,
                role = role,
                createdAt = now,
                updatedAt = now,
            ),
        )
        workSessionDao.insert(
            WorkSessionEntity(id = UUID.randomUUID().toString(), entryId = entryId, date = sessionDate),
        )
        return entryId
    }
}
