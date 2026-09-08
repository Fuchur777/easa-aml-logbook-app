package nl.part66l.logbook.ui.crs

/** Not saved to the entry itself — collected fresh each time a certificate is generated. */
data class CrsFormState(
    val limitations: String = "",
    val maintenanceIncomplete: Boolean = false,
    val generating: Boolean = false,
)
