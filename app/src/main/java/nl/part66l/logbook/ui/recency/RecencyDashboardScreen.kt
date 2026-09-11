package nl.part66l.logbook.ui.recency

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import nl.part66l.logbook.R
import nl.part66l.logbook.domain.ExpiryWarning
import nl.part66l.logbook.domain.RecencyEvaluator
import nl.part66l.logbook.domain.RuleStatus
import nl.part66l.logbook.domain.WarningLevel
import nl.part66l.logbook.ui.theme.onColor
import nl.part66l.logbook.ui.theme.onConfirmGreen
import nl.part66l.logbook.ui.theme.confirmGreen
import nl.part66l.logbook.ui.theme.color
import nl.part66l.logbook.ui.theme.part66TopAppBarColors

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecencyDashboardScreen(
    onEvaluated: () -> Unit = {},
    viewModel: RecencyDashboardViewModel = hiltViewModel(),
) {
    val results by viewModel.results.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val collapsedSubcategories by viewModel.collapsedSubcategories.collectAsStateWithLifecycle()
    val thresholds by viewModel.warningThresholds.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val today = LocalDate.now()

    LaunchedEffect(Unit) {
        viewModel.refresh()
        // Recency can have moved since startup (work logged, a threshold changed), so the tab
        // icon is recomputed alongside the dashboard rather than only once at launch.
        onEvaluated()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recency") },
                actions = {
                    IconButton(
                        onClick = { viewModel.exportEvidence { csv -> shareRecencyCsv(context, csv) } },
                        enabled = !loading && !exporting && results.isNotEmpty(),
                    ) {
                        if (exporting) {
                            Text("…", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        } else {
                            Icon(
                                painterResource(R.drawable.ic_download),
                                contentDescription = "Export the evidence as CSV",
                                tint = Color.White,
                            )
                        }
                    }
                },
                colors = part66TopAppBarColors(),
                expandedHeight = 48.dp,
            )
        },
    ) { padding ->
        when {
            loading -> Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            results.isEmpty() -> Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No subcategories held yet — set them up in Profile.")
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(results, key = { it.subcategory.name }) { result ->
                    SubcategoryCard(
                        result = result,
                        warning = ExpiryWarning.forRecency(result.current, result.lapseDate, today, thresholds),
                        routesVisible = result.subcategory.name !in collapsedSubcategories,
                        onRoutesVisibilityToggled = { visible ->
                            viewModel.onRoutesVisibilityToggled(result.subcategory.name, collapsed = !visible)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SubcategoryCard(
    result: RecencyEvaluator.SubcategoryResult,
    warning: WarningLevel,
    routesVisible: Boolean,
    onRoutesVisibilityToggled: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(result.subcategory.name, style = MaterialTheme.typography.titleLarge)
                StatusBadge(current = result.current, warning = warning)
            }
            result.lapseDate?.let {
                Text(
                    "Current until ${it.format(DATE_FORMAT)} if nothing further is logged.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = warning.color() ?: MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onRoutesVisibilityToggled(!routesVisible) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    if (routesVisible) "▼ Hide routes" else "▶ Show routes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            if (routesVisible) {
                result.routes.forEachIndexed { index, route ->
                    RouteRow(index + 1, route)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(current: Boolean, warning: WarningLevel) {
    // Amber says "current, but not for much longer" — still green would hide that, red would
    // overstate it, since nothing has actually lapsed yet. forRecency already returns RED
    // whenever `current` is false, so the fill needs no separate branch for it; `current` decides
    // the wording only.
    val background = warning.color() ?: confirmGreen()
    val content = warning.onColor() ?: onConfirmGreen()
    Surface(color = background, shape = MaterialTheme.shapes.small) {
        Text(
            if (current) "Current" else "Not current",
            color = content,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun RouteRow(number: Int, route: RecencyEvaluator.RouteResult) {
    val proposed = route.status == RuleStatus.PROPOSED
    val labelColor = if (proposed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface

    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$number. ${route.route.displayLabel}", style = MaterialTheme.typography.labelLarge, color = labelColor)
            when {
                proposed -> Text("Proposed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                route.satisfied -> Text("✓", color = confirmGreen(), style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(route.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
