package nl.part66l.logbook.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.ProfileEntity
import nl.part66l.logbook.data.ProfileRepository
import nl.part66l.logbook.data.SettingsRepository
import nl.part66l.logbook.domain.CrsNumberFormat
import nl.part66l.logbook.domain.WarningThresholds

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    /**
     * Kept as text rather than Int so a half-typed or cleared field doesn't snap back to a number
     * mid-edit; a value that doesn't parse simply isn't persisted.
     */
    val warningThresholds: StateFlow<WarningThresholds> = settingsRepository.warningThresholds
        .stateIn(viewModelScope, SharingStarted.Eagerly, WarningThresholds())

    fun onWarningThresholdChange(update: (WarningThresholds) -> WarningThresholds) {
        viewModelScope.launch { settingsRepository.setWarningThresholds(update(warningThresholds.value)) }
    }

    val crsShowCertifyingStaffContact: StateFlow<Boolean> = settingsRepository.crsShowCertifyingStaffContact
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onCrsShowCertifyingStaffContactChange(value: Boolean) {
        viewModelScope.launch { settingsRepository.setCrsShowCertifyingStaffContact(value) }
    }

    /**
     * Recency reduction and research-counts-toward-recency are Profile fields (regulatory
     * declarations tied to the licence), but edited here rather than on the Profile screen —
     * they read as settings, not identity. [profile] is a synchronous cache updated on every
     * change so each write starts from the latest value regardless of DB write timing.
     */
    private var profile: ProfileEntity? = null

    private val _recencyReductionGranted = MutableStateFlow(false)
    val recencyReductionGranted: StateFlow<Boolean> = _recencyReductionGranted.asStateFlow()

    private val _recencyReductionReference = MutableStateFlow("")
    val recencyReductionReference: StateFlow<String> = _recencyReductionReference.asStateFlow()

    private val _researchCountsTowardRecency = MutableStateFlow(false)
    val researchCountsTowardRecency: StateFlow<Boolean> = _researchCountsTowardRecency.asStateFlow()

    /**
     * CRS numbering (§9.2) — template, annual reset and start-at are validated together,
     * since [CrsNumberFormat]'s own constructor checks the combination (a `{SEQ:N}`
     * placeholder must exist, be unique, and be last). Local buffers, not bound to the
     * persisted flow, so a keystroke never snaps back mid-edit — only a value that actually
     * constructs a valid [CrsNumberFormat] is written to [settingsRepository].
     */
    private val _crsNumberTemplate = MutableStateFlow("")
    val crsNumberTemplate: StateFlow<String> = _crsNumberTemplate.asStateFlow()

    private val _crsAnnualReset = MutableStateFlow(true)
    val crsAnnualReset: StateFlow<Boolean> = _crsAnnualReset.asStateFlow()

    private val _crsStartAt = MutableStateFlow("1")
    val crsStartAt: StateFlow<String> = _crsStartAt.asStateFlow()

    private val _crsNumberingError = MutableStateFlow<String?>(null)
    val crsNumberingError: StateFlow<String?> = _crsNumberingError.asStateFlow()

    init {
        viewModelScope.launch {
            profile = profileRepository.get()
            profile?.let {
                _recencyReductionGranted.value = it.recencyReductionGranted
                _recencyReductionReference.value = it.recencyReductionReference.orEmpty()
                _researchCountsTowardRecency.value = it.researchCountsTowardRecency
            }
        }
        viewModelScope.launch {
            _crsNumberTemplate.value = settingsRepository.crsNumberTemplate.first()
            _crsAnnualReset.value = settingsRepository.crsAnnualReset.first()
            _crsStartAt.value = settingsRepository.crsStartAt.first().toString()
        }
    }

    /** Mirrors the licence's issuing authority, same as the Profile screen used to do — asking twice is duplicate data entry. */
    fun onRecencyReductionGrantedChange(value: Boolean) {
        _recencyReductionGranted.value = value
        persist { it.copy(recencyReductionGranted = value, recencyReductionAuthority = it.issuingAuthority) }
    }

    fun onRecencyReductionReferenceChange(value: String) {
        _recencyReductionReference.value = value
        persist { it.copy(recencyReductionReference = value.trim().ifBlank { null }) }
    }

    fun onResearchCountsTowardRecencyChange(value: Boolean) {
        _researchCountsTowardRecency.value = value
        persist { it.copy(researchCountsTowardRecency = value) }
    }

    private fun persist(update: (ProfileEntity) -> ProfileEntity) {
        val current = profile ?: return
        val updated = update(current)
        profile = updated
        viewModelScope.launch { profileRepository.upsert(updated) }
    }

    fun onCrsNumberTemplateChange(value: String) {
        _crsNumberTemplate.value = value
        validateAndPersistCrsNumbering()
    }

    fun onCrsAnnualResetChange(value: Boolean) {
        _crsAnnualReset.value = value
        validateAndPersistCrsNumbering()
    }

    fun onCrsStartAtChange(value: String) {
        _crsStartAt.value = value
        validateAndPersistCrsNumbering()
    }

    private fun validateAndPersistCrsNumbering() {
        val startAt = _crsStartAt.value.toIntOrNull()
        if (startAt == null) {
            _crsNumberingError.value = "Start-at must be a whole number"
            return
        }
        try {
            CrsNumberFormat(template = _crsNumberTemplate.value, annualReset = _crsAnnualReset.value, startAt = startAt)
        } catch (e: IllegalArgumentException) {
            _crsNumberingError.value = e.message
            return
        }
        _crsNumberingError.value = null
        viewModelScope.launch {
            settingsRepository.setCrsNumberTemplate(_crsNumberTemplate.value)
            settingsRepository.setCrsAnnualReset(_crsAnnualReset.value)
            settingsRepository.setCrsStartAt(startAt)
        }
    }
}
