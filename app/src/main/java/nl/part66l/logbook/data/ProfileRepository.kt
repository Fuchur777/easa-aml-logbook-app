package nl.part66l.logbook.data

import javax.inject.Inject
import javax.inject.Singleton

interface ProfileRepository {
    suspend fun get(): ProfileEntity?
    suspend fun upsert(profile: ProfileEntity)
}

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao,
) : ProfileRepository {
    override suspend fun get(): ProfileEntity? = profileDao.get()
    override suspend fun upsert(profile: ProfileEntity) = profileDao.upsert(profile)
}
