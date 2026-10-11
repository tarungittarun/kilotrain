package io.opentrack.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.opentrack.app.asset.OfflineAsset
import io.opentrack.app.asset.OfflineAssetCodec
import io.opentrack.app.search.ResultsViewModel
import io.opentrack.app.source.ErailTrainSource
import io.opentrack.app.source.HttpErailTransport
import io.opentrack.app.source.SourceLocator
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
            // keyless, no account (3.1); the flag decides whether the source may answer (12.6)
            erailSource = SourceLocator.erailSource ?: ErailTrainSource(HttpErailTransport()),
        )

        setContent {
            ResultsScreen(viewModel = viewModel)
        }
    }
}
