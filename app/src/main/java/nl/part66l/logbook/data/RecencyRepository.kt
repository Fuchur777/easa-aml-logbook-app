package nl.part66l.logbook.data

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import nl.part66l.logbook.domain.RecencyEvaluator
import nl.part66l.logbook.domain.RecencyRoute
import nl.part66l.logbook.domain.Subcategory
import nl.part66l.logbook.domain.SubcategoryResolver

/** One contributing record behind a recency figure — the "download the evidence" export's own row. */
data class RecencyEvidenceRow(
    val subcategory: Subcategory,
    val route: RecencyRoute,
    val date: LocalDate,
    val aircraftRegistration: String?,
    val detail: String,
)

interface RecencyRepository {
    suspend fun evaluate(
        today: LocalDate,
        catalogueVersion: String,
        windowMonths: Long = 24,
    ): List<RecencyEvaluator.SubcategoryResult>

    /** What the dashboard calls — resolves the currently-seeded catalogue version automatically. */
    suspend fun evaluateCurrent(
        today: LocalDate,
        windowMonths: Long = 24,
    ): List<RecencyEvaluator.SubcategoryResult>

    /**
     * Every individual record behind [evaluateCurrent]'s numbers — one row per (record,
     * subcategory it credits), same resolution rules as the evaluation itself (bench work
     * credits every held subcategory; an unresolved MIXED aircraft credits none). What the
     * Recency screen's download button exports, so a reader can check the app's "current
     * until" claim against the actual underlying entries rather than taking it on faith.
     */
    suspend fun evidenceForExport(today: LocalDate, windowMonths: Long = 24): List<RecencyEvidenceRow>
}

/**
 * Wires the stored profile, work sessions, task completions and catalogue into
 * [RecencyEvaluator] — the missing link between raw rows and the pure recency
 * logic, which knows nothing about Room.
 *
 * Aircraft-less (bench/component) work has no aircraft to run the §7.1 similarity
 * test against. By decision, it credits every subcategory the profile holds,
 * rather than none — the same rule is applied, for consistency, to an
 * aircraft-less annual-flagged entry feeding Route C, though that combination
 * should not arise in practice: an annual inspection is inherently aircraft-specific.
 *
 * An aircraft whose subcategory cannot be resolved (MIXED construction with no
 * override yet recorded) contributes to none — silently skipped rather than
 * guessed, matching [SubcategoryResolver]'s own refusal to guess.
 */
