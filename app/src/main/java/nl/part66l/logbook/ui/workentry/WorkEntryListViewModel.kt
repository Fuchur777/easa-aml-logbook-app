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
import nl.part66l.logbook.data.WorkEntryListRow
import nl.part66l.logbook.data.WorkEntryRepository

@HiltViewModel
class WorkEntryListViewModel @Inject constructor(
    workEntryRepository: WorkEntryRepository,
) : ViewModel() {

    val entries: Flow<PagingData<WorkEntryListRow>> = Pager(
        config = PagingConfig(pageSize = 20, enablePlaceholders = false),
        pagingSourceFactory = { workEntryRepository.pagedAllWithDetails() },
    ).flow.cachedIn(viewModelScope)
}
