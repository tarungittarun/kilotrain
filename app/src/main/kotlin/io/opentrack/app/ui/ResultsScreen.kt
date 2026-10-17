package io.opentrack.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.opentrack.app.BuildConfig
import io.opentrack.app.diag.DiagnosticsStore
import io.opentrack.app.search.ResultsEngine
import io.opentrack.app.search.ResultsViewModel
import io.opentrack.core.contract.Provenance
import io.opentrack.core.contract.ProvenanceKind
import io.opentrack.core.contract.label

/**
 * Two views, as the owner asked: the SEARCH view (fields, diagnostics, results) and the DETAIL
 * view (one train's schedule/actual info). Opening a train hides the search; a back button
 * returns to it. All state copy comes from StateCopy only (3.2).
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // header: back to search + the train
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
        Text(
            text = "OpenTrack India ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.labelSmall,
        )

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
            val provenance = Provenance(
                if (status.viaCache) ProvenanceKind.CACHED else ProvenanceKind.LIVE,
                status.sourceLabel, status.fetchedAtMillis,
            )
            Text(
                text = StateCopy.sourceBadge(status.sourceLabel, provenance.label(System.currentTimeMillis())),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp),
            )

            // Data freshness + response time - highlighted prominently (3.1: never claim freshness
            // the source didn't publish; show the feed's own stamp + fetch elapsed)
            val now = System.currentTimeMillis()
            val feedAgeMs = if (status.fetchedAtMillis > 0) now - status.fetchedAtMillis else -1L
            val ageLabel = if (feedAgeMs >= 0) {
                val mins = feedAgeMs / 60_000
                if (mins < 1) "just now" else "${mins} min${if (mins > 1) "s" else ""} ago"
            } else "stamp unavailable"
            Text(
                text = "data as of ${ageLabel} · fetched in ${status.responseTimeMillis}ms",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )

            // Last reported: the last stop the winning source reported an actual for,
            // or the current station NTES names
            val departed = status.stops.firstOrNull { it.isDepartedHere }
            if (departed != null) {
                Text(
                    text = "Last reported: ${departed.code} ${departed.name} · ${departed.actualDeparture ?: departed.actualArrival ?: "—"}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else if (status.currentStationName != null) {
                Text(
                    text = "Position: ${status.currentStationName} (per NTES)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // Source selection: user picks which source answers first
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Text("source:", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(32.dp))
                sourceButton("Auto", null, ui.preferredSource, viewModel, ui.selectedTrainNumber)
                sourceButton("NTES", "ntes", ui.preferredSource, viewModel, ui.selectedTrainNumber)
                sourceButton("RailYatri", "railyatri", ui.preferredSource, viewModel, ui.selectedTrainNumber)
                sourceButton("Ixigo", "ixigo", ui.preferredSource, viewModel, ui.selectedTrainNumber)
                sourceButton("ConfirmTkt", "confirmtkt", ui.preferredSource, viewModel, ui.selectedTrainNumber)
                sourceButton("erail", "erail", ui.preferredSource, viewModel, ui.selectedTrainNumber)
            }
        }
        ui.note?.let { note ->
            Text(text = note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }

        LazyColumn(modifier = Modifier.padding(top = 8.dp).testTag("results_list")) {
            if (status != null) {
                // the position card: one glance answer
                item {
                    val departed = status.stops.firstOrNull { it.isDepartedHere }
                    val next = status.stops.firstOrNull { it.isNextStation }
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (status.currentStationName != null) {
                                Text(
                                    text = "At ${status.currentStationName}",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                            Text(
                                text = StateCopy.positionSummary(
                                    departedStation = departed?.let { "${it.code} ${it.name}" },
                                    nextStation = next?.let { "${it.code} ${it.name}" },
                                    expectedNext = next?.expectedDeparture,
                                    delayMinutes = next?.arrivalDelayMinutes,
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = "as of ${status.currentStatus}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Row {
                                sourceButton("refresh", null, ui.preferredSource, viewModel, ui.selectedTrainNumber, isRefresh = true)
                                sourceButton("NTES", "ntes", ui.preferredSource, viewModel, ui.selectedTrainNumber)
                                sourceButton("Schedule", "erail", ui.preferredSource, viewModel, ui.selectedTrainNumber)
                                sourceButton("Auto", null, ui.preferredSource, viewModel, ui.selectedTrainNumber)
                            }
                        }
                    }
                }
            }
            if (ui.stops.isEmpty() && ui.stopsNote != null) {
                item {
                    Text(
                        text = ui.stopsNote,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            // per-stop rows: actual vs scheduled, delay, platform, position markers
            if (status != null) {
                items(status.stops) { stop ->
                    Text(
                        text = StateCopy.stopStatus(
                            code = stop.code,
                            name = stop.name,
                            scheduledArrival = stop.scheduledArrival,
                            scheduledDeparture = stop.scheduledDeparture,
                            actualArrival = stop.actualArrival,
                            actualDeparture = stop.actualDeparture,
                            arrivalDelayMinutes = stop.arrivalDelayMinutes,
                            departureDelayMinutes = stop.departureDelayMinutes,
                            isCurrentStation = stop.isCurrentStation,
                            isTrainHere = stop.isTrainHere,
                            expectedDeparture = stop.expectedDeparture,
                            distanceKm = stop.distanceKm,
                            haltMinutes = stop.haltMinutes,
                            isDepartedHere = stop.isDepartedHere,
                            isNextStation = stop.isNextStation,
                            kmToNext = stop.kmToNext,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
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

/** 3.6: opt-in diagnostics - OFF collects nothing; shares are file or clipboard (owner's ask). */
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

/** Source chip: picks which source answers first, then refreshes the detail view. */
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

/** The empty-asset screen is not an answer: an em dash, never an implied "none" (3.3). */
internal val EM_DASH = "—"
