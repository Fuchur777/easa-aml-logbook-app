package nl.part66l.logbook.ui.documents

import java.time.LocalDate
import nl.part66l.logbook.domain.DocumentCategory

/** Same validation pattern as every other form in this app: raw fields, computed errors, canSave gates Save. */
data class DocumentFormState(
    /** Null while adding a new document; set once an existing one has been loaded for editing. */
    val documentId: String? = null,
    val name: String = "",
    val category: DocumentCategory = DocumentCategory.MANUAL,
    val revision: String = "",
    val revisionDate: LocalDate? = null,
    val link: String = "",
    /** PDF only, for now. Both blank means no attachment; [pdfPath] is app-internal storage, [pdfFileName] is the original name for display. */
    val pdfPath: String = "",
    val pdfFileName: String = "",
    val archived: Boolean = false,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val deleting: Boolean = false,
) {
    val isEditing: Boolean get() = documentId != null

    val nameError: String? get() = if (name.isBlank()) "Name is required" else null

    val canSave: Boolean get() = !saving && !loading && nameError == null
    val canDelete: Boolean get() = isEditing && !saving && !deleting
}
