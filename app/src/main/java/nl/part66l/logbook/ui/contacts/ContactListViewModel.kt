package nl.part66l.logbook.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.PersonEntity
import nl.part66l.logbook.data.PersonRepository
import nl.part66l.logbook.data.SettingsRepository

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContactListViewModel @Inject constructor(
    personRepository: PersonRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Toggled on the list itself and remembered, same as the aircraft and document directories. */
    val showArchived: StateFlow<Boolean> = settingsRepository.showArchivedContacts
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val contacts: StateFlow<List<PersonEntity>> = settingsRepository.showArchivedContacts
        .flatMapLatest { includeArchived -> personRepository.observeAll(includeArchived) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun onShowArchivedChange(value: Boolean) {
        viewModelScope.launch { settingsRepository.setShowArchivedContacts(value) }
    }
}
