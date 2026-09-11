package nl.part66l.logbook.fakes

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import nl.part66l.logbook.data.SettingsRepository

class FakeSettingsRepository(
    initialShowArchivedAircraft: Boolean = false,
    initialCatalogueSectionOrder: List<String> = emptyList(),
    initialCollapsedCatalogueSections: Set<String> = emptySet(),
    initialCrsNumberTemplate: String = "{REG}-{YYYY}-{SEQ:4}",
    initialCrsAnnualReset: Boolean = true,
    initialCrsStartAt: Int = 1,
    initialCrsShowCertifyingStaffContact: Boolean = false,
    initialConnectedGoogleAccountEmail: String? = null,
    initialDriveRootFolderId: String? = null,
    initialDriveBenchFolderId: String? = null,
    initialDriveDocumentsFolderId: String? = null,
    initialDriveBackupsFolderId: String? = null,
    initialLastDriveSyncAt: Instant? = null,
    initialDriveAutoSyncEnabled: Boolean = false,
) : SettingsRepository {
    private val showArchived = MutableStateFlow(initialShowArchivedAircraft)
    private val sectionOrder = MutableStateFlow(initialCatalogueSectionOrder)
    private val collapsedSections = MutableStateFlow(initialCollapsedCatalogueSections)
    private val crsNumberTemplateFlow = MutableStateFlow(initialCrsNumberTemplate)
    private val crsAnnualResetFlow = MutableStateFlow(initialCrsAnnualReset)
    private val crsStartAtFlow = MutableStateFlow(initialCrsStartAt)
    private val crsShowCertifyingStaffContactFlow = MutableStateFlow(initialCrsShowCertifyingStaffContact)
    private val connectedGoogleAccountEmailFlow = MutableStateFlow(initialConnectedGoogleAccountEmail)
    private val driveRootFolderIdFlow = MutableStateFlow(initialDriveRootFolderId)
    private val driveBenchFolderIdFlow = MutableStateFlow(initialDriveBenchFolderId)
    private val driveDocumentsFolderIdFlow = MutableStateFlow(initialDriveDocumentsFolderId)
    private val driveBackupsFolderIdFlow = MutableStateFlow(initialDriveBackupsFolderId)
    private val lastDriveSyncAtFlow = MutableStateFlow(initialLastDriveSyncAt)
    private val driveAutoSyncEnabledFlow = MutableStateFlow(initialDriveAutoSyncEnabled)

    override val showArchivedAircraft: Flow<Boolean> = showArchived

    override suspend fun setShowArchivedAircraft(value: Boolean) {
        showArchived.value = value
    }

    override val catalogueSectionOrder: Flow<List<String>> = sectionOrder

    override suspend fun setCatalogueSectionOrder(order: List<String>) {
        sectionOrder.value = order
    }

    override val collapsedCatalogueSections: Flow<Set<String>> = collapsedSections

    override suspend fun setCatalogueSectionCollapsed(section: String, collapsed: Boolean) {
        collapsedSections.value = if (collapsed) collapsedSections.value + section else collapsedSections.value - section
    }

    override val crsNumberTemplate: Flow<String> = crsNumberTemplateFlow

    override suspend fun setCrsNumberTemplate(value: String) {
        crsNumberTemplateFlow.value = value
    }

    override val crsAnnualReset: Flow<Boolean> = crsAnnualResetFlow

    override suspend fun setCrsAnnualReset(value: Boolean) {
        crsAnnualResetFlow.value = value
    }

    override val crsStartAt: Flow<Int> = crsStartAtFlow

    override suspend fun setCrsStartAt(value: Int) {
        crsStartAtFlow.value = value
    }

    override val crsShowCertifyingStaffContact: Flow<Boolean> = crsShowCertifyingStaffContactFlow

    override suspend fun setCrsShowCertifyingStaffContact(value: Boolean) {
        crsShowCertifyingStaffContactFlow.value = value
    }

    override val connectedGoogleAccountEmail: Flow<String?> = connectedGoogleAccountEmailFlow

    override suspend fun setConnectedGoogleAccountEmail(value: String?) {
        connectedGoogleAccountEmailFlow.value = value
    }

    override val driveRootFolderId: Flow<String?> = driveRootFolderIdFlow

    override suspend fun setDriveRootFolderId(value: String?) {
        driveRootFolderIdFlow.value = value
    }

    override val driveBenchFolderId: Flow<String?> = driveBenchFolderIdFlow

    override suspend fun setDriveBenchFolderId(value: String?) {
        driveBenchFolderIdFlow.value = value
    }

    override val driveDocumentsFolderId: Flow<String?> = driveDocumentsFolderIdFlow

    override suspend fun setDriveDocumentsFolderId(value: String?) {
        driveDocumentsFolderIdFlow.value = value
    }

    override val driveBackupsFolderId: Flow<String?> = driveBackupsFolderIdFlow

    override suspend fun setDriveBackupsFolderId(value: String?) {
        driveBackupsFolderIdFlow.value = value
    }

    override val lastDriveSyncAt: Flow<Instant?> = lastDriveSyncAtFlow

    override suspend fun setLastDriveSyncAt(value: Instant) {
        lastDriveSyncAtFlow.value = value
    }

    override val driveAutoSyncEnabled: Flow<Boolean> = driveAutoSyncEnabledFlow

    override suspend fun setDriveAutoSyncEnabled(value: Boolean) {
        driveAutoSyncEnabledFlow.value = value
    }
}
