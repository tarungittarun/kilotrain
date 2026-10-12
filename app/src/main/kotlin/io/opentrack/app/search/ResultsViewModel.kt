package io.opentrack.app.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.opentrack.app.asset.OfflineAsset
import io.opentrack.app.source.ErailTrainSource
import io.opentrack.app.source.HttpErailTransport
import io.opentrack.app.source.SourceLocator
import io.opentrack.app.store.OpenTrackStore
import io.opentrack.core.contract.Clock
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
) : ViewModel() {

    data class UiState(
        val state: ResultsEngine.UiStateName,
        val rows: List<ResultsEngine.RowView>,
        val bannerSource: String?,
        val note: String?,
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
                block(ResultsEngine(asset, store, clock, source), a, b)
            }
            _state.value = UiState(outcome.state, outcome.rows, outcome.bannerSource, outcome.note)
        }
    }
}
