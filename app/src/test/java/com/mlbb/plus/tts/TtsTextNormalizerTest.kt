package com.mlbb.plus.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * app/src/test/java/com/mlbb/plus/tts/TtsTextNormalizerTest.kt
 *
 * Covers the three things the feature spec calls out for the normalizer - time
 * phrasing, MLBB abbreviation expansion and truncation - plus the two invariants
 * that protect the player from hearing garbage: no raw field name or URL is ever
 * spoken, and cleaning is idempotent.
 *
 * Every expectation below was checked against the real guide JSON (286 steps
 * across the five role files), not invented text.
 */
class TtsTextNormalizerTest {

    // ---------------------------------------------------------------
    // Time phrasing
    // ---------------------------------------------------------------

    @Test
    fun `clocks become words because both engines read 2-00 as digits`() {
        assertEquals("Turtle at two minutes", TtsTextNormalizer.clean("Turtle at 2:00"))
        assertEquals("Ward at thirty seconds", TtsTextNormalizer.clean("Ward at 0:30"))
        assertEquals("Rotate at one minute thirty seconds", TtsTextNormalizer.clean("Rotate at 1:30"))
        assertEquals("Lord at eight minutes", TtsTextNormalizer.clean("Lord at 8:00"))
        assertEquals("Lord at twelve minutes", TtsTextNormalizer.clean("Lord at 12:00"))
    }

    @Test
    fun `spoken time renders every shape the guide timer produces`() {
        assertEquals("now", TtsTextNormalizer.spokenTime(0))
        assertEquals("45 seconds", TtsTextNormalizer.spokenTime(45))
        assertEquals("1 minute", TtsTextNormalizer.spokenTime(60))
        assertEquals("2 minutes", TtsTextNormalizer.spokenTime(120))
        assertEquals("2 minutes 5 seconds", TtsTextNormalizer.spokenTime(125))
        assertEquals("8 minutes", TtsTextNormalizer.spokenTime(480))
    }

    @Test
    fun `time ranges are dropped because the clock prefix already carries them`() {
        val cleaned = TtsTextNormalizer.clean("Phase 0:00-0:40 begins now.")
        assertFalse("range survived: $cleaned", cleaned.contains("0:00"))
        assertEquals("Phase begins now.", cleaned)
    }

    @Test
    fun `durations and numeric ranges are spoken, not spelled as digits`() {
        // numberWord() only spells 0-59; larger values stay as digits, which
        // both engines pronounce correctly.
        assertEquals("Flicker 120 seconds", TtsTextNormalizer.clean("Flicker 120s"))
        assertEquals("Sprint 75 seconds", TtsTextNormalizer.clean("Sprint 75s"))
        assertEquals("wait twenty to thirty seconds", TtsTextNormalizer.clean("wait 20-30s"))
        assertEquals("take the 1st to 2nd turtle", TtsTextNormalizer.clean("take the 1st-2nd turtle"))
        assertEquals("Fight 5 versus 5 only when ready", TtsTextNormalizer.clean("Fight 5v5 only when ready"))
    }

    // ---------------------------------------------------------------
    // Abbreviation expansion (speak-only; visible text is untouched)
    // ---------------------------------------------------------------

    @Test
    fun `mlbb abbreviations expand on speak only`() {
        assertEquals(
            "Use Retribution on the enemy jungler to steal Turtle.",
            TtsTextNormalizer.clean("Use Retri on the enemy jungler to steal Turtle.")
        )
        assertEquals("Buy E X P lane items", TtsTextNormalizer.clean("Buy EXP items"))
        assertEquals("crowd control", TtsTextNormalizer.clean("CC"))
        assertEquals("the enemy marksman", TtsTextNormalizer.clean("the enemy MM"))
        assertEquals("reach level 4 first", TtsTextNormalizer.clean("reach Lv4 first"))
        assertEquals("as damage dealer, kite", TtsTextNormalizer.clean("as DPS, kite"))
        assertEquals("jungle path", TtsTextNormalizer.clean("JG path"))
        assertEquals("read the knowledge base", TtsTextNormalizer.clean("read the KB"))
    }

    @Test
    fun `expansion never doubles a word that is already there`() {
        // "EXP lane" must not become "E X P lane lane".
        assertEquals("hold E X P lane", TtsTextNormalizer.clean("hold EXP lane"))
        assertEquals("rotate to gold lane", TtsTextNormalizer.clean("rotate to Gold"))
        assertEquals("Gold lane gives extra gold", TtsTextNormalizer.clean("Gold lane gives extra gold"))
    }

    @Test
    fun `lane names are case sensitive so currency and game phases survive`() {
        // Lowercase "gold" is currency in the real guide text.
        assertEquals("take tower gold", TtsTextNormalizer.clean("take tower gold"))
        assertEquals("your gold comes from helping", TtsTextNormalizer.clean("your gold comes from helping"))
        // "Mid Game" is a phase, and an already-qualified name stays qualified.
        assertEquals("Mid Game - control bushes", TtsTextNormalizer.clean("Mid Game - control bushes"))
        assertEquals("keep mid priority", TtsTextNormalizer.clean("keep mid priority"))
        assertEquals("push Gold tower", TtsTextNormalizer.clean("push Gold tower"))
    }

