package nl.part66l.logbook.fakes

import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import nl.part66l.logbook.data.DocumentEntity
import nl.part66l.logbook.data.DocumentRepository
import nl.part66l.logbook.domain.DocumentCategory

class FakeDocumentRepository(initial: List<DocumentEntity> = emptyList()) : DocumentRepository {
    private val documents = MutableStateFlow(initial)

    override fun observeAll(includeArchived: Boolean): Flow<List<DocumentEntity>> =
        documents.map { list -> list.filter { includeArchived || !it.archived } }

    override suspend fun byId(id: String): DocumentEntity? = documents.value.find { it.id == id }

    override suspend fun create(
        name: String,
        category: DocumentCategory,
        revision: String?,
        revisionDate: LocalDate?,
        link: String?,
        pdfPath: String?,
        pdfFileName: String?,
    ): String {
        val id = UUID.randomUUID().toString()
        documents.value = documents.value + DocumentEntity(
            id = id, name = name, category = category, revision = revision, revisionDate = revisionDate, link = link,
            pdfPath = pdfPath, pdfFileName = pdfFileName,
        )
        return id
    }

    override suspend fun update(
        id: String,
        name: String,
        category: DocumentCategory,
        revision: String?,
        revisionDate: LocalDate?,
        link: String?,
        pdfPath: String?,
        pdfFileName: String?,
    ) {
        documents.value = documents.value.map {
            if (it.id == id) {
                it.copy(
                    name = name, category = category, revision = revision, revisionDate = revisionDate,
                    link = link, pdfPath = pdfPath, pdfFileName = pdfFileName,
                )
            } else {
                it
            }
        }
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        documents.value = documents.value.map { if (it.id == id) it.copy(archived = archived) else it }
    }

    override suspend fun delete(id: String) {
        documents.value = documents.value.filterNot { it.id == id }
    }
}
