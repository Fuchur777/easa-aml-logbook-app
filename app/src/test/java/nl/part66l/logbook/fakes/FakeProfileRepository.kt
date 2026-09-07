package nl.part66l.logbook.fakes

import nl.part66l.logbook.data.ProfileEntity
import nl.part66l.logbook.data.ProfileRepository

/** In-memory stand-in for ViewModel tests — no Room, no Robolectric. */
class FakeProfileRepository(initial: ProfileEntity? = null) : ProfileRepository {
    private var stored: ProfileEntity? = initial

    override suspend fun get(): ProfileEntity? = stored

    override suspend fun upsert(profile: ProfileEntity) {
        stored = profile
    }
}
