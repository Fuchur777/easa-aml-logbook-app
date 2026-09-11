package nl.part66l.logbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.ProfileRepository
import nl.part66l.logbook.data.RecencyRepository
import nl.part66l.logbook.data.SettingsRepository
import nl.part66l.logbook.domain.ExpiryWarning
import nl.part66l.logbook.domain.WarningLevel

sealed interface AppStartState {
    data object Loading : AppStartState
    data object NeedsProfile : AppStartState
    data object Ready : AppStartState
}

/** Decides, once at process start, whether the first-run profile gate is needed (§4). */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val recencyRepository: RecencyRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<AppStartState>(AppStartState.Loading)
    val state: StateFlow<AppStartState> = _state.asStateFlow()

    /** Worst of licence expiry and any subcategory's recency lapse — what the Recency tab icon shows. */
    private val _warningLevel = MutableStateFlow(WarningLevel.NONE)
    val warningLevel: StateFlow<WarningLevel> = _warningLevel.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = if (profileRepository.get() == null) AppStartState.NeedsProfile else AppStartState.Ready
        }
        refreshWarningLevel()
    }

    /**
     * Recomputed on demand rather than kept reactive, for the same reason the recency dashboard
     * refreshes on entry: recency depends on sessions, tasks and annuals with no shared
     * invalidation signal yet. Called at startup and whenever the recency tab is (re)entered.
     */
    fun refreshWarningLevel() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val thresholds = settingsRepository.warningThresholds.first()
            val licence = ExpiryWarning.forLicence(profileRepository.get()?.licenceExpiry, today, thresholds)
            val recency = recencyRepository.evaluateCurrent(today)
                .map { ExpiryWarning.forRecency(it.current, it.lapseDate, today, thresholds) }
            _warningLevel.value = ExpiryWarning.highest(recency + licence)
        }
    }

    /** Called once profile setup completes, so the gate doesn't need to re-query the database. */
    fun markProfileReady() {
        _state.value = AppStartState.Ready
        refreshWarningLevel()
    }
}
