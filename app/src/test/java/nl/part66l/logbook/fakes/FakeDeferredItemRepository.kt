package nl.part66l.logbook.fakes

import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import nl.part66l.logbook.data.DeferredItemEntity
import nl.part66l.logbook.data.DeferredItemRepository

class FakeDeferredItemRepository(initial: List<DeferredItemEntity> = emptyList()) : DeferredItemRepository {
    private val items = MutableStateFlow(initial)

    override fun open(): Flow<List<DeferredItemEntity>> = items.map { list -> list.filter { !it.closed } }

    override suspend fun raise(raisedByCrsId: String, description: String): String {
        val id = UUID.randomUUID().toString()
        items.value = items.value + DeferredItemEntity(id = id, raisedByCrsId = raisedByCrsId, description = description)
        return id
    }

    override suspend fun close(id: String, closedByEntryId: String, closedDate: LocalDate): Boolean {
        val target = items.value.find { it.id == id && !it.closed } ?: return false
        items.value = items.value.map {
            if (it.id == id) it.copy(closed = true, closedByEntryId = closedByEntryId, closedDate = closedDate) else it
        }
        return true
    }
}
