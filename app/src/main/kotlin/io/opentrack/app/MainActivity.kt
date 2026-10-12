package io.opentrack.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.opentrack.app.asset.OfflineAsset
import io.opentrack.app.asset.OfflineAssetCodec
import io.opentrack.app.search.ResultsViewModel
import io.opentrack.app.store.OpenTrackStore
import io.opentrack.app.ui.ResultsScreen
import io.opentrack.core.contract.SystemClock

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
        val viewModel = ResultsViewModel(
            asset = asset,
            store = store,
            clock = SystemClock,
            // no source passed here: the ViewModel resolves it at search time, so tests can
            // install a fake (SourceLocator) while production gets the real transport (3.1)
        )

        setContent {
            ResultsScreen(viewModel = viewModel)
        }
    }
}
