package io.opentrack.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * L2 screen tests (10.1): inflation + the exact copy of the state the empty honest asset
 * produces. No railway egress, no emulator (ADR 0006 keeps device-dependent proofs in their
 * own layers).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstRenderIsHonestUnverifiedNeverAnEmptyAnswer() {
        compose.onNodeWithText("offline - showing unverified timetable data. No source answered.")
            .assertIsDisplayed()
    }

    @Test
    fun searchingShowsTheSameHonestBanner() {
        compose.onNodeWithText("from station code").performTextInput("SMET")
        compose.onNodeWithText("to station code").performTextInput("NDLS")
        compose.onNodeWithText("search").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("offline - showing unverified timetable data. No source answered.")
            .assertIsDisplayed()
    }
}
