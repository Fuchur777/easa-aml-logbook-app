package nl.part66l.logbook.fakes

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import nl.part66l.logbook.data.PersonEntity
import nl.part66l.logbook.data.PersonRepository

class FakePersonRepository(initial: List<PersonEntity> = emptyList()) : PersonRepository {
    private val people = MutableStateFlow(initial)

    override fun observeAll(includeArchived: Boolean): Flow<List<PersonEntity>> =
        people.map { list -> list.filter { includeArchived || !it.archived } }

    override suspend fun byId(id: String): PersonEntity? = people.value.find { it.id == id }

    override suspend fun findOrCreate(name: String, licenceNumber: String?): String {
        val existing = people.value.find { it.name == name }
        if (existing != null) {
            if (licenceNumber != null && licenceNumber != existing.licenceNumber) {
                people.value = people.value.map { if (it.id == existing.id) it.copy(licenceNumber = licenceNumber) else it }
            }
            return existing.id
        }
        val id = UUID.randomUUID().toString()
        people.value = people.value + PersonEntity(id = id, name = name, licenceNumber = licenceNumber)
        return id
    }

    override suspend fun create(name: String, licenceNumber: String?, email: String?): String {
        val id = UUID.randomUUID().toString()
        people.value = people.value + PersonEntity(id = id, name = name, licenceNumber = licenceNumber, email = email)
        return id
    }

    override suspend fun update(id: String, name: String, licenceNumber: String?, email: String?) {
        people.value = people.value.map { if (it.id == id) it.copy(name = name, licenceNumber = licenceNumber, email = email) else it }
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        people.value = people.value.map { if (it.id == id) it.copy(archived = archived) else it }
    }

    override suspend fun delete(id: String) {
        people.value = people.value.filterNot { it.id == id }
    }
}
