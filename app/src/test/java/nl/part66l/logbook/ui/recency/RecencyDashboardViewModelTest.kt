package nl.part66l.logbook.ui.recency

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric, not a plain JVM test, only because [RecencyDashboardViewModel.exportEvidence]
 * needs a real `context.filesDir` to write into — everything else here could run without it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecencyDashboardViewModelTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

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
        val viewModel = RecencyDashboardViewModel(context, FakeRecencyRepository(current = listOf(result)))

        assertTrue(viewModel.results.value.isEmpty())

        viewModel.refresh()

        assertEquals(listOf(result), viewModel.results.value)
        assertFalse(viewModel.loading.value)
    }

    @Test
    fun `an empty evaluation (no subcategories held) is reflected as an empty list`() {
        val viewModel = RecencyDashboardViewModel(context, FakeRecencyRepository(current = emptyList()))

        viewModel.refresh()

        assertTrue(viewModel.results.value.isEmpty())
        assertFalse(viewModel.loading.value)
    }

    // exportEvidence's actual CSV-writing is covered end-to-end, deterministically, by
    // RecencyExportTest — it runs on Dispatchers.IO internally, which Dispatchers.setMain
    // doesn't swap, so asserting on it right after a fire-and-forget viewModelScope.launch
    // here would race the real background dispatch instead of reliably waiting for it.
}
