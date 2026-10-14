commit ed88a7cd002a7f806cf54f0841ebbe12adc10fcb
Author: kiloconnect[bot] <240665456+kiloconnect[bot]@users.noreply.github.com>
Date:   Tue Oct 13 12:03:09 2026 +0000

    UI: add edge-to-edge support to avoid content going under system bars

diff --git a/app/src/main/kotlin/io/opentrack/app/ui/ResultsScreen.kt b/app/src/main/kotlin/io/opentrack/app/ui/ResultsScreen.kt
index d3c84f4..4cbcd66 100644
--- a/app/src/main/kotlin/io/opentrack/app/ui/ResultsScreen.kt
+++ b/app/src/main/kotlin/io/opentrack/app/ui/ResultsScreen.kt
@@ -19,6 +19,7 @@ import androidx.compose.material3.CircularProgressIndicator
 import androidx.compose.material3.Divider
 import androidx.compose.material3.MaterialTheme
 import androidx.compose.material3.OutlinedTextField
+import androidx.compose.foundation.layout.safeDrawingPadding
 import androidx.compose.material3.Surface
 import androidx.compose.material3.Switch
 import androidx.compose.material3.Text
@@ -57,7 +58,7 @@ fun ResultsScreen(
     val ui by viewModel.state.collectAsState()
     val detail = ui.selectedTrainNumber
 
-    Surface(modifier = Modifier.fillMaxSize()) {
+    Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
         if (detail != null) {
             DetailView(ui = ui, viewModel = viewModel, diagnosticsStore = diagnosticsStore)
         } else {
