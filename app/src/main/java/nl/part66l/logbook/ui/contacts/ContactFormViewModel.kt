package nl.part66l.logbook.ui.contacts

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
import nl.part66l.logbook.data.PersonRepository
import nl.part66l.logbook.ui.navigation.Destination

@HiltViewModel
class ContactFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val personRepository: PersonRepository,
) : ViewModel() {

    private val contactId: String? = savedStateHandle[Destination.ContactEdit.ARG_CONTACT_ID]

    private val _state = MutableStateFlow(ContactFormState(contactId = contactId, loading = contactId != null))
    val state: StateFlow<ContactFormState> = _state.asStateFlow()

    /** Baseline to detect unsaved changes against — updated to match [_state] right after load and right after every save. */
    private var initialState: ContactFormState = _state.value

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    private val _deleted = MutableSharedFlow<Unit>()
    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()

    init {
        contactId?.let { id ->
            viewModelScope.launch {
                val entity = personRepository.byId(id)
                if (entity != null) {
                    _state.update {
                        it.copy(
                            name = entity.name,
                            licenceNumber = entity.licenceNumber.orEmpty(),
                            email = entity.email.orEmpty(),
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
        fun ContactFormState.normalized() = copy(loading = false, saving = false, deleting = false, archived = false)
        return _state.value.normalized() != initialState.normalized()
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }
    fun onLicenceNumberChange(value: String) = _state.update { it.copy(licenceNumber = value) }
    fun onEmailChange(value: String) = _state.update { it.copy(email = value) }

    fun onArchiveToggle() {
        val id = _state.value.contactId ?: return
        val newValue = !_state.value.archived
        viewModelScope.launch {
            personRepository.setArchived(id, newValue)
            _state.update { it.copy(archived = newValue) }
        }
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val id = current.contactId
            if (id == null) {
                personRepository.create(
                    name = current.name.trim(),
                    licenceNumber = current.licenceNumber.trim().ifBlank { null },
                    email = current.email.trim().ifBlank { null },
                )
            } else {
                personRepository.update(
                    id = id,
                    name = current.name.trim(),
                    licenceNumber = current.licenceNumber.trim().ifBlank { null },
                    email = current.email.trim().ifBlank { null },
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
        val id = current.contactId ?: return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true) }
            personRepository.delete(id)
            _state.update { it.copy(deleting = false) }
            _deleted.emit(Unit)
        }
    }
}
