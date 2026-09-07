package nl.part66l.logbook.ui.recency

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import nl.part66l.logbook.domain.RecencyEvaluator
import nl.part66l.logbook.domain.Subcategory
import nl.part66l.logbook.fakes.FakeRecencyRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecencyDashboardViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `starts empty and loading, until refresh loads evaluateCurrent's results`() {
        val result = RecencyEvaluator.SubcategoryResult(subcategory = Subcategory.L1, current = true, routes = emptyList())
        val viewModel = RecencyDashboardViewModel(FakeRecencyRepository(current = listOf(result)))

        assertTrue(viewModel.results.value.isEmpty())

        viewModel.refresh()

        assertEquals(listOf(result), viewModel.results.value)
        assertFalse(viewModel.loading.value)
    }

    @Test
    fun `an empty evaluation (no subcategories held) is reflected as an empty list`() {
        val viewModel = RecencyDashboardViewModel(FakeRecencyRepository(current = emptyList()))

        viewModel.refresh()

        assertTrue(viewModel.results.value.isEmpty())
        assertFalse(viewModel.loading.value)
    }
}
