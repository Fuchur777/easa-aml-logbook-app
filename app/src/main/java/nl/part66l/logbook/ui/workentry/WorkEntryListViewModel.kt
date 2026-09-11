package nl.part66l.logbook.ui.workentry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import nl.part66l.logbook.data.AircraftRepository
import nl.part66l.logbook.data.AircraftWithRegistration
import nl.part66l.logbook.data.WorkEntryListRow
import nl.part66l.logbook.data.WorkEntryRepository

/** [aircraftId] null means every aircraft (bench work included). [ascending] false sorts newest first. */
data class WorkEntryListFilter(val aircraftId: String? = null, val ascending: Boolean = false)

@HiltViewModel
class WorkEntryListViewModel @Inject constructor(
    private val workEntryRepository: WorkEntryRepository,
    aircraftRepository: AircraftRepository,
) : ViewModel() {

    /** Includes archived aircraft — past entries can still reference one, and the filter needs to find them. */
    val aircraftOptions: StateFlow<List<AircraftWithRegistration>> = aircraftRepository.observeAllWithRegistration(includeArchived = true)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _filter = MutableStateFlow(WorkEntryListFilter())
    val filter: StateFlow<WorkEntryListFilter> = _filter.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val entries: Flow<PagingData<WorkEntryListRow>> = _filter
        .flatMapLatest { filter ->
            Pager(
                config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                pagingSourceFactory = { workEntryRepository.pagedAllWithDetails(filter.aircraftId, filter.ascending) },
            ).flow
        }
        .cachedIn(viewModelScope)

    fun onAircraftFilterChange(aircraftId: String?) = _filter.update { it.copy(aircraftId = aircraftId) }
    fun onSortToggle() = _filter.update { it.copy(ascending = !it.ascending) }
}
