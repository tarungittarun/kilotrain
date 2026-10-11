package in.opentrack.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import in.opentrack.app.asset.OfflineAsset
import in.opentrack.app.asset.OfflineAssetCodec
import in.opentrack.app.search.ResultsViewModel
import in.opentrack.app.store.OpenTrackStore
import in.opentrack.app.ui.ResultsScreen
import in.opentrack.core.contract.SystemClock

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The offline asset ships in assets/ (empty-but-honest today: no source is admitted).
        val asset: OfflineAsset = try {
            assets.open("offline_asset.json").use { stream ->
                OfflineAssetCodec.parse(stream.bufferedReader().readText())
            }
        } catch (e: Exception) { // named: missing asset is an unreadable asset, never fabricated rows
            OfflineAsset(0, emptyList(), false, "this phone carries no offline data")
        }
        val store = OpenTrackStore(applicationContext, SystemClock)
        val viewModel = ResultsViewModel(asset, store, SystemClock)

        setContent {
            ResultsScreen(viewModel = viewModel)
        }
    }
}
