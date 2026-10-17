package io.opentrack.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.opentrack.app.BuildConfig
import io.opentrack.app.diag.DiagnosticsStore
import io.opentrack.app.search.ResultsEngine
import io.opentrack.app.search.ResultsViewModel

/**
 * Two views: SEARCH (fields, diagnostics, results) and DETAIL (train schedule/actual info).
 */
@Composable
fun ResultsScreen(
    viewModel: ResultsViewModel,
    diagnosticsStore: DiagnosticsStore? = null,
) {
    val ui by viewModel.state.collectAsState()
    val detail = ui.selectedTrainNumber

    Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        if (detail != null) {
            DetailView(ui = ui, viewModel = viewModel, diagnosticsStore = diagnosticsStore)
        } else {
            SearchView(ui = ui, viewModel = viewModel, diagnosticsStore = diagnosticsStore)
        }
    }
}

@Composable
private fun SearchView(
    ui: ResultsViewModel.UiState,
    viewModel: ResultsViewModel,
    diagnosticsStore: DiagnosticsStore?,
) {
    val context = LocalContext.current
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var trainNumber by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(true) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "OpenTrack India ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Search",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (ui.rows.isNotEmpty() || ui.note != null || from.isNotBlank() || to.isNotBlank() || trainNumber.isNotBlank()) {
                TextButton(onClick = { searchExpanded = !searchExpanded }) {
                    Text(if (searchExpanded) "hide" else "show")
                }
            }
        }

        if (searchExpanded) {
            OutlinedTextField(
                value = from,
                onValueChange = { from = it },
                label = { Text("from station code") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text("to station code") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.search(from.trim().uppercase(), to.trim().uppercase()) },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("search") }

            DiagnosticsBar(diagnosticsStore)

            OutlinedTextField(
                value = trainNumber,
                onValueChange = { trainNumber = it },
                label = { Text("or train number") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Button(onClick = { viewModel.searchByNumber(trainNumber.trim()) }) { Text("show train") }
        }

        LoaderRow(ui.loading)

        if (ui.rows.isNotEmpty() || (!ui.loading && ui.note != null)) {
            Text(
                text = "Results",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.padding(top = 8.dp).testTag("results_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ui.rows) { row ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = StateCopy.row(row.number, row.name, row.timeDue ?: "—", row.provenanceLabel),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Row {
                            TextButton(onClick = { viewModel.showStops(row.number) }) { Text("schedule") }
                            TextButton(onClick = { viewModel.showLiveStatus(row.number) }) { Text("live status") }
                        }
                    }
                }
            }
            if (ui.rows.isEmpty() && !ui.loading && ui.note != null) {
                item { Text(ui.note, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun DetailView(
    ui: ResultsViewModel.UiState,
    viewModel: ResultsViewModel,
    diagnosticsStore: DiagnosticsStore?,
) {
    var showSearchInDetail by rememberSaveable { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { viewModel.clearSelection() }) { Text("← search") }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = ui.trainLabel ?: "train",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showSearchInDetail = !showSearchInDetail }) {
                Text(if (showSearchInDetail) "hide search" else "search again")
            }
        }
        Spacer(modifier = Modifier.height(4.dp))

        if (showSearchInDetail) {
            val context = LocalContext.current
            var from by rememberSaveable { mutableStateOf("") }
            var to by rememberSaveable { mutableStateOf("") }
            var trainNumber by rememberSaveable { mutableStateOf("") }
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = from,
                        onValueChange = { from = it },
                        label = { Text("from station code") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = to,
                        onValueChange = { to = it },
                        label = { Text("to station code") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            viewModel.search(from.trim().uppercase(), to.trim().uppercase())
                            showSearchInDetail = false
                            viewModel.clearSelection()
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    ) { Text("search") }
                    OutlinedTextField(
                        value = trainNumber,
                        onValueChange = { trainNumber = it },
                        label = { Text("or train number") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Button(onClick = {
                        viewModel.searchByNumber(trainNumber.trim())
                        showSearchInDetail = false
                        viewModel.clearSelection()
                    }) { Text("show train") }
                }
            }
        }

        LoaderRow(ui.loading)

        val status = ui.status as? ResultsEngine.StatusOutcome.Live
        if (status != null) {
            val now = System.currentTimeMillis()
            val feedAgeMs = if (status.fetchedAtMillis > 0) now - status.fetchedAtMillis else -1L
            val ageLabel = if (feedAgeMs >= 0) {
                val mins = feedAgeMs / 60_000
                if (mins < 1) "just now" else "${mins} min ago"
            } else "stamp unavailable"
            val feedStamp = if (status.fetchedAtMillis > 0) {
                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date(status.fetchedAtMillis))
            } else "no stamp"

            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = StateCopy.sourceBadge(status.sourceLabel, provenanceLabel(status.sourceLabel, ageLabel)),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "fetched in ${status.responseTimeMillis / 1000.0}s",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(
                        text = "data as of $feedStamp · $ageLabel · position confidence: ${status.positionInfo?.confidence ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        status.sourceContributions.forEach { c ->
                            Text(
                                text = "${c.source} (${c.stopsCovered} stops${if (c.hasActuals) ", actuals" else ""})",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }

            val position = status.positionInfo
            if (position != null) {
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Current position",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (position.interpolated) "between stations" else "at station",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = if (position.minutesAgo != null) "${position.minutesAgo} min ago" else "—",
                                style = MaterialTheme.typography.labelSmall,
                                color = positionColor(position.confidence),
                            )
                        }
                        Text(
                            text = position.betweenStations ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        position.kmFromPrevious?.let { km ->
                            Text(
                                text = "~$km km from ${position.lastReportedStop ?: "—"}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Row {
                            sourceButton("refresh", null, ui.preferredSource, viewModel, ui.selectedTrainNumber, isRefresh = true)
                            sourceButton("NTES", "ntes", ui.preferredSource, viewModel, ui.selectedTrainNumber)
                            sourceButton("erail", "erail", ui.preferredSource, viewModel, ui.selectedTrainNumber)
                            sourceButton("WIMT", "wimt", ui.preferredSource, viewModel, ui.selectedTrainNumber)
                            sourceButton("Auto", null, ui.preferredSource, viewModel, ui.selectedTrainNumber)
                        }
                    }
                }
            }

            Text(
                text = "Timetable & actuals",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            ui.note?.let { note ->
                Text(text = note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                status.stops.forEach { stop ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${stop.code} ${stop.name}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            val timing = when {
                                stop.actualDeparture != null -> "dep ${stop.actualDeparture}"
                                stop.actualArrival != null -> "arr ${stop.actualArrival}"
                                stop.expectedDeparture != null -> "expected ${stop.expectedDeparture}"
                                else -> "not reported"
                            }
                            Text(
                                text = timing + (stop.arrivalDelayMinutes?.let { " · ${it} min late" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = if ((stop.arrivalDelayMinutes ?: 0) > 0)
                                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "sched ${stop.scheduledArrival ?: "—"}/${stop.scheduledDeparture ?: "—"}" +
                                    (stop.distanceKm?.let { " · ${it} km" } ?: "") +
                                    (stop.haltMinutes?.let { h -> if (h != "0") " · halt $h min" else "" } ?: "") +
                                    " · ${stop.sourceProvenance} (${stop.confidence})" +
                                    (if (stop.positionInterpolated) " · interpolated" else ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val marker = when {
                                stop.isDepartedHere -> "  ⬅ TRAIN LEFT HERE"
                                stop.isNextStation && stop.kmToNext != null -> "  ⬅ NEXT · ~${stop.kmToNext} km"
                                stop.isNextStation -> "  ⬅ NEXT STATION"
                                stop.isCurrentStation -> "  ⬅ HERE NOW"
                                else -> ""
                            }
                            if (marker.isNotEmpty()) {
                                Text(
                                    text = marker,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val timeText = buildString {
                                if (stop.actualArrival != null) append("arr ${stop.actualArrival}  ")
                                if (stop.actualDeparture != null) append("dep ${stop.actualDeparture}  ")
                                if (stop.expectedDeparture != null) append("exp ${stop.expectedDeparture}  ")
                            }
                            if (timeText.isNotEmpty()) {
                                Text(
                                    text = timeText.trimEnd(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Divider()
                }
            }
        }
        ui.note?.let { note ->
            Text(text = note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun provenanceLabel(sourceLabel: String, ageLabel: String): String {
    return "via $sourceLabel · $ageLabel"
}

private fun positionColor(confidence: String): androidx.compose.ui.graphics.Color {
    return when (confidence) {
        "high" -> MaterialTheme.colorScheme.primary
        "medium" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun LoaderRow(loading: Boolean) {
    if (!loading) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("fetching live data…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun DiagnosticsBar(store: io.opentrack.app.diag.DiagnosticsStore?) {
    if (store == null) return
    val context = androidx.compose.ui.platform.LocalContext.current
    var enabled by rememberSaveable { mutableStateOf(store.enabled) }
    var captureRaw by rememberSaveable { mutableStateOf(store.captureRaw) }
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("diagnostics", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    store.enabled = it
                },
                modifier = Modifier.testTag("diagnostics-toggle"),
            )
        }
        if (enabled) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("include raw responses", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = captureRaw,
                    onCheckedChange = {
                        captureRaw = it
                        store.captureRaw = it
                    },
                )
            }
            Row(modifier = Modifier.padding(top = 4.dp)) {
                TextButton(onClick = {
                    store.saveToLocal(context)?.let { uri ->
                        android.widget.Toast.makeText(context, "saved to $uri", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text("save to local") }
                TextButton(onClick = {
                    store.saveAndShare(context)?.let { context.startActivity(Intent.createChooser(it, "Share diagnostics file")) }
                }) { Text("save + share file") }
                TextButton(onClick = { context.startActivity(store.shareAsText(context)) }) { Text("share as copy") }
                TextButton(onClick = { store.clear() }) { Text("clear") }
            }
        }
    }
}

@Composable
private fun sourceButton(
    label: String,
    source: String?,
    currentPreferred: String?,
    viewModel: ResultsViewModel,
    trainNumber: String?,
    isRefresh: Boolean = false,
) {
    val selected = currentPreferred == source || (isRefresh && source == null)
    TextButton(
        onClick = {
            if (!isRefresh) viewModel.setPreferredSource(source)
            trainNumber?.let(viewModel::showLiveStatus)
        },
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun screenStateOf(name: ResultsEngine.UiStateName): ScreenState = when (name) {
    ResultsEngine.UiStateName.LIVE -> ScreenState.LIVE
    ResultsEngine.UiStateName.LIVE_STALE -> ScreenState.LIVE_STALE
    ResultsEngine.UiStateName.CACHED -> ScreenState.CACHED
    ResultsEngine.UiStateName.OFFLINE_UNVERIFIED -> ScreenState.OFFLINE_UNVERIFIED
    ResultsEngine.UiStateName.ANSWERED_EMPTY -> ScreenState.ANSWERED_EMPTY
    ResultsEngine.UiStateName.COULD_NOT_LOOK -> ScreenState.COULD_NOT_LOOK
    ResultsEngine.UiStateName.PARTIAL -> ScreenState.PARTIAL
    ResultsEngine.UiStateName.PROTOCOL_CHANGED -> ScreenState.PROTOCOL_CHANGED
}

internal val EM_DASH = "—"
