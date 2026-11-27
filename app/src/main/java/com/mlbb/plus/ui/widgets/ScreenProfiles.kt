package com.mlbb.plus.ui.widgets

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlin.math.roundToInt

/**
 * Remembers the screen size seen in each orientation, and where the bubble sat in it.
 * Nothing is configured by hand: the overlay records the current orientation's size each time
 * it is shown and each time the phone rotates.
 *
 * Layout does NOT read these stored sizes. The widget always uses the live display metrics,
 * because a stored value can go stale and the live one is free. What the record is for is the
 * bubble: each orientation keeps its own spot, stored as a fraction of the screen it was
 * dragged on. The same fraction maps back onto any screen size, so a bubble saved on one
 * display lands in the same place on a different one (foldable, external display, density).
 */
internal object ScreenProfiles {
    private const val PREFS = "screen_profiles"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(landscape: Boolean, part: String) =
        (if (landscape) "land_" else "port_") + part

    /** Records the screen size of [landscape] (or portrait) as seen now. */
    fun record(prefs: SharedPreferences, widthPx: Int, heightPx: Int, landscape: Boolean) {
        prefs.edit {
            putInt(key(landscape, "w"), widthPx)
            putInt(key(landscape, "h"), heightPx)
        }
    }

    /** The screen size last recorded for this orientation, or null if it was never seen. */
    fun recordedSize(prefs: SharedPreferences, landscape: Boolean): Pair<Int, Int>? {
        val w = prefs.getInt(key(landscape, "w"), 0)
        val h = prefs.getInt(key(landscape, "h"), 0)
        return if (w > 0 && h > 0) w to h else null
    }

    /**
     * Saves the bubble at ([x], [y]) for this orientation. The screen size is recorded in the
     * same write, so the fraction and its reference size are always stored together.
     */
    fun saveBubble(
        prefs: SharedPreferences,
        landscape: Boolean,
        x: Int,
        y: Int,
        widthPx: Int,
        heightPx: Int
    ) {
        record(prefs, widthPx, heightPx, landscape)
        prefs.edit {
            putFloat(key(landscape, "bx"), bubbleFractionX(x, widthPx))
            putFloat(key(landscape, "by"), bubbleFractionY(y, heightPx))
        }
    }

    /** The bubble position saved for this orientation, mapped onto the current screen. */
    fun savedBubble(prefs: SharedPreferences, landscape: Boolean, widthPx: Int, heightPx: Int): Pair<Int, Int>? {
        val fxKey = key(landscape, "bx")
        if (!prefs.contains(fxKey)) return null
        return bubbleFromFraction(
            fx = prefs.getFloat(fxKey, 0f),
            fy = prefs.getFloat(key(landscape, "by"), 0f),
            widthPx = widthPx,
            heightPx = heightPx
        )
    }
}

/** x is the offset from the horizontal centre, so its fraction runs from -0.5 to 0.5. */
internal fun bubbleFractionX(x: Int, widthPx: Int): Float =
    if (widthPx > 0) x.toFloat() / widthPx else 0f

/** y is the offset from the top, so its fraction runs from 0 to 1. */
internal fun bubbleFractionY(y: Int, heightPx: Int): Float =
    if (heightPx > 0) y.toFloat() / heightPx else 0f

internal fun bubbleFromFraction(fx: Float, fy: Float, widthPx: Int, heightPx: Int): Pair<Int, Int> =
    (fx * widthPx).roundToInt() to (fy * heightPx).roundToInt()
