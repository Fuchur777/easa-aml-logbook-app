package nl.part66l.logbook.ui.documents

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.DocumentRepository
import nl.part66l.logbook.domain.DocumentCategory
import nl.part66l.logbook.ui.navigation.Destination

@HiltViewModel
class DocumentFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
) : ViewModel() {

    private val documentId: String? = savedStateHandle[Destination.DocumentEdit.ARG_DOCUMENT_ID]

    private val _state = MutableStateFlow(DocumentFormState(documentId = documentId, loading = documentId != null))
    val state: StateFlow<DocumentFormState> = _state.asStateFlow()

    /** Baseline to detect unsaved changes against — updated to match [_state] right after load and right after every save. */
    private var initialState: DocumentFormState = _state.value

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    private val _deleted = MutableSharedFlow<Unit>()
    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()

    init {
        documentId?.let { id ->
            viewModelScope.launch {
                val entity = documentRepository.byId(id)
                if (entity != null) {
                    _state.update {
                        it.copy(
                            name = entity.name,
                            category = entity.category,
                            revision = entity.revision.orEmpty(),
                            link = entity.link.orEmpty(),
                            pdfPath = entity.pdfPath.orEmpty(),
                            pdfFileName = entity.pdfFileName.orEmpty(),
                            archived = entity.archived,
                            loading = false,
                        )
                    }
                } else {
                    _state.update { it.copy(loading = false) }
                }
                initialState = _state.value
            }
        }
    }

    /** Whether the form differs from what's saved — gates the unsaved-changes prompt on close/back. Archive/loading/etc. are metadata, not pending edits. */
    fun isDirty(): Boolean {
        fun DocumentFormState.normalized() = copy(loading = false, saving = false, deleting = false, archived = false)
        return _state.value.normalized() != initialState.normalized()
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }
    fun onCategoryChange(value: DocumentCategory) = _state.update { it.copy(category = value) }
    fun onRevisionChange(value: String) = _state.update { it.copy(revision = value) }
    fun onLinkChange(value: String) = _state.update { it.copy(link = value) }

    fun onPdfAttached(path: String, fileName: String) = _state.update { it.copy(pdfPath = path, pdfFileName = fileName) }
    fun onPdfRemoved() = _state.update { it.copy(pdfPath = "", pdfFileName = "") }

    fun onArchiveToggle() {
        val id = _state.value.documentId ?: return
        val newValue = !_state.value.archived
        viewModelScope.launch {
            documentRepository.setArchived(id, newValue)
            _state.update { it.copy(archived = newValue) }
        }
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val id = current.documentId
            if (id == null) {
                documentRepository.create(
                    name = current.name.trim(),
                    category = current.category,
                    revision = current.revision.trim().ifBlank { null },
                    link = current.link.trim().ifBlank { null },
                    pdfPath = current.pdfPath.ifBlank { null },
                    pdfFileName = current.pdfFileName.ifBlank { null },
                )
            } else {
                documentRepository.update(
                    id = id,
                    name = current.name.trim(),
                    category = current.category,
                    revision = current.revision.trim().ifBlank { null },
                    link = current.link.trim().ifBlank { null },
                    pdfPath = current.pdfPath.ifBlank { null },
                    pdfFileName = current.pdfFileName.ifBlank { null },
                )
            }
            _state.update { it.copy(saving = false) }
            initialState = _state.value
            _saved.emit(Unit)
        }
    }

    fun delete() {
        val current = _state.value
        if (!current.canDelete) return
        val id = current.documentId ?: return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true) }
            documentRepository.delete(id)
            _state.update { it.copy(deleting = false) }
            _deleted.emit(Unit)
        }
    }
}
