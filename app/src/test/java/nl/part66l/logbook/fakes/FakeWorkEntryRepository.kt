package nl.part66l.logbook.fakes

import androidx.paging.PagingSource
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import nl.part66l.logbook.data.WorkEntryEntity
import nl.part66l.logbook.data.WorkEntryRepository
import nl.part66l.logbook.domain.ActivityType
import nl.part66l.logbook.domain.EntryRole
import nl.part66l.logbook.domain.Provenance

/**
 * In-memory stand-in for ViewModel tests — no Room, no Robolectric.
 *
 * `pagedAll`/`filtered` aren't faked: no ViewModel needs them yet, and a
 * meaningful fake needs androidx.paging:paging-testing, which belongs with
 * the list-screen milestone that actually exercises them.
 */
class FakeWorkEntryRepository : WorkEntryRepository {
    val created = mutableListOf<WorkEntryEntity>()

    override fun pagedAll(): PagingSource<Int, WorkEntryEntity> =
        throw UnsupportedOperationException("not faked yet — no ViewModel needs this until the list screen milestone")

    override fun filtered(
        aircraftId: String?,
        role: EntryRole?,
        annualOnly: Boolean,
        provenance: Provenance?,
        from: LocalDate?,
        to: LocalDate?,
    ): PagingSource<Int, WorkEntryEntity> =
        throw UnsupportedOperationException("not faked yet — no ViewModel needs this until the list screen milestone")

    override suspend fun create(
        aircraftId: String?,
        description: String,
        activityType: ActivityType,
        role: EntryRole,
        sessionDate: LocalDate,
    ): String {
        val id = UUID.randomUUID().toString()
        created += WorkEntryEntity(
            id = id, aircraftId = aircraftId, description = description, activityType = activityType,
            role = role, createdAt = Instant.now(), updatedAt = Instant.now(),
        )
        return id
    }
}
