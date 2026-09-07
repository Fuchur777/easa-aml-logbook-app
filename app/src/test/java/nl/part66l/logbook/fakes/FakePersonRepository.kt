package nl.part66l.logbook.fakes

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import nl.part66l.logbook.data.PersonEntity
import nl.part66l.logbook.data.PersonRepository

class FakePersonRepository(initial: List<PersonEntity> = emptyList()) : PersonRepository {
    private val people = MutableStateFlow(initial)

    override fun all(): Flow<List<PersonEntity>> = people

    override suspend fun findOrCreate(name: String): String {
        people.value.find { it.name == name }?.let { return it.id }
        val id = UUID.randomUUID().toString()
        people.value = people.value + PersonEntity(id = id, name = name)
        return id
    }
}
