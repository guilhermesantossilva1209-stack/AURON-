package br.com.auron.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import br.com.auron.ui.theme.AuronTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.Assume.assumeTrue
import android.animation.ValueAnimator

/** Execute on an emulator/device with Android animation duration scale enabled. */
class OpeningAnimationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun openingChangesItsRenderedGeometryOverTime() {
        assumeTrue("Zoom requires Android animations enabled", ValueAnimator.areAnimatorsEnabled())
        compose.mainClock.autoAdvance = false
        compose.setContent { AuronTheme { AuronIntro {} } }
        compose.waitUntil(timeoutMillis = 5000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag("intro_reference").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.advanceTimeBy(500)
        val revealing = compose.onNodeWithTag("auron_intro").captureToImage()
        compose.mainClock.advanceTimeBy(1300)
        val zoomedOut = compose.onNodeWithTag("auron_intro").captureToImage()
        assertTrue("The opening must render camera movement", changedSamples(revealing, zoomedOut) > 20)
    }

    @Test fun skipIsAvailableWithoutWaitingForTheAnimation() {
        var skipped = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { AuronTheme { AuronIntro { skipped++ } } }
        compose.onNodeWithTag("intro_skip").performClick()
        assertEquals(1, skipped)
    }

    @Test fun disabledMotionKeepsTheRobotStill() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AuronTheme { AuronRobot(Modifier.size(300.dp).testTag("still_robot"), motionEnabled = false) }
        }
        compose.mainClock.advanceTimeBy(64)
        val first = compose.onNodeWithTag("still_robot").captureToImage()
        compose.mainClock.advanceTimeBy(1400)
        val later = compose.onNodeWithTag("still_robot").captureToImage()
        assertEquals(0, changedSamples(first, later))
    }

    private fun changedSamples(first: ImageBitmap, second: ImageBitmap): Int {
        assertEquals(first.width, second.width)
        assertEquals(first.height, second.height)
        val a = first.toPixelMap()
        val b = second.toPixelMap()
        var changed = 0
        for (y in 0 until first.height step 8) {
            for (x in 0 until first.width step 8) {
                if (a[x, y] != b[x, y]) changed++
            }
        }
        return changed
    }
}
