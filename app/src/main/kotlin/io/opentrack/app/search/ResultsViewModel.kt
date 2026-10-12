package io.opentrack.app.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.opentrack.app.asset.OfflineAsset
import io.opentrack.app.source.ErailPairSource
import io.opentrack.app.source.ErailRouteSource
import io.opentrack.app.source.ErailTrainSource
import io.opentrack.app.source.NtesRunningStatusSource
import io.opentrack.app.source.HttpErailTransport
import io.opentrack.app.source.SourceLocator
import io.opentrack.app.store.OpenTrackStore
import io.opentrack.core.contract.Clock
import io.opentrack.core.diag.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResultsViewModel(
    private val asset: OfflineAsset,
    private val store: OpenTrackStore,
    private val clock: Clock,
    private val erailSource: ErailTrainSource? = null,
    private val erailPairSource: ErailPairSource? = null,
    private val erailRouteSource: ErailRouteSource? = null,
    private val ntesStatusSource: NtesRunningStatusSource? = null,
    private val diagnostics: Diagnostics? = null,
) : ViewModel() {

    data class UiState(
        val state: ResultsEngine.UiStateName,
        val rows: List<ResultsEngine.RowView>,
        val bannerSource: String?,
        val note: String?,
        val stops: List<ResultsEngine.StopView> = emptyList(),
        val stopsNote: String? = null,
        val status: ResultsEngine.StatusOutcome? = null,
    )

    private val _state = MutableStateFlow(
        UiState(ResultsEngine.UiStateName.OFFLINE_UNVERIFIED, emptyList(), null, null)
    )
    val state: StateFlow<UiState> = _state

    /** Never blocks the first render on a network call (3.8/6.7): we only read local data. */
    fun search(from: String, to: String) {
        launch(from, to) { engine, f, t -> engine.lookup(f, t) }
    }

    /** Priority 1: a train number is a complete query on its own. */
    fun searchByNumber(number: String) {
        launch(number, number) { engine, n, _ -> engine.lookupByNumber(n) }
    }

    private fun launch(
        a: String,
        b: String,
        block: (ResultsEngine, String, String) -> ResultsEngine.Outcome,
    ) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.Default) {
                // resolved at CALL time (not construction) so tests install a fake before the
                // click; production resolves to the real transport here.
                val source = erailSource ?: SourceLocator.erailSource
                    ?: ErailTrainSource(HttpErailTransport())
                val pairSource = erailPairSource ?: SourceLocator.erailPairSource
                    ?: ErailPairSource(HttpErailTransport())
                val routeSource = erailRouteSource ?: SourceLocator.erailRouteSource
                    ?: ErailRouteSource(HttpErailTransport(), source)
                block(
                    ResultsEngine(
                        asset = asset,
                        store = store,
                        clock = clock,
                        erailSource = source,
                        erailPairSource = pairSource,
                        erailRouteSource = routeSource,
                        diagnostics = diagnostics,
                    ),
                    a, b,
                )
            }
            _state.value = UiState(outcome.state, outcome.rows, outcome.bannerSource, outcome.note)
        }
    }

    /** Priority 3: the live running status for a train from the current rows. */
    fun showLiveStatus(number: String) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.Default) {
                val source = ntesStatusSource ?: SourceLocator.ntesStatusSource
                ResultsEngine(
                    asset = asset,
                    store = store,
                    clock = clock,
                    ntesStatusSource = source,
                    diagnostics = diagnostics,
                ).liveStatus(number)
            } ?: return@launch
            val current = _state.value
            _state.value = when (outcome) {
                is ResultsEngine.StatusOutcome.Live -> current.copy(status = outcome, stops = emptyList(), stopsNote = null)
                is ResultsEngine.StatusOutcome.Unavailable -> current.copy(stops = emptyList(), stopsNote = outcome.reason)
            }
        }
    }

    /** Priority 3: show the stop times for a train from the current rows. */
    fun showStops(number: String) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.Default) {
                val routeSource = erailRouteSource ?: SourceLocator.erailRouteSource
                    ?: ErailRouteSource(
                        HttpErailTransport(),
                        ErailTrainSource(HttpErailTransport()),
                    )
                ResultsEngine(
                    asset = asset,
                    store = store,
                    clock = clock,
                    erailRouteSource = routeSource,
                    diagnostics = diagnostics,
                ).lookupRoute(number)
            } ?: return@launch
            val current = _state.value
            _state.value = when (outcome) {
                is ResultsEngine.RouteOutcome.Stops -> current.copy(stops = outcome.stops, stopsNote = null)
                is ResultsEngine.RouteOutcome.Unavailable -> current.copy(stops = emptyList(), stopsNote = outcome.reason)
            }
        }
    }
}
