package com.mlbb.plus.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * app/src/test/java/com/mlbb/plus/tts/VoiceGuideManagerTest.kt
 *
 * State transitions of the Voice Guide coach.
 *
 * [VoiceGuideManager] itself needs a Context (SharedPreferences, AudioManager,
 * AccessibilityManager), so the decisions that drive its
 * state live in the pure [VoiceGuideTransitions] table and are tested here on a
 * plain JVM. The manager and the overlay widget both call that table, which is
 * what keeps the on-screen button, the notification action and these tests
 * describing one behaviour.
 *
 * Every case below maps to an acceptance criterion in the feature spec:
 * auto-speak once per step change, no duplicates, Prev/Next always speaking,
 * Pause/Resume only applying where they can, and the spoken line staying inside
 * the word budget.
 */
class VoiceGuideManagerTest {

    private val setup = SpokenStep(
        key = "roam-aggressive#0#0",
        startSeconds = 0,
        fragments = listOf("Setup", "Guard buff, don't feed", "Stand near the buff pit and watch the enemy jungler.")
    )

    private val turtle = SpokenStep(
        key = "roam-aggressive#3#120",
        startSeconds = 120,
        fragments = listOf("First Turtle", "Contest only with level four", "Arrive early, push the wave, then zone the pit.")
    )

    // ---------------------------------------------------------------
    // What gets said
    // ---------------------------------------------------------------

    @Test
    fun `a step at zero seconds has no clock prefix`() {
        val line = VoiceGuideTransitions.spokenLine(setup.fragments, setup.startSeconds)
        assertFalse("unexpected prefix: $line", line.startsWith("At "))
        assertTrue(line.startsWith("Setup."))
    }

    @Test
    fun `a later step is prefixed with the spoken game clock`() {
        val line = VoiceGuideTransitions.spokenLine(turtle.fragments, turtle.startSeconds)
        assertTrue("missing clock prefix: $line", line.startsWith("At 2 minutes."))
        assertTrue(line.contains("First Turtle"))
    }

    @Test
    fun `the whole line stays inside the spoken word budget`() {
        listOf(setup, turtle).forEach { step ->
            val line = VoiceGuideTransitions.spokenLine(step.fragments, step.startSeconds)
            val body = line.substringAfter(". ", line)
            assertTrue(
                "body over budget (${TtsTextNormalizer.countWords(body)}w): $line",
                TtsTextNormalizer.countWords(body) <= TtsTextNormalizer.MAX_WORDS
            )
        }
    }

    @Test
    fun `a step with no usable text produces nothing to say`() {
        assertEquals("", VoiceGuideTransitions.spokenLine(emptyList(), 0))
        assertEquals("", VoiceGuideTransitions.spokenLine(listOf("", "   "), 0))
        // A clock is still worth saying even when the prose is missing.
        assertEquals("At 2 minutes", VoiceGuideTransitions.spokenLine(emptyList(), 120))
    }

    @Test
    fun `fragments fall back in priority order`() {
        // phase + callout only (instruction missing)
        // Fragments are joined with a full stop between them; no extra stop is
        // appended after the last one, so a short line stays short.
        assertEquals(
            "First Turtle. Contest with level four",
            VoiceGuideTransitions.spokenLine(listOf("First Turtle", "Contest with level four"), 0)
        )
        // callout only
        assertEquals("Guard buff, don't feed", VoiceGuideTransitions.spokenLine(listOf("Guard buff, don't feed"), 0))
    }

    // ---------------------------------------------------------------
    // Auto-speak: once per step change, never a duplicate
    // ---------------------------------------------------------------

    @Test
    fun `auto speak fires once per step change`() {
        assertTrue(
            VoiceGuideTransitions.shouldAutoSpeak(
                autoSpeakEnabled = true,
                newKey = turtle.key,
                lastKey = setup.key,
                fragmentsChanged = true
            )
        )
    }

    @Test
    fun `the same step with the same text is never repeated`() {
        // This is the recomposition / 1 Hz clock tick case: the widget recomposes
        // and re-derives the same step, and the coach must stay quiet.
        assertFalse(
            VoiceGuideTransitions.shouldAutoSpeak(
                autoSpeakEnabled = true,
                newKey = turtle.key,
                lastKey = turtle.key,
                fragmentsChanged = false
            )
        )
    }

    @Test
    fun `the same key with different text does speak again`() {
        // Switching playstyle can reuse a step index; the content changed, so it
        // is a new instruction and must be read.
        assertTrue(
            VoiceGuideTransitions.shouldAutoSpeak(
                autoSpeakEnabled = true,
                newKey = turtle.key,
                lastKey = turtle.key,
                fragmentsChanged = true
            )
        )
    }

    @Test
    fun `auto speak off means only the Speaker button talks`() {
        assertFalse(
            VoiceGuideTransitions.shouldAutoSpeak(
                autoSpeakEnabled = false,
                newKey = turtle.key,
                lastKey = "",
                fragmentsChanged = true
            )
        )
    }

