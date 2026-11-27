package com.mlbb.plus.ui.widgets

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScreenProfilesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = ScreenProfiles.prefs(context).also { it.edit().clear().commit() }

    @Test
    fun launchRecordsTheSizeOfItsOrientation() {
        ScreenProfiles.record(prefs, 1080, 2400, landscape = false)
        ScreenProfiles.record(prefs, 2400, 1080, landscape = true)
        assertEquals(1080 to 2400, ScreenProfiles.recordedSize(prefs, landscape = false))
        assertEquals(2400 to 1080, ScreenProfiles.recordedSize(prefs, landscape = true))
    }

    @Test
    fun neverSeenOrientationHasNoRecord() {
        assertNull(ScreenProfiles.recordedSize(prefs, landscape = true))
        assertNull(ScreenProfiles.savedBubble(prefs, landscape = true, widthPx = 2400, heightPx = 1080))
    }

    @Test
    fun bubbleIsRememberedPerOrientation() {
        ScreenProfiles.saveBubble(prefs, landscape = false, x = 0, y = 300, widthPx = 1080, heightPx = 2400)
        ScreenProfiles.saveBubble(prefs, landscape = true, x = 600, y = 40, widthPx = 2400, heightPx = 1080)
        assertEquals(0 to 300, ScreenProfiles.savedBubble(prefs, false, 1080, 2400))
        assertEquals(600 to 40, ScreenProfiles.savedBubble(prefs, true, 2400, 1080))
    }

    @Test
    fun bubbleKeepsItsPlaceWhenTheScreenSizeChanges() {
        // Saved on a 1080x2400 screen at 1/4 down; restored on a 720x1600 screen.
        ScreenProfiles.saveBubble(prefs, landscape = false, x = -270, y = 600, widthPx = 1080, heightPx = 2400)
        assertEquals((-180) to 400, ScreenProfiles.savedBubble(prefs, false, 720, 1600))
    }

    @Test
    fun fractionsRoundTrip() {
        val fx = bubbleFractionX(-120, 1200)
        val fy = bubbleFractionY(90, 800)
        assertEquals(-120 to 90, bubbleFromFraction(fx, fy, 1200, 800))
    }

    @Test
    fun zeroSizedScreenGivesZeroFraction() {
        assertEquals(0f, bubbleFractionX(50, 0), 0f)
        assertEquals(0f, bubbleFractionY(50, 0), 0f)
    }

    @Test
    fun landscapeWidgetIsSeventyPercentOfWidth() {
        assertEquals(560f, landscapeWidgetWidthDp(800f), 0.01f)
        assertEquals(700f, landscapeWidgetWidthDp(1000f), 0.01f)
    }

    @Test
    fun landscapeWidgetHasMinimumAndNeverFillsTheScreen() {
        // 0.7 * 400 = 280, raised to the 300 dp minimum.
        assertEquals(300f, landscapeWidgetWidthDp(400f), 0.01f)
        // Tiny screen: the 300 dp minimum would fill it, so it is held 8 dp short of the edge.
        assertEquals(292f, landscapeWidgetWidthDp(300f), 0.01f)
    }
}
