package nl.part66l.logbook.ui.crs

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricPrompt
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.part66l.logbook.data.CrsEntity
import nl.part66l.logbook.data.CrsRepository
import nl.part66l.logbook.data.DeferredItemRepository
import nl.part66l.logbook.signing.BiometricSigningException
import nl.part66l.logbook.signing.LocalKeystoreSigner
import nl.part66l.logbook.ui.navigation.Destination

@HiltViewModel
class CrsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val crsRepository: CrsRepository,
    private val deferredItemRepository: DeferredItemRepository,
) : ViewModel() {

    private val entryId: String = savedStateHandle[Destination.Crs.ARG_ENTRY_ID]
        ?: error("CrsViewModel requires ${Destination.Crs.ARG_ENTRY_ID}")

    /** Every certificate already issued against this entry — a fresh generation adds a row, never replaces one. */
    val issued: StateFlow<List<CrsEntity>> = crsRepository.forEntry(entryId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _state = MutableStateFlow(CrsFormState())
    val state: StateFlow<CrsFormState> = _state.asStateFlow()

    fun onLimitationsChange(value: String) = _state.update { it.copy(limitations = value) }

    /** Unchecking clears any queued deferred items — they only mean something while this is checked. */
    fun onMaintenanceIncompleteChange(value: Boolean) = _state.update {
        it.copy(maintenanceIncomplete = value, deferredItemDescriptions = if (value) it.deferredItemDescriptions else emptyList())
    }

    fun onDeferredItemAdd(description: String) {
        val trimmed = description.trim()
        if (trimmed.isBlank()) return
        _state.update { it.copy(deferredItemDescriptions = it.deferredItemDescriptions + trimmed) }
    }

    fun onDeferredItemRemove(index: Int) = _state.update {
        it.copy(deferredItemDescriptions = it.deferredItemDescriptions.filterIndexed { i, _ -> i != index })
    }

    /** Adds straight to the record — [issued] picks it up reactively, no round trip needed here. */
    fun onPhotoAttached(crsId: String, path: String) {
        viewModelScope.launch { crsRepository.setSignedPhoto(crsId, path) }
    }

    /** Clears a wrongly attached photo — the slot goes back to "tap to attach", same as if none had been picked. */
    fun onPhotoRemoved(crsId: String) {
        viewModelScope.launch { crsRepository.setSignedPhoto(crsId, null) }
    }

    fun generate() {
        val current = _state.value
        if (current.generating) return
        viewModelScope.launch {
            _state.update { it.copy(generating = true, error = null) }
            val crs = crsRepository.generateUnsigned(
                entryId = entryId,
                limitations = current.limitations.trim().ifBlank { null },
                maintenanceIncomplete = current.maintenanceIncomplete,
                deferredItemDescriptions = current.deferredItemDescriptions,
            )
            if (crs == null) {
                _state.update { it.copy(generating = false, error = CrsGenerationError.EntryNotFound) }
                return@launch
            }
            // Raised against the number just allocated — a deferred item always cites a real, issued CRS.
            current.deferredItemDescriptions.forEach { deferredItemRepository.raise(crs.id, it) }
            _state.value = CrsFormState() // reset for the next one, now that generating is done
        }
    }

    /**
     * Signs on-device (§9.3) with [signer], which must already be authorized for one signature —
     * see [nl.part66l.logbook.signing.BiometricSigningGate]. The biometric prompt itself happens
     * before this is called, at the Compose layer; this never shows one.
     */
    fun signNow(signer: LocalKeystoreSigner) {
        val current = _state.value
        if (current.signing) return
        viewModelScope.launch {
            _state.update { it.copy(signing = true, error = null) }
            val result = crsRepository.signLocal(
                entryId = entryId,
                limitations = current.limitations.trim().ifBlank { null },
                maintenanceIncomplete = current.maintenanceIncomplete,
                deferredItemDescriptions = current.deferredItemDescriptions,
                signer = signer,
            )
            when {
                result == null -> _state.update { it.copy(signing = false, error = CrsGenerationError.EntryNotFound) }
                result.isSuccess -> {
                    val crs = result.getOrThrow()
                    current.deferredItemDescriptions.forEach { deferredItemRepository.raise(crs.id, it) }
                    _state.value = CrsFormState()
                }
                else -> _state.update { it.copy(signing = false, error = mapSigningError(result.exceptionOrNull())) }
            }
        }
    }

    /** For a failure in [nl.part66l.logbook.signing.BiometricSigningGate.authorize] itself — before there's an authorized signer to hand [signNow]. */
    fun onSignAuthorizationFailed(error: Throwable) = _state.update { it.copy(signing = false, error = mapSigningError(error)) }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun mapSigningError(error: Throwable?): CrsGenerationError = when {
        error is KeyPermanentlyInvalidatedException -> CrsGenerationError.KeyInvalidated
        error is BiometricSigningException && error.errorCode in CANCELLED_BIOMETRIC_ERROR_CODES -> CrsGenerationError.BiometricCancelled
        error is BiometricSigningException && error.errorCode in UNAVAILABLE_BIOMETRIC_ERROR_CODES -> CrsGenerationError.BiometricUnavailable
        error != null -> CrsGenerationError.Other(error.message ?: error::class.simpleName.orEmpty())
        else -> CrsGenerationError.Other("Signing failed")
    }

    private companion object {
        val CANCELLED_BIOMETRIC_ERROR_CODES = setOf(
            BiometricPrompt.ERROR_CANCELED,
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        )
        val UNAVAILABLE_BIOMETRIC_ERROR_CODES = setOf(
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
        )
    }
}
