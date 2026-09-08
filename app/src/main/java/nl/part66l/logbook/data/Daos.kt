package nl.part66l.logbook.data

import androidx.paging.PagingSource
import androidx.room.*
// FTS index, identifier normalisation and search live in Search.kt
import kotlinx.coroutines.flow.Flow
import nl.part66l.logbook.domain.*
import java.time.LocalDate

@Dao
interface WorkEntryDao {

    @Insert suspend fun insert(entry: WorkEntryEntity)
    @Update suspend fun update(entry: WorkEntryEntity)
    @Insert suspend fun insertActivityTypes(rows: List<WorkEntryActivityTypeEntity>)

    @Query("SELECT * FROM work_entry_activity_type WHERE entryId = :entryId")
    suspend fun activityTypesForEntry(entryId: String): List<WorkEntryActivityTypeEntity>

    @Query("DELETE FROM work_entry_activity_type WHERE entryId = :entryId")
    suspend fun deleteActivityTypesForEntry(entryId: String)

    /** Cascades to every child table (sessions, helpers, documentation, parts, task completions) via their FKs. */
    @Query("DELETE FROM work_entry WHERE id = :id")
    suspend fun delete(id: String)

    @Transaction
    @Query("SELECT * FROM work_entry ORDER BY id DESC")
    fun pagedAll(): PagingSource<Int, WorkEntryEntity>

    @Transaction
    @Query("SELECT * FROM work_entry WHERE id = :id")
    suspend fun byId(id: String): WorkEntryEntity?

    /**
     * Structured filtering. Null arguments mean "no constraint", which keeps one
     * query serving the whole filter UI instead of a combinatorial explosion.
     */
    @Transaction
    @Query("""
        SELECT e.* FROM work_entry e
        LEFT JOIN work_session s ON s.entryId = e.id
        WHERE (:aircraftId IS NULL OR e.aircraftId = :aircraftId)
          AND (:role IS NULL OR e.role = :role)
          AND (:annualOnly = 0 OR e.annualInspection = 1)
          AND (:provenance IS NULL OR e.provenance = :provenance)
          AND (:from IS NULL OR s.date >= :from)
          AND (:to IS NULL OR s.date <= :to)
        GROUP BY e.id
        ORDER BY MAX(s.date) DESC
    """)
    fun filtered(
        aircraftId: String?,
        role: EntryRole?,
        annualOnly: Boolean,
        provenance: Provenance?,
        from: LocalDate?,
        to: LocalDate?,
    ): PagingSource<Int, WorkEntryEntity>

    /**
     * What the list screen actually renders: each entry with its latest session date,
     * its aircraft's current registration, and its activity types (comma-joined).
     * The session and activity-type joins each multiply rows per entry, but MAX and
     * GROUP_CONCAT(DISTINCT ...) are both unaffected by that duplication.
     */
    @Transaction
    @Query("""
        SELECT e.*, MAX(s.date) AS workDate, ar.registration AS aircraftRegistration,
               GROUP_CONCAT(DISTINCT wat.activityType) AS activityTypesCsv
        FROM work_entry e
        LEFT JOIN work_session s ON s.entryId = e.id
        LEFT JOIN aircraft_registration ar ON ar.aircraftId = e.aircraftId AND ar.validTo IS NULL
        LEFT JOIN work_entry_activity_type wat ON wat.entryId = e.id
        GROUP BY e.id
        ORDER BY workDate DESC
    """)
    fun pagedAllWithDetails(): PagingSource<Int, WorkEntryListRow>
}

@Dao
interface PersonDao {
    @Insert suspend fun insert(person: PersonEntity)
    @Update suspend fun update(person: PersonEntity)

