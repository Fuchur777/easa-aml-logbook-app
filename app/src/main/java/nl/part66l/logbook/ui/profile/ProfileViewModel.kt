package nl.part66l.logbook.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.ProfileRepository
import nl.part66l.logbook.domain.Subcategory

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileFormState())
    val state: StateFlow<ProfileFormState> = _state.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    init {
        viewModelScope.launch {
            profileRepository.get()?.let { existing -> _state.value = existing.toFormState() }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value) }
    fun onLicenceNumberChange(value: String) = _state.update { it.copy(licenceNumber = value) }
    fun onIssuingAuthorityChange(value: String) = _state.update { it.copy(issuingAuthority = value) }
    fun onLicenceExpiryChange(value: LocalDate?) = _state.update { it.copy(licenceExpiry = value) }

    fun onSubcategoryToggle(subcategory: Subcategory, checked: Boolean) = _state.update {
        when (subcategory) {
            Subcategory.L1 -> it.copy(holdsL1 = checked)
            Subcategory.L1C -> it.copy(holdsL1C = checked)
            Subcategory.L2 -> it.copy(holdsL2 = checked)
            Subcategory.L2C -> it.copy(holdsL2C = checked)
        }
    }

    fun onRecencyReductionGrantedChange(value: Boolean) = _state.update { it.copy(recencyReductionGranted = value) }
    fun onRecencyReductionAuthorityChange(value: String) = _state.update { it.copy(recencyReductionAuthority = value) }
    fun onRecencyReductionReferenceChange(value: String) = _state.update { it.copy(recencyReductionReference = value) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            profileRepository.upsert(current.toEntity())
            _state.update { it.copy(saving = false) }
            _saved.emit(Unit)
        }
    }
}
