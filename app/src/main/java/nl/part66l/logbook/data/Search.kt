package nl.part66l.logbook.data

import androidx.paging.PagingSource
import androidx.room.*

/**
 * Standalone FTS4 index over an entry's free text.
 *
 * Standalone rather than external-content because helper names live in `person`,
 * reached through a join, and `contentEntity` can only mirror one entity. The
 * repository owns population: whenever an entry is saved, a helper is added or
 * removed, or a person is renamed, the affected rows are rebuilt. A person rename
 * touches every entry they appear on — that is the price of denormalised text.
 *
 * Columns are split rather than concatenated so queries can be scoped
 * (`description:rudder`) and so fields can be weighted later without a migration.
 *
 * `remove_diacritics=1` matters for Dutch and German names: without it "Grünberg"
 * and "Grunberg" are different words.
 *
 * Identifiers are deliberately absent — see [SearchDao].
 */
@Fts4(
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=1"],
    notIndexed = ["entryId"],
)
@Entity(tableName = "work_entry_fts")
data class WorkEntryFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long = 0,
    val entryId: String,
    val description: String,
    val workorder: String,
    val people: String,
)

/**
 * Identifiers are punctuated, and FTS tokenizers split them badly: `PH-1234`
 * becomes `ph` + `1234`, so a partial `PH-12` matches nothing and `CRS-2026-0007`
 * is unreachable by searching `7`. They are therefore held in normalised, indexed
 * columns and queried by prefix instead.
 */
object Identifiers {

    /** Uppercase, strip everything that isn't a letter or digit. `ph-1234` → `PH1234`. */
    fun normalise(raw: String?): String =
        raw?.uppercase()?.filter { it.isLetterOrDigit() } ?: ""

    /** Prefix pattern for a LIKE query. Index-usable because the wildcard trails. */
    fun prefixPattern(raw: String): String = normalise(raw) + "%"

    /**
     * Trailing digit group of a CRS number, so `CRS-2026-0007` is findable by `7`.
     * Returns null when the query isn't numeric.
     */
    fun sequenceOrNull(raw: String): Int? =
        Regex("(\\d+)\\s*$").find(raw.trim())?.groupValues?.get(1)?.toIntOrNull()
}

/**
 * Two paths, one search box. Both queries always run and results are merged —
 * at 10,000 entries each is sub-millisecond, so dispatching on a heuristic would
 * save nothing and would occasionally guess wrong.
 */
@Dao
interface SearchDao {

    @Query("""
        SELECT e.* FROM work_entry e
        JOIN work_entry_fts f ON f.entryId = e.id
        WHERE work_entry_fts MATCH :ftsQuery
    """)
    suspend fun byText(ftsQuery: String): List<WorkEntryEntity>

    /**
     * Registrations, CRS numbers, part numbers and documentation references.
     * [sequence] catches the "CRS 7" case where leading zeros would otherwise hide
     * the record.
     */
    @Query("""
        SELECT DISTINCT e.* FROM work_entry e
        LEFT JOIN aircraft_registration r ON r.aircraftId = e.aircraftId
        LEFT JOIN crs c ON c.entryId = e.id
        LEFT JOIN part_used p ON p.entryId = e.id
        LEFT JOIN documentation_ref d ON d.entryId = e.id
        WHERE r.registrationNormalised LIKE :prefix
           OR c.numberNormalised LIKE :prefix
           OR p.partNumberNormalised LIKE :prefix
           OR d.referenceNormalised LIKE :prefix
           OR e.workorderReferenceNormalised LIKE :prefix
           OR (:sequence IS NOT NULL AND c.sequence = :sequence)
    """)
    suspend fun byIdentifier(prefix: String, sequence: Int?): List<WorkEntryEntity>

    @Transaction
    @Query("SELECT * FROM work_entry WHERE id IN (:ids)")
    fun pagedByIds(ids: List<String>): PagingSource<Int, WorkEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIndex(row: WorkEntryFts)

    @Query("DELETE FROM work_entry_fts WHERE entryId = :entryId")
    suspend fun deleteIndex(entryId: String)
}

/**
 * Merges the two paths. Identifier hits lead, because a user who typed a
 * registration wants that aircraft's work, not every entry mentioning the number.
 */
class SearchRepository(private val dao: SearchDao) {

    suspend fun search(query: String): List<WorkEntryEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val byId = dao.byIdentifier(
            prefix = Identifiers.prefixPattern(trimmed),
            sequence = Identifiers.sequenceOrNull(trimmed),
        )
        val byText = runCatching { dao.byText(toFtsQuery(trimmed)) }.getOrDefault(emptyList())

        val seen = LinkedHashMap<String, WorkEntryEntity>()
        byId.forEach { seen[it.id] = it }
        byText.forEach { seen.putIfAbsent(it.id, it) }
        return seen.values.toList()
    }

    /**
     * FTS MATCH syntax is unforgiving of user punctuation, so tokens are extracted
     * and a trailing wildcard added for as-you-type behaviour.
     */
    private fun toFtsQuery(raw: String): String =
        raw.split(Regex("\\W+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
}