@Singleton
class RecencyRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val aircraftDao: AircraftDao,
    private val recencyDao: RecencyDao,
    private val catalogueDao: CatalogueDao,
    private val evaluator: RecencyEvaluator = RecencyEvaluator(),
) : RecencyRepository {

    override suspend fun evaluateCurrent(today: LocalDate, windowMonths: Long): List<RecencyEvaluator.SubcategoryResult> {
        val version = catalogueDao.currentVersion() ?: return emptyList()
        return evaluate(today, version, windowMonths)
    }

    override suspend fun evaluate(
        today: LocalDate,
        catalogueVersion: String,
        windowMonths: Long,
    ): List<RecencyEvaluator.SubcategoryResult> {
        val profileEntity = profileDao.get() ?: return emptyList()
        val heldSubcategories = buildSet {
            if (profileEntity.holdsL1) add(Subcategory.L1)
            if (profileEntity.holdsL1C) add(Subcategory.L1C)
            if (profileEntity.holdsL2) add(Subcategory.L2)
            if (profileEntity.holdsL2C) add(Subcategory.L2C)
        }
        if (heldSubcategories.isEmpty()) return emptyList()

        val profile = RecencyEvaluator.Profile(
            subcategories = heldSubcategories,
            reductionGranted = profileEntity.recencyReductionGranted,
            reductionReference = profileEntity.recencyReductionReference,
            initialCertificationDate = profileEntity.initialCertificationDate,
        )

        val windowStart = today.minusMonths(windowMonths)
        val aircraftSubcategory = aircraftDao.observeAll(includeArchived = true).first().associate { aircraft ->
            aircraft.id to (aircraft.subcategoryOverride
                ?: SubcategoryResolver.resolve(aircraft.propulsion, aircraft.structure))
        }

        val days = recencyDao.sessionsInWindow(windowStart, profileEntity.researchCountsTowardRecency).flatMap { row ->
            subcategoriesFor(row.aircraftId, aircraftSubcategory, heldSubcategories)
                .map { sub -> RecencyEvaluator.ExperienceDay(row.date, sub) }
        }

        val annuals = recencyDao.annualSessionsInWindow(windowStart).flatMap { row ->
            subcategoriesFor(row.aircraftId, aircraftSubcategory, heldSubcategories)
                .map { sub -> RecencyEvaluator.AnnualInspection(row.date, sub) }
        }

        val tasks = recencyDao.taskCompletionsInWindow(windowStart).flatMap { row ->
            heldSubcategories.filter { row.appliesTo(it) }.map { sub ->
                RecencyEvaluator.TaskEvent(
                    taskId = row.taskId,
                    sectionCode = row.sectionCode,
                    date = row.date,
                    subcategory = sub,
                    isSubstituteTask = row.substituteText != null,
                )
            }
        }

        val denominators = heldSubcategories.associateWith { sub ->
            val applicable = catalogueDao.applicableTasks(
                version = catalogueVersion,
                l1 = sub == Subcategory.L1,
                l1c = sub == Subcategory.L1C,
                l2 = sub == Subcategory.L2,
                l2c = sub == Subcategory.L2C,
            )
            RecencyEvaluator.Denominator(
                taskIds = applicable.map { it.id }.toSet(),
                sectionCodes = applicable.map { it.sectionCode }.toSet(),
            )
        }

        return evaluator.evaluate(
            today = today,
            profile = profile,
            days = days,
            tasks = tasks,
            annuals = annuals,
            denominators = denominators,
        )
    }

    override suspend fun evidenceForExport(today: LocalDate, windowMonths: Long): List<RecencyEvidenceRow> {
        val profileEntity = profileDao.get() ?: return emptyList()
        val heldSubcategories = buildSet {
            if (profileEntity.holdsL1) add(Subcategory.L1)
            if (profileEntity.holdsL1C) add(Subcategory.L1C)
            if (profileEntity.holdsL2) add(Subcategory.L2)
            if (profileEntity.holdsL2C) add(Subcategory.L2C)
        }
        if (heldSubcategories.isEmpty()) return emptyList()

        val windowStart = today.minusMonths(windowMonths)
        val aircraft = aircraftDao.observeAll(includeArchived = true).first()
        val aircraftSubcategory = aircraft.associate { a ->
            a.id to (a.subcategoryOverride ?: SubcategoryResolver.resolve(a.propulsion, a.structure))
        }
        // Today's registration is good enough for a reader cross-checking current records —
        // this isn't the certified "registration at the time" the CRS itself prints.
        val registrationByAircraftId = aircraft.associate { a -> a.id to aircraftDao.registrationOn(a.id, today) }

        val rows = mutableListOf<RecencyEvidenceRow>()

        recencyDao.sessionsForExport(windowStart, profileEntity.researchCountsTowardRecency).forEach { row ->
            subcategoriesFor(row.aircraftId, aircraftSubcategory, heldSubcategories).forEach { sub ->
                rows += RecencyEvidenceRow(
                    subcategory = sub,
                    route = RecencyRoute.DAYS,
                    date = row.date,
                    aircraftRegistration = row.aircraftId?.let { registrationByAircraftId[it] },
                    detail = row.description,
                )
            }
        }

        recencyDao.annualSessionsForExport(windowStart).forEach { row ->
            subcategoriesFor(row.aircraftId, aircraftSubcategory, heldSubcategories).forEach { sub ->
                rows += RecencyEvidenceRow(
                    subcategory = sub,
                    route = RecencyRoute.ANNUAL_INSPECTIONS,
                    date = row.date,
                    aircraftRegistration = row.aircraftId?.let { registrationByAircraftId[it] },
                    detail = row.description,
                )
            }
        }

        recencyDao.taskCompletionsForExport(windowStart).forEach { row ->
            heldSubcategories.filter { row.appliesTo(it) }.forEach { sub ->
                rows += RecencyEvidenceRow(
                    subcategory = sub,
                    route = RecencyRoute.TASKS,
                    date = row.date,
                    aircraftRegistration = row.aircraftId?.let { registrationByAircraftId[it] },
                    detail = row.substituteText ?: row.taskTextSnapshot,
                )
            }
        }

        return rows.sortedWith(compareBy({ it.subcategory }, { it.route }, { it.date }))
    }

    private fun subcategoriesFor(
        aircraftId: String?,
        aircraftSubcategory: Map<String, Subcategory?>,
        heldSubcategories: Set<Subcategory>,
    ): List<Subcategory> = when {
        aircraftId == null -> heldSubcategories.toList()
        else -> listOfNotNull(aircraftSubcategory[aircraftId]).filter { it in heldSubcategories }
    }

    private fun TaskCompletionRow.appliesTo(sub: Subcategory) = when (sub) {
        Subcategory.L1 -> appliesToL1
        Subcategory.L1C -> appliesToL1C
        Subcategory.L2 -> appliesToL2
        Subcategory.L2C -> appliesToL2C
    }

    private fun TaskCompletionDetailRow.appliesTo(sub: Subcategory) = when (sub) {
        Subcategory.L1 -> appliesToL1
        Subcategory.L1C -> appliesToL1C
        Subcategory.L2 -> appliesToL2
        Subcategory.L2C -> appliesToL2C
    }
}
