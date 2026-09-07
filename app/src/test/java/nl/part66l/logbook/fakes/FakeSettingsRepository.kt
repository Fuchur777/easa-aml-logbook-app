package nl.part66l.logbook.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import nl.part66l.logbook.data.SettingsRepository

class FakeSettingsRepository(initialShowArchivedAircraft: Boolean = false) : SettingsRepository {
    private val showArchived = MutableStateFlow(initialShowArchivedAircraft)

    override val showArchivedAircraft: Flow<Boolean> = showArchived

    override suspend fun setShowArchivedAircraft(value: Boolean) {
        showArchived.value = value
    }
}
