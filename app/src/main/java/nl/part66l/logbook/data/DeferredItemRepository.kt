package nl.part66l.logbook.data

import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface DeferredItemRepository {
    /** Every deferred item still open — a note in the engineer's own record, never a statement about the aircraft (§5.7). */
    fun open(): Flow<List<DeferredItemEntity>>

    /** Raises a new deferred item against an already-issued CRS. */
    suspend fun raise(raisedByCrsId: String, description: String): String

    /** Closes an open item as resolved by [closedByEntryId]. A no-op (returns false) if the item is already closed. */
    suspend fun close(id: String, closedByEntryId: String, closedDate: LocalDate): Boolean
}

@Singleton
class DeferredItemRepositoryImpl @Inject constructor(
    private val deferredItemDao: DeferredItemDao,
) : DeferredItemRepository {

    override fun open(): Flow<List<DeferredItemEntity>> = deferredItemDao.open()

    override suspend fun raise(raisedByCrsId: String, description: String): String {
        val id = UUID.randomUUID().toString()
        deferredItemDao.insert(DeferredItemEntity(id = id, raisedByCrsId = raisedByCrsId, description = description))
        return id
    }

    override suspend fun close(id: String, closedByEntryId: String, closedDate: LocalDate): Boolean =
        deferredItemDao.close(id, closedByEntryId, closedDate) > 0
}
