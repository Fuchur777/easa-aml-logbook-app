package nl.part66l.logbook.ui.contacts

/** Same validation pattern as every other form in this app: raw fields, computed errors, canSave gates Save. */
data class ContactFormState(
    /** Null while adding a new contact; set once an existing one has been loaded for editing. */
    val contactId: String? = null,
    val name: String = "",
    val licenceNumber: String = "",
    val email: String = "",
    val archived: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val deleting: Boolean = false,
) {
    val isEditing: Boolean get() = contactId != null

    val nameError: String? get() = if (name.isBlank()) "Name is required" else null

    val canSave: Boolean get() = !saving && !loading && nameError == null
    val canDelete: Boolean get() = isEditing && !saving && !deleting
}
