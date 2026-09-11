package nl.part66l.logbook.ui.crs

/** Not saved to the entry itself — collected fresh each time a certificate is generated. */
data class CrsFormState(
    val limitations: String = "",
    val maintenanceIncomplete: Boolean = false,
    /** Only meaningful while [maintenanceIncomplete] is set — raised against the new certificate once it's generated (§5.7). */
    val deferredItemDescriptions: List<String> = emptyList(),
    val generating: Boolean = false,
    /** Set only while [signNow][CrsViewModel.signNow] (§9.3) is running — the biometric prompt itself happens before this, at the Compose layer. */
    val signing: Boolean = false,
    val error: CrsGenerationError? = null,
) {

    /**
     * Whether anything has been typed that would be lost on leaving.
     *
     * Unlike the other forms there is nothing to save here — this state is only ever
     * consumed by generating a certificate, and reset once one is issued. So leaving is
     * always a discard, and the prompt offers to stay rather than to save.
     */
    val hasUnissuedInput: Boolean
        get() = limitations.isNotBlank() || maintenanceIncomplete || deferredItemDescriptions.isNotEmpty()
}

/** What can stop [CrsViewModel.generate] or [CrsViewModel.signNow] from producing a certificate. */
sealed interface CrsGenerationError {
    /** The work entry backing this screen was deleted from under it. */
    data object EntryNotFound : CrsGenerationError

    /** The user dismissed or cancelled the biometric prompt — not a failure worth alarming over. */
    data object BiometricCancelled : CrsGenerationError

    /** No usable biometric enrolled, or the hardware doesn't support Class 3 biometric auth. */
    data object BiometricUnavailable : CrsGenerationError

    /** The signing key was invalidated (e.g. a new fingerprint was enrolled since it was created) — signing again generates a fresh key. */
    data object KeyInvalidated : CrsGenerationError

    /** The device has no StrongBox; the key fell back to the TEE. Informational, not a failure — surfaced so the user knows before relying on it. */
    data object StrongBoxUnavailable : CrsGenerationError

    data class Other(val message: String) : CrsGenerationError
}
