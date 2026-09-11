package nl.part66l.logbook.ui.crs

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.AircraftRepository
import nl.part66l.logbook.data.AircraftWithRegistration
import nl.part66l.logbook.data.CrsRepository
import nl.part66l.logbook.data.IssuedCrsRow

/**
 * [aircraftId] null means every aircraft (bench work included). [numberQuery] blank means no
 * name filter. [helperQuery] blank means no helper filter. [ascending] false sorts newest first.
 */
data class IssuedCrsFilter(
    val aircraftId: String? = null,
    val numberQuery: String = "",
    val helperQuery: String = "",
    val ascending: Boolean = false,
)

@HiltViewModel
class IssuedCrsListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crsRepository: CrsRepository,
    aircraftRepository: AircraftRepository,
) : ViewModel() {

    /** Includes archived aircraft — a past certificate can still reference one, and the filter needs to find it. */
    val aircraftOptions: StateFlow<List<AircraftWithRegistration>> = aircraftRepository.observeAllWithRegistration(includeArchived = true)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _filter = MutableStateFlow(IssuedCrsFilter())
    val filter: StateFlow<IssuedCrsFilter> = _filter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<IssuedCrsRow>> = _filter
        .flatMapLatest { filter ->
            crsRepository.latestIssued(
                aircraftId = filter.aircraftId,
                numberQuery = filter.numberQuery,
                helperQuery = filter.helperQuery,
                ascending = filter.ascending,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Empty means "not in selection mode" — the screen switches its top bar and row tap behaviour on this. */
    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    /** Changing which rows are even in view would leave stale ids selected, so a narrowing/widening filter change clears the selection; re-sorting the same set does not. */
    fun onAircraftFilterChange(aircraftId: String?) {
        _filter.update { it.copy(aircraftId = aircraftId) }
        _selectedIds.value = emptySet()
    }
    fun onNumberQueryChange(value: String) {
        _filter.update { it.copy(numberQuery = value) }
        _selectedIds.value = emptySet()
    }
    fun onHelperQueryChange(value: String) {
        _filter.update { it.copy(helperQuery = value) }
        _selectedIds.value = emptySet()
    }
    fun onSortToggle() = _filter.update { it.copy(ascending = !it.ascending) }

    /** Long-pressing a row enters selection mode with just that row selected. */
    fun onRowLongPress(id: String) = _selectedIds.update { it + id }

    /** Only meaningful once selection mode is already active — the screen routes a plain tap here itself, or to the normal open-PDF action, depending on [selectedIds]. */
    fun onRowToggleSelected(id: String) = _selectedIds.update { if (id in it) it - id else it + id }

    /** Every row the current filter shows, not just what's scrolled into view. */
    fun onSelectAll() = _selectedIds.update { rows.value.map { row -> row.crs.id }.toSet() }

    fun onClearSelection() {
        _selectedIds.value = emptySet()
    }

    /** No-op with nothing selected. [onReady] is only invoked once the zip is actually built. */
    fun exportSelected(onReady: (File) -> Unit) {
        val selected = rows.value.filter { it.crs.id in _selectedIds.value }
        if (selected.isEmpty() || _exporting.value) return
        viewModelScope.launch {
            _exporting.value = true
            val zip = exportCrsZip(context, selected)
            _exporting.value = false
            onReady(zip)
        }
    }
}
