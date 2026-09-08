package nl.part66l.logbook.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import nl.part66l.logbook.data.SettingsRepository

class FakeSettingsRepository(
    initialShowArchivedAircraft: Boolean = false,
    initialCatalogueSectionOrder: List<String> = emptyList(),
    initialCollapsedCatalogueSections: Set<String> = emptySet(),
) : SettingsRepository {
    private val showArchived = MutableStateFlow(initialShowArchivedAircraft)
    private val sectionOrder = MutableStateFlow(initialCatalogueSectionOrder)
    private val collapsedSections = MutableStateFlow(initialCollapsedCatalogueSections)

    override val showArchivedAircraft: Flow<Boolean> = showArchived

    override suspend fun setShowArchivedAircraft(value: Boolean) {
        showArchived.value = value
    }

    override val catalogueSectionOrder: Flow<List<String>> = sectionOrder

    override suspend fun setCatalogueSectionOrder(order: List<String>) {
        sectionOrder.value = order
    }

    override val collapsedCatalogueSections: Flow<Set<String>> = collapsedSections

    override suspend fun setCatalogueSectionCollapsed(section: String, collapsed: Boolean) {
        collapsedSections.value = if (collapsed) collapsedSections.value + section else collapsedSections.value - section
    }
}