    @Query("SELECT * FROM person ORDER BY name")
    fun all(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM person WHERE archived = 0 OR :includeArchived = 1 ORDER BY name")
    fun observeAll(includeArchived: Boolean): Flow<List<PersonEntity>>

    @Query("SELECT * FROM person WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): PersonEntity?

    @Query("SELECT * FROM person WHERE id = :id")
    suspend fun byId(id: String): PersonEntity?

    @Query("UPDATE person SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("DELETE FROM person WHERE id = :id")
    suspend fun delete(id: String)
}

/** Document directory (§5.3) — management screen CRUD, plus what the entry form's documentation picker reads from. */
@Dao
interface DocumentDao {
    @Insert suspend fun insert(document: DocumentEntity)
    @Update suspend fun update(document: DocumentEntity)

    @Query("SELECT * FROM document WHERE id = :id")
    suspend fun byId(id: String): DocumentEntity?

    @Query("SELECT * FROM document WHERE archived = 0 OR :includeArchived = 1 ORDER BY category, name")
    fun observeAll(includeArchived: Boolean): Flow<List<DocumentEntity>>

    @Query("UPDATE document SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("DELETE FROM document WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface RecencyDao {

    /**
     * Route A. AMC 66.A.20(b)(2) permits the 6-month period to be replaced by 100
     * days of experience — 50 where the competent authority has agreed in advance.
     * Counted as distinct local dates in the trailing window.
     *
     * Every logged day counts. The AMC's 20% substitution allowance for training,
     * technical support and maintenance planning is deliberately not modelled — it
     * is explained in the help text and left to the user, rather than becoming a
     * flag on every entry.
     *
     * Imported history counts too, but the UI renders it separately as declared
     * rather than evidenced.
     *
     * Split from [distinctDaysForAircraft] rather than taking a nullable list:
     * binding the same collection parameter both as a scalar (`:x IS NULL`) and as
     * an `IN (:x)` multi-bind compiles under Room but breaks at runtime for any
     * list with more than one element — SQLite reads the expanded `IS NULL` site as
     * a row-value expression and throws "row value misused".
     */
    @Query("""
        SELECT COUNT(DISTINCT s.date)
        FROM work_session s
        JOIN work_entry e ON e.id = s.entryId
        WHERE s.date >= :windowStart
    """)
    suspend fun distinctDays(windowStart: LocalDate): Int

    /** As [distinctDays], restricted to the given aircraft. [aircraftIds] must be non-empty. */
    @Query("""
        SELECT COUNT(DISTINCT s.date)
        FROM work_session s
        JOIN work_entry e ON e.id = s.entryId
        WHERE s.date >= :windowStart
          AND e.aircraftId IN (:aircraftIds)
    """)
    suspend fun distinctDaysForAircraft(windowStart: LocalDate, aircraftIds: List<String>): Int

    /**
     * Dates in the window, ascending. The lapse date — "compliant until X if you
     * log nothing further" — is derived from this by finding the date on which the
     * count first falls below the threshold as early dates roll out.
     */
    @Query("""
        SELECT DISTINCT s.date FROM work_session s
        JOIN work_entry e ON e.id = s.entryId
        WHERE s.date >= :windowStart
        ORDER BY s.date ASC
    """)
    suspend fun daysInWindow(windowStart: LocalDate): List<LocalDate>

    /**
     * Route B numerator, per section. AMC 66.A.45(h) requires both 50% overall and
     * coverage of tasks from each paragraph, so the UI needs section granularity,
     * not a single percentage.
     */
    @Query("""
        SELECT t.sectionCode AS sectionCode, COUNT(DISTINCT c.taskId) AS completed
        FROM task_completion c
        JOIN catalogue_task t ON t.id = c.taskId
        JOIN work_entry e ON e.id = c.entryId
        JOIN work_session s ON s.entryId = e.id
        WHERE s.date >= :windowStart
        GROUP BY t.sectionCode
    """)
    suspend fun completedTasksBySection(windowStart: LocalDate): List<SectionCount>

    /**
     * Route C. Annual inspections count regardless of whether an airworthiness
     * review was carried out the same day — `concurrentWithArc` is not a recency
     * exclusion. It constrains which certification basis is available: a yearly
     * signed off on the same day as the ARC cannot be released under ML.A.803.
     * That rule lives in the basis-availability check, not here.
     */
    @Query("""
        SELECT COUNT(DISTINCT e.id) FROM work_entry e
        JOIN work_session s ON s.entryId = e.id
        WHERE e.annualInspection = 1
          AND s.date >= :windowStart
    """)
    suspend fun annualInspections(windowStart: LocalDate): Int

    /**
     * Raw (date, aircraft) rows for Route A, one per session in the window. Unlike
     * [daysInWindow], the aircraft is carried through so a repository can resolve
     * each date to the subcategory whose privileges it was exercised under —
     * [distinctDays] and [daysInWindow] answer "how many/which days", not "for
     * which subcategory", so they can't build [nl.part66l.logbook.domain.RecencyEvaluator]'s
     * per-subcategory input on their own.
     *
     * [includeResearchOnlyDays] excludes (when false) sessions on an entry whose activity
     * types are exactly `{RESEARCH_AND_PAPERWORK}` — see [ProfileEntity.researchCountsTowardRecency].
     * An entry that combines research with a real regulatory activity always counts regardless.
     */
    @Query("""
        SELECT DISTINCT s.date AS date, e.aircraftId AS aircraftId
        FROM work_session s
        JOIN work_entry e ON e.id = s.entryId
        WHERE s.date >= :windowStart
          AND (
            :includeResearchOnlyDays = 1
            OR (SELECT COUNT(*) FROM work_entry_activity_type wat WHERE wat.entryId = e.id) != 1
            OR NOT EXISTS (
                SELECT 1 FROM work_entry_activity_type wat2
                WHERE wat2.entryId = e.id AND wat2.activityType = 'RESEARCH_AND_PAPERWORK'
            )
          )
    """)
    suspend fun sessionsInWindow(windowStart: LocalDate, includeResearchOnlyDays: Boolean): List<SessionAircraftRow>

    /** As [sessionsInWindow], restricted to annual-inspection entries, for Route C. */
    @Query("""
        SELECT DISTINCT s.date AS date, e.aircraftId AS aircraftId
        FROM work_session s
        JOIN work_entry e ON e.id = s.entryId
        WHERE s.date >= :windowStart
          AND e.annualInspection = 1
    """)
    suspend fun annualSessionsInWindow(windowStart: LocalDate): List<SessionAircraftRow>

    /**
     * Raw completions for Route B, one row per (task_completion, session) pair,
     * carrying the completed task's per-subcategory applicability through — which
     * subcategories a completion credits is a property of the *task* (from the
     * catalogue), not of the aircraft the work happened to be performed on.
     */
    @Query("""
        SELECT c.taskId AS taskId, t.sectionCode AS sectionCode, s.date AS date,
               t.appliesToL1 AS appliesToL1, t.appliesToL1C AS appliesToL1C,
               t.appliesToL2 AS appliesToL2, t.appliesToL2C AS appliesToL2C,
               c.substituteText AS substituteText
        FROM task_completion c
        JOIN catalogue_task t ON t.id = c.taskId
        JOIN work_entry e ON e.id = c.entryId
        JOIN work_session s ON s.entryId = e.id
        WHERE s.date >= :windowStart
    """)
    suspend fun taskCompletionsInWindow(windowStart: LocalDate): List<TaskCompletionRow>
}

data class SectionCount(val sectionCode: String, val completed: Int)

data class SessionAircraftRow(val date: LocalDate, val aircraftId: String?)

data class TaskCompletionRow(
    val taskId: String,
    val sectionCode: String,
    val date: LocalDate,
    val appliesToL1: Boolean,
    val appliesToL1C: Boolean,
    val appliesToL2: Boolean,
    val appliesToL2C: Boolean,
    /** Non-null when this completion is a user-authored substitute (AMC 66.A.45(h)). */
    val substituteText: String?,
)

/** The single profile row (§4: one licence holder per installation). */
@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 'self'")
    suspend fun get(): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface CatalogueDao {

    /**
     * Route B denominator, filtered by held subcategory. This filtering is not
     * cosmetic: on an unfiltered combined list an L1 holder's 50% threshold exceeds
     * the number of tasks available to them.
     */
    @Query("""
        SELECT * FROM catalogue_task
        WHERE catalogueVersion = :version
          AND status = 'IN_FORCE'
          AND ((:l1 = 1 AND appliesToL1 = 1)
            OR (:l1c = 1 AND appliesToL1C = 1)
            OR (:l2 = 1 AND appliesToL2 = 1)
            OR (:l2c = 1 AND appliesToL2C = 1))
    """)
    suspend fun applicableTasks(
        version: String,
        l1: Boolean, l1c: Boolean, l2: Boolean, l2c: Boolean,
    ): List<CatalogueTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<CatalogueTaskEntity>)

    /** Backs first-run seeding: is there anything here yet at all? */
    @Query("SELECT COUNT(*) FROM catalogue_task")
    suspend fun count(): Int

    /** Only one catalogue version is ever seeded at a time, so any row's version is THE current one. */
    @Query("SELECT catalogueVersion FROM catalogue_task LIMIT 1")
    suspend fun currentVersion(): String?

    /** Resolves selected task ids back to their full rows — what a completion's text/version snapshot is taken from. */
    @Query("SELECT * FROM catalogue_task WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<CatalogueTaskEntity>
}

@Dao
interface CrsDao {

    /**
     * Numbers are allocated at signing, never at draft creation, so abandoned
     * drafts leave no gaps in the sequence.
     */
    @Query("SELECT number FROM crs WHERE number LIKE :prefixPattern ORDER BY number DESC LIMIT 1")
    suspend fun highestNumber(prefixPattern: String): String?

    /**
     * Every number ever issued, format-agnostic. Feeds
     * [nl.part66l.logbook.domain.CrsNumberFormat.nextNumber] and `collidesWith`,
     * which do their own format-aware filtering — a plain `LIKE` prefix can't
     * correctly scope "any year" when annual reset is off (§9.2).
     */
    @Query("SELECT number FROM crs")
    suspend fun allNumbers(): List<String>

    @Insert suspend fun insert(crs: CrsEntity)

    /** Signed certificates are never updated. Only draft and void transitions are permitted. */
    @Query("UPDATE crs SET signatureState = :state, voidReason = :reason WHERE id = :id AND signatureState IN ('DRAFT','TIMESTAMP_PENDING')")
    suspend fun transitionUnsigned(id: String, state: SignatureState, reason: String?): Int

    @Query("SELECT * FROM crs ORDER BY number DESC")
    fun all(): Flow<List<CrsEntity>>

    @Query("SELECT * FROM crs WHERE id = :id")
    suspend fun byId(id: String): CrsEntity?

    @Query("SELECT * FROM crs WHERE entryId = :entryId ORDER BY number DESC")
    fun forEntry(entryId: String): Flow<List<CrsEntity>>
}

@Dao
interface AircraftDao {
    @Insert suspend fun insert(aircraft: AircraftEntity)
    @Update suspend fun update(aircraft: AircraftEntity)
    @Insert suspend fun insertRegistration(reg: AircraftRegistrationEntity)

    @Query("SELECT * FROM aircraft WHERE manufacturer = :manufacturer AND serialNumber = :serial")
    suspend fun bySerial(manufacturer: String, serial: String): AircraftEntity?

    @Query("SELECT * FROM aircraft WHERE id = :id")
    suspend fun byId(id: String): AircraftEntity?

    /** Registration as it stood on a given date — so old certificates print correctly. */
    @Query("""
        SELECT registration FROM aircraft_registration
        WHERE aircraftId = :aircraftId
          AND validFrom <= :on
          AND (validTo IS NULL OR validTo >= :on)
        LIMIT 1
    """)
    suspend fun registrationOn(aircraftId: String, on: LocalDate): String?

    /** The current (validTo IS NULL) registration row — the one an edit needs to close out on a re-registration. */
    @Query("SELECT * FROM aircraft_registration WHERE aircraftId = :aircraftId AND validTo IS NULL LIMIT 1")
    suspend fun currentRegistrationRow(aircraftId: String): AircraftRegistrationEntity?

    @Query("UPDATE aircraft_registration SET validTo = :validTo WHERE id = :id")
    suspend fun closeRegistration(id: String, validTo: LocalDate)

    @Query("SELECT * FROM aircraft WHERE archived = 0 OR :includeArchived = 1 ORDER BY sortOrder")
    fun observeAll(includeArchived: Boolean): Flow<List<AircraftEntity>>

    @Transaction
    @Query("""
        SELECT aircraft.*, ar.registration AS registration
        FROM aircraft
        LEFT JOIN aircraft_registration ar ON ar.aircraftId = aircraft.id AND ar.validTo IS NULL
        WHERE aircraft.archived = 0 OR :includeArchived = 1
        ORDER BY aircraft.sortOrder
    """)
    fun observeAllWithRegistration(includeArchived: Boolean): Flow<List<AircraftWithRegistration>>

    @Query("UPDATE aircraft SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    @Query("UPDATE aircraft SET archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("SELECT EXISTS(SELECT 1 FROM work_entry WHERE aircraftId = :aircraftId)")
    suspend fun hasWorkHistory(aircraftId: String): Boolean

    @Query("DELETE FROM aircraft WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM aircraft")
    suspend fun maxSortOrder(): Int
}

@Dao
interface AttachmentDao {
    @Insert suspend fun insert(attachment: AttachmentEntity)

    @Query("SELECT * FROM attachment WHERE entryId = :entryId")
    suspend fun forEntry(entryId: String): List<AttachmentEntity>

    /** Backs the "verify records" function: match / missing / mismatch. */
    @Query("SELECT * FROM attachment")
    suspend fun all(): List<AttachmentEntity>

    @Query("UPDATE attachment SET driveFileId = :driveFileId WHERE id = :id")
    suspend fun setDriveFileId(id: String, driveFileId: String)
}

/**
 * The time log (§5.5). Every RecencyDao Route A query reads `work_session`; this
 * is its only write path.
 */
@Dao
interface WorkSessionDao {
    @Insert suspend fun insert(session: WorkSessionEntity)

    @Query("SELECT * FROM work_session WHERE entryId = :entryId ORDER BY date")
    suspend fun forEntry(entryId: String): List<WorkSessionEntity>

    @Query("DELETE FROM work_session WHERE entryId = :entryId")
    suspend fun deleteForEntry(entryId: String)
}

/** Helpers named on an entry (§5.5). Basis: ML.A.801(d). */
@Dao
interface EntryHelperDao {
    @Insert suspend fun insert(helper: EntryHelperEntity)

    @Query("SELECT * FROM entry_helper WHERE entryId = :entryId")
    suspend fun forEntry(entryId: String): List<EntryHelperEntity>

    @Query("DELETE FROM entry_helper WHERE entryId = :entryId")
    suspend fun deleteForEntry(entryId: String)
}

/** Maintenance data used on an entry (§5.3), reference plus revision status. */
@Dao
interface DocumentationRefDao {
    @Insert suspend fun insert(ref: DocumentationRefEntity)

    @Query("SELECT * FROM documentation_ref WHERE entryId = :entryId")
    suspend fun forEntry(entryId: String): List<DocumentationRefEntity>

    @Query("DELETE FROM documentation_ref WHERE entryId = :entryId")
    suspend fun deleteForEntry(entryId: String)
}

/** Parts and materials fitted on an entry (§5.3). */
@Dao
interface PartUsedDao {
    @Insert suspend fun insert(part: PartUsedEntity)

    @Query("SELECT * FROM part_used WHERE entryId = :entryId")
    suspend fun forEntry(entryId: String): List<PartUsedEntity>

    @Query("DELETE FROM part_used WHERE entryId = :entryId")
    suspend fun deleteForEntry(entryId: String)
}

/**
 * A note in the engineer's record (§5.7) — never a statement about the
 * aircraft's airworthiness. No due dates, no reminders, no computed status.
 */
@Dao
interface DeferredItemDao {
    @Insert suspend fun insert(item: DeferredItemEntity)

    @Query("SELECT * FROM deferred_item WHERE closed = 0 ORDER BY id DESC")
    fun open(): Flow<List<DeferredItemEntity>>

    @Query("""
        UPDATE deferred_item
        SET closed = 1, closedByEntryId = :closedByEntryId, closedDate = :closedDate
        WHERE id = :id AND closed = 0
    """)
    suspend fun close(id: String, closedByEntryId: String, closedDate: LocalDate): Int
}

/**
 * Completions of catalogue tasks (§5.9), feeding RecencyDao's Route B queries.
 */
@Dao
interface TaskCompletionDao {
    @Insert suspend fun insert(completion: TaskCompletionEntity)

    @Query("SELECT * FROM task_completion WHERE entryId = :entryId")
    suspend fun forEntry(entryId: String): List<TaskCompletionEntity>

    @Query("DELETE FROM task_completion WHERE entryId = :entryId")
    suspend fun deleteForEntry(entryId: String)
}
