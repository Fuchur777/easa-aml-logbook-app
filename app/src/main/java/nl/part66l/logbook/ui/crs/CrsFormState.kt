package nl.part66l.logbook.ui.crs

/** Not saved to the entry itself — collected fresh each time a certificate is generated. */
data class CrsFormState(
    val limitations: String = "",
    val maintenanceIncomplete: Boolean = false,
    /** Only meaningful while [maintenanceIncomplete] is set — raised against the new certificate once it's generated (§5.7). */
    val deferredItemDescriptions: List<String> = emptyList(),
    val generating: Boolean = false,
)
