package nl.part66l.logbook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.ProfileRepository

sealed interface AppStartState {
    data object Loading : AppStartState
    data object NeedsProfile : AppStartState
    data object Ready : AppStartState
}

/** Decides, once at process start, whether the first-run profile gate is needed (§4). */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<AppStartState>(AppStartState.Loading)
    val state: StateFlow<AppStartState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = if (profileRepository.get() == null) AppStartState.NeedsProfile else AppStartState.Ready
        }
    }

    /** Called once profile setup completes, so the gate doesn't need to re-query the database. */
    fun markProfileReady() {
        _state.value = AppStartState.Ready
    }
}
