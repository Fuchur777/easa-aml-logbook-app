package nl.part66l.logbook.data

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface PersonRepository {
    /** Ordered by name — what both the Contacts management screen and the entry-form pickers render. */
    fun observeAll(includeArchived: Boolean): Flow<List<PersonEntity>>

    suspend fun byId(id: String): PersonEntity?

    /**
     * Looks up an existing person by exact name, or creates one — the helper and workorder-issuer
     * pickers never ask for an ID, only a name. When [licenceNumber] is given and differs from what's
     * on file, it's persisted too, so entering it once while adding a helper is enough to remember it.
     */
    suspend fun findOrCreate(name: String, licenceNumber: String? = null): String

    suspend fun create(name: String, licenceNumber: String?, email: String?): String

    suspend fun update(id: String, name: String, licenceNumber: String?, email: String?)

    suspend fun setArchived(id: String, archived: Boolean)

    suspend fun delete(id: String)
}

@Singleton
class PersonRepositoryImpl @Inject constructor(
    private val personDao: PersonDao,
) : PersonRepository {

    override fun observeAll(includeArchived: Boolean): Flow<List<PersonEntity>> = personDao.observeAll(includeArchived)

    override suspend fun byId(id: String): PersonEntity? = personDao.byId(id)

    override suspend fun findOrCreate(name: String, licenceNumber: String?): String {
        val existing = personDao.byName(name)
        if (existing != null) {
            if (licenceNumber != null && licenceNumber != existing.licenceNumber) {
                personDao.update(existing.copy(licenceNumber = licenceNumber))
            }
            return existing.id
        }
        val id = UUID.randomUUID().toString()
        personDao.insert(PersonEntity(id = id, name = name, licenceNumber = licenceNumber))
        return id
    }

    override suspend fun create(name: String, licenceNumber: String?, email: String?): String {
        val id = UUID.randomUUID().toString()
        personDao.insert(PersonEntity(id = id, name = name, licenceNumber = licenceNumber, email = email))
        return id
    }

    override suspend fun update(id: String, name: String, licenceNumber: String?, email: String?) {
        val existing = personDao.byId(id) ?: return
        personDao.update(existing.copy(name = name, licenceNumber = licenceNumber, email = email))
    }

    override suspend fun setArchived(id: String, archived: Boolean) = personDao.setArchived(id, archived)

    override suspend fun delete(id: String) = personDao.delete(id)
}
