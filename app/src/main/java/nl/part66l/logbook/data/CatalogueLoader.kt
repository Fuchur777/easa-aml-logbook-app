package nl.part66l.logbook.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.part66l.logbook.domain.RuleStatus

/**
 * Parses a catalogue document — the seeded JSON described in spec §6, e.g.
 * `app/src/main/assets/catalogue/appendix-ii-2026.1.json` — into rows ready for
 * [CatalogueDao.upsertAll]. Pure and Android-free so it can be unit tested without
 * an asset manager or instrumentation.
 */
object CatalogueLoader {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(source: String): List<CatalogueTaskEntity> {
        val document = json.decodeFromString<CatalogueDocument>(source)
        val status = RuleStatus.valueOf(document.status)
        return document.tasks.map { task ->
            CatalogueTaskEntity(
                id = task.id,
                catalogueVersion = document.version,
                table = task.table,
                section = task.section,
                sectionCode = task.sectionCode,
                text = task.text,
                reference = task.reference,
                appliesToL1 = task.appliesTo.L1,
                appliesToL1C = task.appliesTo.L1C,
                appliesToL2 = task.appliesTo.L2,
                appliesToL2C = task.appliesTo.L2C,
                supersedes = task.supersedes,
                status = status,
            )
        }
    }
}

@Serializable
data class CatalogueDocument(
    val catalogueId: String,
    val version: String,
    val status: String,
    val tasks: List<CatalogueTaskDto>,
)

@Serializable
data class CatalogueTaskDto(
    val id: String,
    val table: String,
    val section: String,
    val sectionCode: String,
    val text: String,
    val appliesTo: CatalogueApplicability,
    val reference: String,
    val supersedes: String? = null,
)

@Serializable
data class CatalogueApplicability(
    val L1: Boolean,
    val L1C: Boolean,
    val L2: Boolean,
    val L2C: Boolean,
)
