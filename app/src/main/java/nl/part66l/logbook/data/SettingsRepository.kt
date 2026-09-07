package nl.part66l.logbook.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SettingsRepository {
    val showArchivedAircraft: Flow<Boolean>
    suspend fun setShowArchivedAircraft(value: Boolean)
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val SHOW_ARCHIVED_AIRCRAFT = booleanPreferencesKey("show_archived_aircraft")
    }

    override val showArchivedAircraft: Flow<Boolean> =
        dataStore.data.map { it[Keys.SHOW_ARCHIVED_AIRCRAFT] ?: false }

    override suspend fun setShowArchivedAircraft(value: Boolean) {
        dataStore.edit { it[Keys.SHOW_ARCHIVED_AIRCRAFT] = value }
    }
}
