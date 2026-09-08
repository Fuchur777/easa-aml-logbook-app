package nl.part66l.logbook.data

import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import nl.part66l.logbook.domain.DocumentCategory

interface DocumentRepository {
    /** Ordered by category then name — what both the management screen and the entry-form picker render. */
    fun observeAll(includeArchived: Boolean): Flow<List<DocumentEntity>>

    suspend fun byId(id: String): DocumentEntity?

    suspend fun create(
        name: String,
        category: DocumentCategory,
        revision: String?,
        revisionDate: LocalDate? = null,
        link: String?,
        pdfPath: String? = null,
        pdfFileName: String? = null,
    ): String

    suspend fun update(
        id: String,
        name: String,
        category: DocumentCategory,
        revision: String?,
        revisionDate: LocalDate? = null,
        link: String?,
        pdfPath: String? = null,
        pdfFileName: String? = null,
    )

    suspend fun setArchived(id: String, archived: Boolean)

    suspend fun delete(id: String)
}

/**
 * Documents are picked from here into a [WorkEntryRepository.create]'s [DocumentationRefInput]s
 * by name/revision snapshot, not by id — archiving or deleting a document here never touches
 * a past entry's documentation-used record, same relationship [CatalogueRepository] has with
 * [TaskCompletionEntity].
 */
@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val documentDao: DocumentDao,
) : DocumentRepository {

    override fun observeAll(includeArchived: Boolean): Flow<List<DocumentEntity>> = documentDao.observeAll(includeArchived)

    override suspend fun byId(id: String): DocumentEntity? = documentDao.byId(id)

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
        documentDao.insert(
            DocumentEntity(
                id = id, name = name, category = category, revision = revision, revisionDate = revisionDate, link = link,
                pdfPath = pdfPath, pdfFileName = pdfFileName,
            ),
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
        val existing = documentDao.byId(id) ?: return
        documentDao.update(
            existing.copy(
                name = name, category = category, revision = revision, revisionDate = revisionDate, link = link,
                pdfPath = pdfPath, pdfFileName = pdfFileName,
            ),
        )
    }

    override suspend fun setArchived(id: String, archived: Boolean) = documentDao.setArchived(id, archived)

    override suspend fun delete(id: String) = documentDao.delete(id)
}
