package nl.part66l.logbook.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 66.A.20(b)(2) recency. Today is fixed so window arithmetic (24-month lookback)
 * produces exact, checkable dates rather than relative assertions.
 */
class RecencyEvaluatorTest {

    private val evaluator = RecencyEvaluator()
    private val today = LocalDate.of(2026, 9, 4)
    private val windowStart = today.minusMonths(24) // 2024-09-04

    // -----------------------------------------------------------------
    // Route A — days
    // -----------------------------------------------------------------

    @Test
    fun `route A satisfied at exactly the 100-day threshold with correct lapse date`() {
        // 100 consecutive distinct days starting at windowStart: the oldest day
        // is the one that will roll out of the window first, so the lapse date
        // is that oldest day plus the 24-month window — here, today itself.
        val days = (0 until 100).map { offset ->
            RecencyEvaluator.ExperienceDay(windowStart.plusDays(offset.toLong()), Subcategory.L1)
        }
        val profile = RecencyEvaluator.Profile(subcategories = setOf(Subcategory.L1), reductionGranted = false)

        val result = evaluateSingle(profile, days = days)
        val routeA = result.routeResult(RecencyRoute.DAYS)

        assertTrue(routeA.satisfied)
        assertEquals(100, routeA.have)
        assertEquals(100, routeA.need)
        assertEquals(today, routeA.lapseDate)

        // One day short of the threshold: no longer satisfied, no lapse date.
        val short = evaluateSingle(profile, days = days.dropLast(1))
        val shortRouteA = short.routeResult(RecencyRoute.DAYS)
        assertFalse(shortRouteA.satisfied)
        assertEquals(99, shortRouteA.have)
        assertNull(shortRouteA.lapseDate)
    }

    @Test
    fun `route A satisfied at the reduced 50-day threshold with correct lapse date`() {
        val days = (0 until 50).map { offset ->
            RecencyEvaluator.ExperienceDay(windowStart.plusDays(offset.toLong()), Subcategory.L1)
        }
        val profile = RecencyEvaluator.Profile(
            subcategories = setOf(Subcategory.L1),
            reductionGranted = true,
            reductionReference = "CAA-2026-0042",
        )

        val result = evaluateSingle(profile, days = days)
        val routeA = result.routeResult(RecencyRoute.DAYS)

        assertTrue(routeA.satisfied)
        assertEquals(50, routeA.have)
        assertEquals(50, routeA.need)
        assertEquals(today, routeA.lapseDate)

        // 49 days does not meet the reduced threshold either.
        val short = evaluateSingle(profile, days = days.dropLast(1))
        val shortRouteA = short.routeResult(RecencyRoute.DAYS)
        assertFalse(shortRouteA.satisfied)
        assertEquals(49, shortRouteA.have)
        assertNull(shortRouteA.lapseDate)
    }

    // -----------------------------------------------------------------
    // Route B — 50% of tasks, coverage from each section
    // -----------------------------------------------------------------

    @Test
    fun `route B with one empty section is not satisfied even though the overall share is met`() {
        // Denominator: two sections of two tasks each: 50% of 4 is 2.
        val denominator = RecencyEvaluator.Denominator(
            taskIds = setOf("A1", "A2", "B1", "B2"),
            sectionCodes = setOf("A", "B"),
        )
        // Both completions are in section A: numerator (2) meets the threshold,
        // but section B has no completion in the window.
        val completionDate = today.minusMonths(6)
        val tasks = listOf(
            RecencyEvaluator.TaskEvent("A1", "A", completionDate, Subcategory.L1),
            RecencyEvaluator.TaskEvent("A2", "A", completionDate, Subcategory.L1),
        )
        val profile = RecencyEvaluator.Profile(subcategories = setOf(Subcategory.L1), reductionGranted = false)

        val result = evaluateSingle(
            profile,
            tasks = tasks,
            denominators = mapOf(Subcategory.L1 to denominator),
        )
        val routeB = result.routeResult(RecencyRoute.TASKS)

        assertEquals(2, routeB.have)
        assertEquals(2, routeB.need)
        assertEquals(listOf("B"), routeB.emptySections)
        assertFalse("50% of tasks is met, but an uncovered section must still fail the route", routeB.satisfied)
        assertFalse("the subcategory must not be current on an unsatisfied route", result.current)
    }

    // -----------------------------------------------------------------
    // Route C — annual inspections, proposed
    // -----------------------------------------------------------------

    @Test
    fun `route C never binds the verdict while its status is PROPOSED`() {
        val annuals = listOf(RecencyEvaluator.AnnualInspection(today.minusMonths(3), Subcategory.L1))
        val profile = RecencyEvaluator.Profile(subcategories = setOf(Subcategory.L1), reductionGranted = false)
        // A non-empty, wholly uncompleted denominator, so Route B genuinely fails
        // instead of being vacuously satisfied by "0 of 0 tasks required" — the
        // point of this test is Route C alone, isolated from the other routes.
        val denominator = RecencyEvaluator.Denominator(taskIds = setOf("X1"), sectionCodes = setOf("X"))

        val result = evaluateSingle(
            profile,
            annuals = annuals,
            denominators = mapOf(Subcategory.L1 to denominator),
            routeCStatus = RuleStatus.PROPOSED,
            routeCRequired = 1,
        )
        val routeC = result.routeResult(RecencyRoute.ANNUAL_INSPECTIONS)

        // The count itself meets the configured requirement...
        assertEquals(1, routeC.have)
        assertEquals(1, routeC.need)
        assertEquals(RuleStatus.PROPOSED, routeC.status)
        // ...but PROPOSED status must keep it from satisfying the route or the subcategory.
        assertFalse(routeC.satisfied)
        assertNull(routeC.lapseDate)
        assertFalse(result.current)
    }

    // -----------------------------------------------------------------

    private fun evaluateSingle(
        profile: RecencyEvaluator.Profile,
        days: List<RecencyEvaluator.ExperienceDay> = emptyList(),
        tasks: List<RecencyEvaluator.TaskEvent> = emptyList(),
        annuals: List<RecencyEvaluator.AnnualInspection> = emptyList(),
        denominators: Map<Subcategory, RecencyEvaluator.Denominator> = emptyMap(),
        routeCStatus: RuleStatus = RuleStatus.PROPOSED,
        routeCRequired: Int = 6,
    ): RecencyEvaluator.SubcategoryResult =
        evaluator.evaluate(today, profile, days, tasks, annuals, denominators, routeCStatus, routeCRequired).single()

    private fun RecencyEvaluator.SubcategoryResult.routeResult(route: RecencyRoute) =
        routes.first { it.route == route }
}
