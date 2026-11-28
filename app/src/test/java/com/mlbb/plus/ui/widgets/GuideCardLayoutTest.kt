package com.mlbb.plus.ui.widgets

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.mlbb.plus.ui.theme.MLBBPlusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Measures the two-row guide card.
 *
 * - Hidden spell tracker: the card is the compact header width.
 * - Spell tracker open (">"): the card widens to 70% of the landscape screen; it does not grow taller.
 * - The spell cells share the header row with the timer.
 * - "+" / "-" folds the guide row, which shortens the card.
 *
 * Heights are estimates from the layout constants; run this test to confirm them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GuideCardLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setCard(screenWidthDp: Float) {
        composeRule.setContent {
            MLBBPlusTheme {
                RoamGuideWidget(
                    role = "Roam",
                    roleType = "",
                    onBack = {},
                    screenWidthDp = screenWidthDp
                )
            }
        }
    }

    @Test
    fun headerIsCompactWhileSpellTrackerIsHidden() {
        setCard(screenWidthDp = 800f)
        composeRule.onNodeWithTag("overlay_card").assertWidthIsEqualTo(360.dp)
    }

    @Test
    fun openingSpellTrackerWidensToSeventyPercent() {
        setCard(screenWidthDp = 800f)
        composeRule.onNodeWithTag("overlay_spell_toggle").performClick()
        composeRule.onNodeWithTag("overlay_card").assertWidthIsEqualTo(560.dp)
    }

    @Test
    fun expandedWidthFollowsTheScreen() {
        setCard(screenWidthDp = 1000f)
        composeRule.onNodeWithTag("overlay_spell_toggle").performClick()
        composeRule.onNodeWithTag("overlay_card").assertWidthIsEqualTo(700.dp)
    }

    @Test
    fun spellCellsShareTheHeaderRowWithTheTimer() {
        setCard(screenWidthDp = 800f)
        composeRule.onNodeWithTag("overlay_spell_toggle").performClick()
        val card = composeRule.onNodeWithTag("overlay_card").getBoundsInRoot()
        val header = composeRule.onNodeWithTag("overlay_header").getBoundsInRoot()
        val timer = composeRule.onNodeWithTag("overlay_timer_button").getBoundsInRoot()
        val spell = composeRule.onNodeWithTag("overlay_spell_exp").getBoundsInRoot()
        assertTrue("header must be inside the card", header.top >= card.top && header.bottom <= card.bottom)
        assertTrue("timer must be inside the header", timer.top >= header.top && timer.bottom <= header.bottom)
        // Same row: the spell cell's vertical centre lies within the timer's band.
        val spellCentre = (spell.top + spell.bottom) / 2
        assertTrue("spell cell must sit on the timer's row", spellCentre >= timer.top && spellCentre <= timer.bottom)
    }

    @Test
    fun spellTrackerIsHiddenByDefault() {
        setCard(screenWidthDp = 800f)
        val card = composeRule.onNodeWithTag("overlay_card").getBoundsInRoot()
        val spell = composeRule.onNodeWithTag("overlay_spell_exp").getBoundsInRoot()
        // Zero-width slot while closed: the cell has no visible width.
        assertTrue("spell cell must be collapsed while hidden", spell.width <= 0f)
    }

    @Test
    fun guideFoldShortensTheCard() {
        setCard(screenWidthDp = 800f)
        val open = composeRule.onNodeWithTag("overlay_card").getBoundsInRoot()
        composeRule.onNodeWithTag("overlay_guide_toggle").performClick()
        val folded = composeRule.onNodeWithTag("overlay_card").getBoundsInRoot()
        assertTrue("folding the guide must make the card shorter", folded.height < open.height)
        val limit = with(composeRule.density) { 40.dp.toPx() }
        assertTrue("header-only card should be about one header row high", folded.height <= limit)
    }

    @Test
    fun voiceToggleIsAbsentWithoutAVoiceManager() {
        setCard(screenWidthDp = 800f)
        composeRule.onNodeWithTag("overlay_voice_toggle").assertDoesNotExist()
    }
}
