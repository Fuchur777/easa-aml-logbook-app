package nl.part66l.logbook.ui.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import nl.part66l.logbook.data.DocumentEntity
import nl.part66l.logbook.data.DocumentRepository

@HiltViewModel
class DocumentListViewModel @Inject constructor(
    documentRepository: DocumentRepository,
) : ViewModel() {

    // Archived documents stay in the list, marked as such, rather than behind a
    // separate setting — the directory is small and this keeps the screen simple.
    val documents: StateFlow<List<DocumentEntity>> = documentRepository.observeAll(includeArchived = true)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
