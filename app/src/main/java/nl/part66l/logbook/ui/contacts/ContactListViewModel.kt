package nl.part66l.logbook.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import nl.part66l.logbook.data.PersonEntity
import nl.part66l.logbook.data.PersonRepository

@HiltViewModel
class ContactListViewModel @Inject constructor(
    personRepository: PersonRepository,
) : ViewModel() {

    // Archived contacts stay in the list, marked as such, rather than behind a
    // separate setting — same convention as the Document directory.
    val contacts: StateFlow<List<PersonEntity>> = personRepository.observeAll(includeArchived = true)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
