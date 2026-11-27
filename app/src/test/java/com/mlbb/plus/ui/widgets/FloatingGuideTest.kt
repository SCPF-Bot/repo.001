package com.mlbb.plus.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingGuideTest {

    @Test
    fun guideOpensOnlyInLandscape() {
        assertTrue(guideOpen(landscape = true, expanded = true))
        assertFalse(guideOpen(landscape = true, expanded = false))
    }

    @Test
    fun portraitNeverShowsTheGuide_evenWhenExpanded() {
        // Rotating to portrait must hide the card, whatever the expanded flag says.
        assertFalse(guideOpen(landscape = false, expanded = true))
        assertFalse(guideOpen(landscape = false, expanded = false))
    }

    @Test
    fun offsetInsideScreenIsUnchanged() {
        assertEquals(10 to 200, clampedOffset(10, 200, screenWidthPx = 2400, screenHeightPx = 1080))
    }

    @Test
    fun offsetIsClampedWhenRotatingToNarrowerScreen() {
        // Saved in landscape at x = 1100 on a 2400 px wide screen. Portrait is 1080 px wide,
        // so the horizontal offset must be pulled back to 540.
        assertEquals(540 to 300, clampedOffset(1100, 300, screenWidthPx = 1080, screenHeightPx = 2400))
    }

    @Test
    fun offsetIsClampedVerticallyAndNegativeXIsKept() {
        assertEquals((-400) to 0, clampedOffset(-400, -50, screenWidthPx = 1200, screenHeightPx = 800))
        assertEquals(0 to 400, clampedOffset(0, 9000, screenWidthPx = 1200, screenHeightPx = 800))
    }
}
