package nl.part66l.logbook.ui.recency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.RecencyRepository
import nl.part66l.logbook.domain.RecencyEvaluator

@HiltViewModel
class RecencyDashboardViewModel @Inject constructor(
    private val recencyRepository: RecencyRepository,
) : ViewModel() {

    private val _results = MutableStateFlow<List<RecencyEvaluator.SubcategoryResult>>(emptyList())
    val results: StateFlow<List<RecencyEvaluator.SubcategoryResult>> = _results.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /**
     * Re-evaluated on demand rather than kept reactive — recency depends on
     * sessions/tasks/annuals with no shared invalidation signal yet. Called from
     * the screen's LaunchedEffect(Unit), so it re-runs every time the tab is
     * (re)entered even though this ViewModel instance itself survives tab switches.
     */
    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _results.value = recencyRepository.evaluateCurrent(LocalDate.now())
            _loading.value = false
        }
    }
}
