package io.opentrack.app.ui

import androidx.compose.foundation.layout.Arrangement
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
fun ResultsScreen(viewModel: ResultsViewModel) {
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
                    }
                }
                if (ui.stops.isEmpty() && ui.stopsNote != null) {
                    item {
                        Text(
                            text = ui.stopsNote,
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
