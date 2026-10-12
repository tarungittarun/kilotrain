package io.opentrack.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.opentrack.app.search.ResultsEngine
import io.opentrack.app.search.ResultsViewModel

/**
 * The one results screen. State copy comes ONLY from StateCopy (3.2); provenance is part of
 * every row (10.5); the banner is a full sentence for TalkBack.
 */
@Composable
fun ResultsScreen(
    viewModel: ResultsViewModel,
    diagnosticsStore: io.opentrack.app.diag.DiagnosticsStore? = null,
) {
    val ui by viewModel.state.collectAsState()
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var trainNumber by rememberSaveable { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
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
            ) {
                Text("search")
            }

            DiagnosticsBar(diagnosticsStore)

            OutlinedTextField(
                value = trainNumber,
                onValueChange = { trainNumber = it },
                label = { Text("or train number") },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Button(
                onClick = { viewModel.searchByNumber(trainNumber.trim()) },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text("show train")
            }

            val banner = StateCopy.banner(screenStateOf(ui.state), ui.bannerSource, age = null)
            Text(
                text = banner,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .semantics { contentDescription = "status: $banner" },
            )
            ui.note?.let { note ->
                Text(text = note, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp))
            }

            LazyColumn(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ui.rows) { row ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = StateCopy.row(row.number, row.name, row.timeDue ?: "—", row.provenanceLabel),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(onClick = { viewModel.showStops(row.number) }) {
                            Text("stop times")
                        }
                        TextButton(onClick = { viewModel.showLiveStatus(row.number) }) {
                            Text("live status")
                        }
                    }
                }
                val stopsNote = ui.stopsNote
                val status = ui.status
                if (status is ResultsEngine.StatusOutcome.Live) {
                    item {
                        Text(
                            text = StateCopy.liveStatus(status),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                } else if (status is ResultsEngine.StatusOutcome.Unavailable) {
                    item {
                        Text(
                            text = status.reason,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                if (ui.stops.isEmpty() && stopsNote != null) {
                    item {
                        Text(
                            text = stopsNote,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                } else {
                    items(ui.stops) { stop ->
                        Text(
                            text = StateCopy.stop(stop.code, stop.name, stop.arrival, stop.departure),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
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
            Spacer(modifier = Modifier.weight(1f))
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
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = captureRaw,
                    onCheckedChange = {
                        captureRaw = it
                        store.captureRaw = it
                    },
                )
            }
            Row(modifier = Modifier.padding(top = 4.dp)) {
                TextButton(onClick = { context.startActivity(store.shareAsFile(context)) }) {
                    Text("share as file")
                }
                TextButton(onClick = { context.startActivity(store.shareAsText(context)) }) {
                    Text("share as copy")
                }
                TextButton(onClick = { store.clear() }) { Text("clear") }
            }
        }
    }
}

@Composable
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
