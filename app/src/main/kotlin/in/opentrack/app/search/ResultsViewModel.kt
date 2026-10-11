package in.opentrack.app.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import in.opentrack.app.asset.OfflineAsset
import in.opentrack.app.store.OpenTrackStore
import in.opentrack.core.contract.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResultsViewModel(
    private val asset: OfflineAsset,
    private val store: OpenTrackStore,
    private val clock: Clock,
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
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.Default) {
                ResultsEngine(asset, store, clock).lookup(from, to)
            }
            _state.value = UiState(outcome.state, outcome.rows, outcome.bannerSource, outcome.note)
        }
    }
}
