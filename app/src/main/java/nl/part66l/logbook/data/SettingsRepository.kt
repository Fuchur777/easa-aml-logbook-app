package nl.part66l.logbook.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.Instant
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

    /** CRS numbering template (§9.2), e.g. `{REG}-{YYYY}-{SEQ:4}`. Default matches the worked example. */
    val crsNumberTemplate: Flow<String>
    suspend fun setCrsNumberTemplate(value: String)

    val crsAnnualReset: Flow<Boolean>
    suspend fun setCrsAnnualReset(value: Boolean)

    val crsStartAt: Flow<Int>
    suspend fun setCrsStartAt(value: Int)

    /** Off by default — the certifying staff's phone/email are personal data, printed on the CRS only when opted in. */
    val crsShowCertifyingStaffContact: Flow<Boolean>
    suspend fun setCrsShowCertifyingStaffContact(value: Boolean)

    /** Google Drive backup (§10). Null means "not connected" — the app is fully functional offline without any of these. */
    val connectedGoogleAccountEmail: Flow<String?>
    suspend fun setConnectedGoogleAccountEmail(value: String?)

    /** The root "AMLog" folder's Drive file ID, created once on first sync. */
    val driveRootFolderId: Flow<String?>
    suspend fun setDriveRootFolderId(value: String?)

    /** The single folder bench/component work (no aircraft) syncs into, created once on first sync. */
    val driveBenchFolderId: Flow<String?>
    suspend fun setDriveBenchFolderId(value: String?)

    /** The single "Documents" folder the document library syncs into, created lazily on first pending upload. */
    val driveDocumentsFolderId: Flow<String?>
    suspend fun setDriveDocumentsFolderId(value: String?)

    /** The single "Backups" folder full-device backups upload into, created lazily on first backup. */
    val driveBackupsFolderId: Flow<String?>
    suspend fun setDriveBackupsFolderId(value: String?)

    val lastDriveSyncAt: Flow<Instant?>
    suspend fun setLastDriveSyncAt(value: Instant)

    /** Off by default — periodic background sync via WorkManager, in addition to manual "Sync now". */
    val driveAutoSyncEnabled: Flow<Boolean>
    suspend fun setDriveAutoSyncEnabled(value: Boolean)
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val SHOW_ARCHIVED_AIRCRAFT = booleanPreferencesKey("show_archived_aircraft")
        val CATALOGUE_SECTION_ORDER = stringPreferencesKey("catalogue_section_order")
        val COLLAPSED_CATALOGUE_SECTIONS = stringSetPreferencesKey("collapsed_catalogue_sections")
        val CRS_NUMBER_TEMPLATE = stringPreferencesKey("crs_number_template")
        val CRS_ANNUAL_RESET = booleanPreferencesKey("crs_annual_reset")
        val CRS_START_AT = intPreferencesKey("crs_start_at")
        val CRS_SHOW_CERTIFYING_STAFF_CONTACT = booleanPreferencesKey("crs_show_certifying_staff_contact")
        val CONNECTED_GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("connected_google_account_email")
        val DRIVE_ROOT_FOLDER_ID = stringPreferencesKey("drive_root_folder_id")
        val DRIVE_BENCH_FOLDER_ID = stringPreferencesKey("drive_bench_folder_id")
        val DRIVE_DOCUMENTS_FOLDER_ID = stringPreferencesKey("drive_documents_folder_id")
        val DRIVE_BACKUPS_FOLDER_ID = stringPreferencesKey("drive_backups_folder_id")
        val LAST_DRIVE_SYNC_AT = longPreferencesKey("last_drive_sync_at")
        val DRIVE_AUTO_SYNC_ENABLED = booleanPreferencesKey("drive_auto_sync_enabled")
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

    override val crsNumberTemplate: Flow<String> =
        dataStore.data.map { it[Keys.CRS_NUMBER_TEMPLATE] ?: "{REG}-{YYYY}-{SEQ:4}" }

    override suspend fun setCrsNumberTemplate(value: String) {
        dataStore.edit { it[Keys.CRS_NUMBER_TEMPLATE] = value }
    }

    override val crsAnnualReset: Flow<Boolean> =
        dataStore.data.map { it[Keys.CRS_ANNUAL_RESET] ?: true }

    override suspend fun setCrsAnnualReset(value: Boolean) {
        dataStore.edit { it[Keys.CRS_ANNUAL_RESET] = value }
    }

    override val crsStartAt: Flow<Int> =
        dataStore.data.map { it[Keys.CRS_START_AT] ?: 1 }

    override suspend fun setCrsStartAt(value: Int) {
        dataStore.edit { it[Keys.CRS_START_AT] = value }
    }

    override val crsShowCertifyingStaffContact: Flow<Boolean> =
        dataStore.data.map { it[Keys.CRS_SHOW_CERTIFYING_STAFF_CONTACT] ?: false }

    override suspend fun setCrsShowCertifyingStaffContact(value: Boolean) {
        dataStore.edit { it[Keys.CRS_SHOW_CERTIFYING_STAFF_CONTACT] = value }
    }

    override val connectedGoogleAccountEmail: Flow<String?> =
        dataStore.data.map { it[Keys.CONNECTED_GOOGLE_ACCOUNT_EMAIL] }

    override suspend fun setConnectedGoogleAccountEmail(value: String?) {
        dataStore.edit { prefs ->
            if (value == null) prefs.remove(Keys.CONNECTED_GOOGLE_ACCOUNT_EMAIL) else prefs[Keys.CONNECTED_GOOGLE_ACCOUNT_EMAIL] = value
        }
    }

    override val driveRootFolderId: Flow<String?> =
        dataStore.data.map { it[Keys.DRIVE_ROOT_FOLDER_ID] }

    override suspend fun setDriveRootFolderId(value: String?) {
        dataStore.edit { prefs ->
            if (value == null) prefs.remove(Keys.DRIVE_ROOT_FOLDER_ID) else prefs[Keys.DRIVE_ROOT_FOLDER_ID] = value
        }
    }

    override val driveBenchFolderId: Flow<String?> =
        dataStore.data.map { it[Keys.DRIVE_BENCH_FOLDER_ID] }

    override suspend fun setDriveBenchFolderId(value: String?) {
        dataStore.edit { prefs ->
            if (value == null) prefs.remove(Keys.DRIVE_BENCH_FOLDER_ID) else prefs[Keys.DRIVE_BENCH_FOLDER_ID] = value
        }
    }

    override val driveDocumentsFolderId: Flow<String?> =
        dataStore.data.map { it[Keys.DRIVE_DOCUMENTS_FOLDER_ID] }

    override suspend fun setDriveDocumentsFolderId(value: String?) {
        dataStore.edit { prefs ->
            if (value == null) prefs.remove(Keys.DRIVE_DOCUMENTS_FOLDER_ID) else prefs[Keys.DRIVE_DOCUMENTS_FOLDER_ID] = value
        }
    }

    override val driveBackupsFolderId: Flow<String?> =
        dataStore.data.map { it[Keys.DRIVE_BACKUPS_FOLDER_ID] }

    override suspend fun setDriveBackupsFolderId(value: String?) {
        dataStore.edit { prefs ->
            if (value == null) prefs.remove(Keys.DRIVE_BACKUPS_FOLDER_ID) else prefs[Keys.DRIVE_BACKUPS_FOLDER_ID] = value
        }
    }

    override val lastDriveSyncAt: Flow<Instant?> =
        dataStore.data.map { prefs -> prefs[Keys.LAST_DRIVE_SYNC_AT]?.let { Instant.ofEpochMilli(it) } }

    override suspend fun setLastDriveSyncAt(value: Instant) {
        dataStore.edit { it[Keys.LAST_DRIVE_SYNC_AT] = value.toEpochMilli() }
    }

    override val driveAutoSyncEnabled: Flow<Boolean> =
        dataStore.data.map { it[Keys.DRIVE_AUTO_SYNC_ENABLED] ?: false }

    override suspend fun setDriveAutoSyncEnabled(value: Boolean) {
        dataStore.edit { it[Keys.DRIVE_AUTO_SYNC_ENABLED] = value }
    }
}
