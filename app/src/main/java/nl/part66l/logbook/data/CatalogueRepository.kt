package nl.part66l.logbook.data

import javax.inject.Inject
import javax.inject.Singleton

interface CatalogueRepository {
    /** Tasks applicable to the profile's held subcategories, from whichever catalogue version is seeded. Empty if no profile or no catalogue. */
    suspend fun applicableTasksForProfile(): List<CatalogueTaskEntity>
}

@Singleton
class CatalogueRepositoryImpl @Inject constructor(
    private val catalogueDao: CatalogueDao,
    private val profileDao: ProfileDao,
) : CatalogueRepository {

    override suspend fun applicableTasksForProfile(): List<CatalogueTaskEntity> {
        val profile = profileDao.get() ?: return emptyList()
        val version = catalogueDao.currentVersion() ?: return emptyList()
        return catalogueDao.applicableTasks(
            version = version,
            l1 = profile.holdsL1,
            l1c = profile.holdsL1C,
            l2 = profile.holdsL2,
            l2c = profile.holdsL2C,
        )
    }
}
