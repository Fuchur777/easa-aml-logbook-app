package nl.part66l.logbook.data

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface PersonRepository {
    fun all(): Flow<List<PersonEntity>>

    /** Looks up an existing person by exact name, or creates one — the helper picker never asks for an ID, only a name. */
    suspend fun findOrCreate(name: String): String
}

@Singleton
class PersonRepositoryImpl @Inject constructor(
    private val personDao: PersonDao,
) : PersonRepository {

    override fun all(): Flow<List<PersonEntity>> = personDao.all()

    override suspend fun findOrCreate(name: String): String {
        personDao.byName(name)?.let { return it.id }
        val id = UUID.randomUUID().toString()
        personDao.insert(PersonEntity(id = id, name = name))
        return id
    }
}
