package nl.part66l.logbook.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SettingsRepository {
    val showArchivedAircraft: Flow<Boolean>
    suspend fun setShowArchivedAircraft(value: Boolean)

    /** User's chosen section order for the Appendix II task picker. Sections not listed fall back to alphabetical, after the ones that are. */
    val catalogueSectionOrder: Flow<List<String>>
    suspend fun setCatalogueSectionOrder(order: List<String>)

    /** Sections folded closed in the Appendix II task picker. */
    val collapsedCatalogueSections: Flow<Set<String>>
    suspend fun setCatalogueSectionCollapsed(section: String, collapsed: Boolean)
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val SHOW_ARCHIVED_AIRCRAFT = booleanPreferencesKey("show_archived_aircraft")
        val CATALOGUE_SECTION_ORDER = stringPreferencesKey("catalogue_section_order")
        val COLLAPSED_CATALOGUE_SECTIONS = stringSetPreferencesKey("collapsed_catalogue_sections")
    }

    /** ASCII unit separator (code point 31) - won't appear in a section name, so it safely joins/splits the stored order string. */
    private val sectionOrderDelimiter = 31.toChar().toString()

    override val showArchivedAircraft: Flow<Boolean> =
        dataStore.data.map { it[Keys.SHOW_ARCHIVED_AIRCRAFT] ?: false }

    override suspend fun setShowArchivedAircraft(value: Boolean) {
        dataStore.edit { it[Keys.SHOW_ARCHIVED_AIRCRAFT] = value }
    }

    override val catalogueSectionOrder: Flow<List<String>> =
        dataStore.data.map { prefs ->
            prefs[Keys.CATALOGUE_SECTION_ORDER]?.split(sectionOrderDelimiter)?.filter { it.isNotEmpty() } ?: emptyList()
        }

    override suspend fun setCatalogueSectionOrder(order: List<String>) {
        dataStore.edit { it[Keys.CATALOGUE_SECTION_ORDER] = order.joinToString(sectionOrderDelimiter) }
    }

    override val collapsedCatalogueSections: Flow<Set<String>> =
        dataStore.data.map { it[Keys.COLLAPSED_CATALOGUE_SECTIONS] ?: emptySet() }

    override suspend fun setCatalogueSectionCollapsed(section: String, collapsed: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.COLLAPSED_CATALOGUE_SECTIONS] ?: emptySet()
            prefs[Keys.COLLAPSED_CATALOGUE_SECTIONS] = if (collapsed) current + section else current - section
        }
    }
}
