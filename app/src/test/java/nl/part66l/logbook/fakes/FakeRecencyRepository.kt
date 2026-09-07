package nl.part66l.logbook.fakes

import java.time.LocalDate
import nl.part66l.logbook.data.RecencyRepository
import nl.part66l.logbook.domain.RecencyEvaluator

class FakeRecencyRepository(
    private val current: List<RecencyEvaluator.SubcategoryResult> = emptyList(),
) : RecencyRepository {

    override suspend fun evaluate(
        today: LocalDate,
        catalogueVersion: String,
        windowMonths: Long,
    ): List<RecencyEvaluator.SubcategoryResult> = current

    override suspend fun evaluateCurrent(today: LocalDate, windowMonths: Long): List<RecencyEvaluator.SubcategoryResult> = current
}
