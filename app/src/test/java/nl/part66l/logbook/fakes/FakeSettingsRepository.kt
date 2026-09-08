package nl.part66l.logbook.fakes

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import nl.part66l.logbook.data.SettingsRepository

class FakeSettingsRepository(
    initialShowArchivedAircraft: Boolean = false,
    initialCatalogueSectionOrder: List<String> = emptyList(),
    initialCollapsedCatalogueSections: Set<String> = emptySet(),
    initialCrsNumberTemplate: String = "{REG}-{YYYY}-{SEQ:4}",
    initialCrsNumberPrefix: String = "",
    initialCrsAnnualReset: Boolean = true,
    initialCrsStartAt: Int = 1,
) : SettingsRepository {
    private val showArchived = MutableStateFlow(initialShowArchivedAircraft)
    private val sectionOrder = MutableStateFlow(initialCatalogueSectionOrder)
    private val collapsedSections = MutableStateFlow(initialCollapsedCatalogueSections)
    private val crsNumberTemplateFlow = MutableStateFlow(initialCrsNumberTemplate)
    private val crsNumberPrefixFlow = MutableStateFlow(initialCrsNumberPrefix)
    private val crsAnnualResetFlow = MutableStateFlow(initialCrsAnnualReset)
    private val crsStartAtFlow = MutableStateFlow(initialCrsStartAt)

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

    override val crsNumberTemplate: Flow<String> = crsNumberTemplateFlow

    override suspend fun setCrsNumberTemplate(value: String) {
        crsNumberTemplateFlow.value = value
    }

    override val crsNumberPrefix: Flow<String> = crsNumberPrefixFlow

    override suspend fun setCrsNumberPrefix(value: String) {
        crsNumberPrefixFlow.value = value
    }

    override val crsAnnualReset: Flow<Boolean> = crsAnnualResetFlow

    override suspend fun setCrsAnnualReset(value: Boolean) {
        crsAnnualResetFlow.value = value
    }

    override val crsStartAt: Flow<Int> = crsStartAtFlow

    override suspend fun setCrsStartAt(value: Int) {
        crsStartAtFlow.value = value
    }
}
