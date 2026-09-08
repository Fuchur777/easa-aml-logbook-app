package nl.part66l.logbook.fakes

import nl.part66l.logbook.data.CatalogueRepository
import nl.part66l.logbook.data.CatalogueTaskEntity

class FakeCatalogueRepository(
    private val applicableTasks: List<CatalogueTaskEntity> = emptyList(),
) : CatalogueRepository {
    override suspend fun applicableTasksForProfile(): List<CatalogueTaskEntity> = applicableTasks
}
