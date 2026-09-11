package nl.part66l.logbook.ui.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.AircraftRepository
import nl.part66l.logbook.data.AircraftWithRegistration
import nl.part66l.logbook.data.DocumentEntity
import nl.part66l.logbook.data.DocumentRepository
import nl.part66l.logbook.data.SettingsRepository
import nl.part66l.logbook.domain.DocumentCategory

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DocumentListViewModel @Inject constructor(
    documentRepository: DocumentRepository,
    aircraftRepository: AircraftRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _category = MutableStateFlow<DocumentCategory?>(null)
    val category: StateFlow<DocumentCategory?> = _category.asStateFlow()

    private val _aircraftId = MutableStateFlow<String?>(null)
    val aircraftId: StateFlow<String?> = _aircraftId.asStateFlow()

    val showArchived: StateFlow<Boolean> = settingsRepository.showArchivedDocuments
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Aircraft to filter by — archived ones included, since documents used on them still exist. */
    val aircraftOptions: StateFlow<List<AircraftWithRegistration>> =
        aircraftRepository.observeAllWithRegistration(includeArchived = true)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val documents: StateFlow<List<DocumentEntity>> =
        combine(settingsRepository.showArchivedDocuments, _category, _aircraftId) { showArchived, category, aircraftId ->
            Triple(showArchived, category, aircraftId)
        }.flatMapLatest { (showArchived, category, aircraftId) ->
            documentRepository.observeFiltered(includeArchived = showArchived, category = category, aircraftId = aircraftId)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun onCategoryChange(value: DocumentCategory?) {
        _category.value = value
    }

    fun onAircraftChange(value: String?) {
        _aircraftId.value = value
    }

    fun onShowArchivedChange(value: Boolean) {
        viewModelScope.launch { settingsRepository.setShowArchivedDocuments(value) }
    }
}