    @Test
    fun `a step with no key cannot auto speak`() {
        assertFalse(
            VoiceGuideTransitions.shouldAutoSpeak(
                autoSpeakEnabled = true,
                newKey = "",
                lastKey = "",
                fragmentsChanged = true
            )
        )
    }

    // ---------------------------------------------------------------
    // Primary button: label and action agree in every phase
    // ---------------------------------------------------------------

    @Test
    fun `idle offers Speak`() {
        assertEquals(
            "Speak",
            VoiceGuideTransitions.primaryLabel(VoiceGuideManager.Phase.IDLE, downloading = false, preparing = false)
        )
        assertEquals(
            VoiceGuideTransitions.PrimaryAction.SPEAK,
            VoiceGuideTransitions.primaryAction(VoiceGuideManager.Phase.IDLE, downloading = false, preparing = false)
        )
    }

    @Test
    fun `speaking offers Pause and paused offers Resume`() {
        assertEquals(
            "Pause",
            VoiceGuideTransitions.primaryLabel(VoiceGuideManager.Phase.SPEAKING, downloading = false, preparing = false)
        )
        assertEquals(
            VoiceGuideTransitions.PrimaryAction.PAUSE,
            VoiceGuideTransitions.primaryAction(VoiceGuideManager.Phase.SPEAKING, downloading = false, preparing = false)
        )
        assertEquals(
            "Resume",
            VoiceGuideTransitions.primaryLabel(VoiceGuideManager.Phase.PAUSED, downloading = false, preparing = false)
        )
        assertEquals(
            VoiceGuideTransitions.PrimaryAction.RESUME,
            VoiceGuideTransitions.primaryAction(VoiceGuideManager.Phase.PAUSED, downloading = false, preparing = false)
        )
    }

    @Test
    fun `a download turns the primary button into Cancel`() {
        assertEquals(
            "Cancel",
            VoiceGuideTransitions.primaryLabel(VoiceGuideManager.Phase.DOWNLOADING, downloading = true, preparing = false)
        )
        assertEquals(
            VoiceGuideTransitions.PrimaryAction.CANCEL_DOWNLOAD,
            VoiceGuideTransitions.primaryAction(VoiceGuideManager.Phase.DOWNLOADING, downloading = true, preparing = false)
        )
    }

    @Test
    fun `loading the model ignores taps instead of queueing a second line`() {
        assertEquals(
            "Loading",
            VoiceGuideTransitions.primaryLabel(VoiceGuideManager.Phase.PREPARING, downloading = false, preparing = true)
        )
        assertEquals(
            VoiceGuideTransitions.PrimaryAction.NONE,
            VoiceGuideTransitions.primaryAction(VoiceGuideManager.Phase.PREPARING, downloading = false, preparing = true)
        )
    }

    @Test
    fun `errors and unavailable engines fall back to offering Speak`() {
        listOf(VoiceGuideManager.Phase.ERROR, VoiceGuideManager.Phase.UNAVAILABLE).forEach { phase ->
            assertEquals("Speak", VoiceGuideTransitions.primaryLabel(phase, downloading = false, preparing = false))
            assertEquals(
                VoiceGuideTransitions.PrimaryAction.SPEAK,
                VoiceGuideTransitions.primaryAction(phase, downloading = false, preparing = false)
            )
        }
    }

    @Test
    fun `every phase has a label and an action`() {
        // Guards against adding a Phase and forgetting the table.
        VoiceGuideManager.Phase.entries.forEach { phase ->
            assertTrue(VoiceGuideTransitions.primaryLabel(phase, false, false).isNotBlank())
            VoiceGuideTransitions.primaryAction(phase, false, false)
        }
    }

    // ---------------------------------------------------------------
    // Pause / Resume transitions
    // ---------------------------------------------------------------

    @Test
    fun `pause only applies while speaking`() {
        assertEquals(
            VoiceGuideManager.Phase.PAUSED,
            VoiceGuideTransitions.phaseAfterPause(VoiceGuideManager.Phase.SPEAKING)
        )
        VoiceGuideManager.Phase.entries
            .filter { it != VoiceGuideManager.Phase.SPEAKING }
            .forEach { assertEquals(it, VoiceGuideTransitions.phaseAfterPause(it)) }
    }

    @Test
    fun `resume only applies while paused`() {
        assertEquals(
            VoiceGuideManager.Phase.SPEAKING,
            VoiceGuideTransitions.phaseAfterResume(VoiceGuideManager.Phase.PAUSED)
        )
        VoiceGuideManager.Phase.entries
            .filter { it != VoiceGuideManager.Phase.PAUSED }
            .forEach { assertEquals(it, VoiceGuideTransitions.phaseAfterResume(it)) }
    }

    @Test
    fun `pause then resume returns to speaking`() {
        val paused = VoiceGuideTransitions.phaseAfterPause(VoiceGuideManager.Phase.SPEAKING)
        assertEquals(VoiceGuideManager.Phase.SPEAKING, VoiceGuideTransitions.phaseAfterResume(paused))
    }
}
