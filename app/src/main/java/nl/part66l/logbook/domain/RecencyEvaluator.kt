package nl.part66l.logbook.domain

import java.time.LocalDate
import kotlin.math.ceil

/**
 * Evaluates 66.A.20(b)(2) recency per held subcategory.
 *
 * A subcategory is current if **any** route is satisfied. Routes are evaluated
 * independently and all are reported, so the user can see which one they are
 * living on and how close the others are.
 *
 * The evaluator never gates certification. §7.4: the app shows status, it does not
 * decide privileges — not least because it cannot see work logged on paper or
 * before install.
 */
class RecencyEvaluator(
    private val windowMonths: Long = 24,
) {

    // ---------------------------------------------------------------------
    // Inputs
    // ---------------------------------------------------------------------

    data class Profile(
        val subcategories: Set<Subcategory>,
        /**
         * 50% reduction of the day requirement, permitted by AMC 66.A.20(b)(2)
         * only when agreed in advance by the competent authority.
         */
        val reductionGranted: Boolean,
        val reductionReference: String? = null,
    )

    /** One day on which experience was gained, already resolved to a subcategory. */
    data class ExperienceDay(
        val date: LocalDate,
        val subcategory: Subcategory,
    )

    data class TaskEvent(
        val taskId: String,
        val sectionCode: String,
        val date: LocalDate,
        val subcategory: Subcategory,
        val isSubstituteTask: Boolean = false,
    )

    data class AnnualInspection(val date: LocalDate, val subcategory: Subcategory)

    /** Applicable task ids and their sections, already filtered for the subcategory. */
    data class Denominator(val taskIds: Set<String>, val sectionCodes: Set<String>)

    // ---------------------------------------------------------------------
    // Outputs
    // ---------------------------------------------------------------------

    data class RouteResult(
        val route: RecencyRoute,
        val status: RuleStatus,
        val satisfied: Boolean,
        val have: Int,
        val need: Int,
        /** Last date on which this route remains satisfied if nothing further is logged. */
        val lapseDate: LocalDate?,
        val detail: String,
        /** Sections with no completion in the window. Route B only. */
        val emptySections: List<String> = emptyList(),
    )

    data class SubcategoryResult(
        val subcategory: Subcategory,
        val current: Boolean,
        val routes: List<RouteResult>,
    ) {
        /** The route the user is actually relying on, if any. */
        val satisfiedBy: RouteResult? get() = routes.firstOrNull { it.satisfied }

        /** Latest date the subcategory stays current across all satisfied routes. */
        val lapseDate: LocalDate?
            get() = routes.filter { it.satisfied }.mapNotNull { it.lapseDate }.maxOrNull()
    }

    // ---------------------------------------------------------------------

    fun evaluate(
        today: LocalDate,
        profile: Profile,
        days: List<ExperienceDay>,
        tasks: List<TaskEvent>,
        annuals: List<AnnualInspection>,
        denominators: Map<Subcategory, Denominator>,
        routeCStatus: RuleStatus = RuleStatus.PROPOSED,
        routeCRequired: Int = 6,
    ): List<SubcategoryResult> {
        val windowStart = today.minusMonths(windowMonths)

        return profile.subcategories.sorted().map { sub ->
            val routes = listOf(
                routeA(today, windowStart, profile, days.filter { it.subcategory == sub }),
                routeB(
                    today, windowStart,
                    tasks.filter { it.subcategory == sub },
                    denominators[sub] ?: Denominator(emptySet(), emptySet()),
                ),
                routeC(today, windowStart, annuals.filter { it.subcategory == sub }, routeCStatus, routeCRequired),
            )
            SubcategoryResult(
                subcategory = sub,
                current = routes.any { it.satisfied },
                routes = routes,
            )
        }
    }

    // ---------------------------------------------------------------------
    // Route A — days
    // ---------------------------------------------------------------------

    /**
     * AMC 66.A.20(b)(2) allows the six-month period to be replaced by 100 days of
     * maintenance experience, reduced to 50 where the competent authority has
     * agreed in advance. Every logged day counts toward that threshold.
     *
     * The same AMC permits up to 20% of the duration to be replaced by training,
     * technical support/engineering or maintenance management/planning. That is
     * **not** modelled: it would put a flag on every entry to serve a case most
     * independent certifying staff never claim, and anyone who does claim it has
     * already agreed it with their authority. It is explained in the help text.
     */
    private fun routeA(
        today: LocalDate,
        windowStart: LocalDate,
        profile: Profile,
        days: List<ExperienceDay>,
    ): RouteResult {
        val required = if (profile.reductionGranted) DAYS_REDUCED else DAYS_FULL

        val dates = days.filter { it.date >= windowStart }.map { it.date }.distinct().sorted()
        val have = dates.size
        val satisfied = have >= required

        val detail = buildString {
            append("$have days of maintenance experience; threshold $required")
            if (profile.reductionGranted) {
                append(" — 50% reduction applied")
                profile.reductionReference?.let { append(", ref. $it") }
            }
            append(".")
        }

        return RouteResult(
            route = RecencyRoute.DAYS,
            status = RuleStatus.IN_FORCE,
            satisfied = satisfied,
            have = have,
            need = required,
            lapseDate = if (satisfied) lapseDate(dates, required) else null,
            detail = detail,
        )
    }

    // ---------------------------------------------------------------------
    // Route B — tasks
    // ---------------------------------------------------------------------

    /**
     * AMC 66.A.45(h): 50% of the Appendix II tasks relevant to the licence category
     * and applicable ratings, **and** coverage of tasks from each paragraph.
     *
     * Both conditions bind. A user at 26 of 25 with an empty section is not
     * compliant, and is told which section is empty rather than shown a green
     * percentage.
     */
    private fun routeB(
        today: LocalDate,
        windowStart: LocalDate,
        events: List<TaskEvent>,
        denominator: Denominator,
    ): RouteResult {
        val required = ceil(denominator.taskIds.size * TASK_SHARE).toInt()

        // Latest completion per task, so a task done twice doesn't drop early.
        val latestPerTask = events
            .filter { it.date >= windowStart && (it.isSubstituteTask || it.taskId in denominator.taskIds) }
            .groupBy { it.taskId }
            .mapValues { (_, e) -> e.maxOf { it.date } }

        val have = latestPerTask.size

        val coveredSections = events
            .filter { it.date >= windowStart }
            .map { it.sectionCode }
            .toSet()
        val emptySections = (denominator.sectionCodes - coveredSections).sorted()

        val satisfied = have >= required && emptySections.isEmpty()

        // Overall lapse, then bounded by the first section to fall empty.
        val overall = lapseDate(latestPerTask.values.sorted(), required)
        val perSection = events
            .filter { it.date >= windowStart }
            .groupBy { it.sectionCode }
            .mapNotNull { (_, e) -> e.maxOf { it.date }.plusMonths(windowMonths) }
        val bounded = (listOfNotNull(overall) + perSection).minOrNull()

        return RouteResult(
            route = RecencyRoute.TASKS,
            status = RuleStatus.IN_FORCE,
            satisfied = satisfied,
            have = have,
            need = required,
            lapseDate = if (satisfied) bounded else null,
            detail = "$have of ${denominator.taskIds.size} applicable tasks in the window; " +
                "threshold $required" +
                if (emptySections.isEmpty()) ", all sections covered."
                else ", sections with no completion: ${emptySections.joinToString(", ")}.",
            emptySections = emptySections,
        )
    }

    // ---------------------------------------------------------------------
    // Route C — annual inspections
    // ---------------------------------------------------------------------

    /**
     * Proposed AMC2 66.A.20(b)(2) from NPA 2025-12. Reported but not contributing
     * to the verdict while [status] is PROPOSED. Flipping the catalogue field on
     * publication of the ED Decision activates it over entries already logged.
     */
    private fun routeC(
        today: LocalDate,
        windowStart: LocalDate,
        annuals: List<AnnualInspection>,
        status: RuleStatus,
        required: Int,
    ): RouteResult {
        val dates = annuals.filter { it.date >= windowStart }.map { it.date }.sorted()
        val meets = dates.size >= required
        val counts = status == RuleStatus.IN_FORCE

        return RouteResult(
            route = RecencyRoute.ANNUAL_INSPECTIONS,
            status = status,
            satisfied = meets && counts,
            have = dates.size,
            need = required,
            lapseDate = if (meets && counts) lapseDate(dates, required) else null,
            detail = if (counts) "${dates.size} of $required annual inspections in the window."
                     else "${dates.size} of $required — proposed under NPA 2025-12, not yet applicable.",
        )
    }

    // ---------------------------------------------------------------------

    /**
     * The date on which the count first falls below the threshold as the trailing
     * window slides forward. With n qualifying dates ascending and a threshold of
     * r, compliance ends when the (n − r)th date leaves the window.
     *
     * This is the headline figure: "compliant until 12 April 2027 if you log
     * nothing further."
     */
    private fun lapseDate(ascending: List<LocalDate>, required: Int): LocalDate? {
        if (required <= 0) return null
        if (ascending.size < required) return null
        return ascending[ascending.size - required].plusMonths(windowMonths)
    }

    companion object {
        const val DAYS_FULL = 100
        const val DAYS_REDUCED = 50
        const val TASK_SHARE = 0.50
    }
}

/**
 * Which subcategory's privileges are exercised on a given aircraft. Derived from
 * propulsion and structure, per the similarity test in AMC 66.A.20(b)(2).
 *
 * MIXED construction is genuinely ambiguous and is not guessed: the user is asked
 * once per aircraft and the answer is stored.
 */
object SubcategoryResolver {

    fun resolve(propulsion: Propulsion, structure: Structure): Subcategory? {
        val powered = propulsion != Propulsion.UNPOWERED
        return when (structure) {
            Structure.COMPOSITE -> if (powered) Subcategory.L2C else Subcategory.L1C
            Structure.WOOD_AND_FABRIC,
            Structure.METAL -> if (powered) Subcategory.L2 else Subcategory.L1
            Structure.MIXED -> null
        }
    }
}
