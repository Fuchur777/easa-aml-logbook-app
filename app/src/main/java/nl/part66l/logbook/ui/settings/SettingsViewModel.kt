package nl.part66l.logbook.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.ProfileEntity
import nl.part66l.logbook.data.ProfileRepository
import nl.part66l.logbook.data.SettingsRepository

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    val showArchivedAircraft: StateFlow<Boolean> = settingsRepository.showArchivedAircraft
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun onShowArchivedAircraftChange(value: Boolean) {
        viewModelScope.launch { settingsRepository.setShowArchivedAircraft(value) }
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

    init {
        viewModelScope.launch {
            profile = profileRepository.get()
            profile?.let {
                _recencyReductionGranted.value = it.recencyReductionGranted
                _recencyReductionReference.value = it.recencyReductionReference.orEmpty()
                _researchCountsTowardRecency.value = it.researchCountsTowardRecency
            }
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
}