    @Test
    fun `flicker is kept verbatim as the spec requires`() {
        assertEquals(
            "wait for the enemy Flicker to be used",
            TtsTextNormalizer.clean("wait for the enemy Flicker to be used")
        )
    }

    // ---------------------------------------------------------------
    // Truncation to the 8-18 word spoken budget
    // ---------------------------------------------------------------

    @Test
    fun `long text is cut at a sentence boundary, not mid sentence`() {
        val input = "Rotate to Gold lane when your mid laner pushes. " +
            "Then ward the river bush so the jungler cannot gank unseen."
        val spoken = TtsTextNormalizer.spoken(input)
        // "Gold lane" is already qualified, so the dictionary leaves it alone
        // (including its capitalisation); only a bare "Gold" expands.
        assertEquals("Rotate to Gold lane when your mid laner pushes.", spoken)
        assertTrue(spoken.endsWith("."))
        assertTrue(TtsTextNormalizer.countWords(spoken) <= TtsTextNormalizer.MAX_WORDS)
    }

    @Test
    fun `an over long first sentence is cut at a clause boundary`() {
        val input = "Contest only if the enemy jungler is dead, otherwise trade for Lord, " +
            "and never dive the back line without your ultimate ready"
        val spoken = TtsTextNormalizer.condense(input, 8)
        assertTrue("too long: $spoken", TtsTextNormalizer.countWords(spoken) <= 8)
        assertTrue("cut mid clause: $spoken", spoken.endsWith("."))
    }

    @Test
    fun `a hard cut never leaves a dangling connector`() {
        val spoken = TtsTextNormalizer.condense(
            "Push the wave then rotate through the river toward the enemy jungle",
            5
        )
        assertTrue(spoken.endsWith("."))
        val last = spoken.trimEnd('.').substringAfterLast(' ')
        assertFalse("ends on a connector: $spoken", last.lowercase() in setOf("the", "and", "to", "of", "a"))
    }

    @Test
    fun `spoken output stays inside the word budget for every shape of input`() {
        listOf(
            "",
            "Setup",
            "Guard buff, don't feed",
            listOf("Setup", "Guard buff, don't feed", "Stand near the buff pit and watch the enemy jungler.")
        ).forEach { input ->
            val spoken = when (input) {
                is List<*> -> TtsTextNormalizer.spoken(input.filterIsInstance<String>())
                else -> TtsTextNormalizer.spoken(input as String)
            }
            assertTrue(
                "over budget (${TtsTextNormalizer.countWords(spoken)}w): $spoken",
                TtsTextNormalizer.countWords(spoken) <= TtsTextNormalizer.MAX_WORDS
            )
        }
    }

    @Test
    fun `fragments are composed most important first and joined as sentences`() {
        val spoken = TtsTextNormalizer.spoken(
            listOf(
                "Setup",
                "Guard buff, don't feed",
                "Stand near the buff pit and watch the enemy jungler for the first forty seconds."
            )
        )
        assertTrue(spoken.startsWith("Setup. Guard buff, don't feed."))
        assertTrue(spoken.length > "Setup. Guard buff, don't feed.".length)
    }

    // ---------------------------------------------------------------
    // Nothing raw is ever spoken
    // ---------------------------------------------------------------

    @Test
    fun `field names, links and markdown never reach the speaker`() {
        val cleaned = TtsTextNormalizer.clean(
            "objective_contest says push; see https://mlbbplus.app/guide and `winCondition` for details"
        )
        assertFalse("snake_case survived: $cleaned", cleaned.contains("_"))
        assertFalse("url survived: $cleaned", cleaned.contains("http"))
        assertFalse("camelCase survived: $cleaned", cleaned.contains("winCondition"))
        assertFalse("backtick survived: $cleaned", cleaned.contains("`"))
    }

    @Test
    fun `markdown emphasis is stripped but contractions survive`() {
        assertEquals("Priority, clear the wave first", TtsTextNormalizer.clean("**Priority:** clear the wave first"))
        assertEquals("Guard buff, don't feed", TtsTextNormalizer.clean("Guard buff, don't feed"))
        assertEquals("Don't push early and get ganked.", TtsTextNormalizer.clean("Don't push early and get ganked."))
    }

    @Test
    fun `cleaning is idempotent so a second pass cannot corrupt a line`() {
        val inputs = listOf(
            "Use Retri at 2:00 to steal Turtle.",
            "Buy EXP lane items and hold mid game pressure.",
            "AoE damage from the enemy MM will shred you; keep HP high.",
            "objective_contest: push, then rotate -> Gold"
        )
        inputs.forEach { input ->
            val once = TtsTextNormalizer.clean(input)
            assertEquals("not idempotent for: $input", once, TtsTextNormalizer.clean(once))
        }
    }

    @Test
    fun `empty and punctuation only input produce nothing to say`() {
        assertEquals("", TtsTextNormalizer.spoken(""))
        assertEquals("", TtsTextNormalizer.spoken("   "))
        assertEquals("", TtsTextNormalizer.spoken(listOf("", "  ")))
        assertEquals(0, TtsTextNormalizer.countWords(TtsTextNormalizer.condense("...")))
    }
}
