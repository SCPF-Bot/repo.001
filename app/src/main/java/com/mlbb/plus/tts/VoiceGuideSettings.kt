package com.mlbb.plus.tts

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit

/**
 * Every Voice Guide preference in one place, on the same [SharedPreferences]
 * mechanism the rest of the app uses (no DataStore, no DI). Values survive a
 * process death and a device restart, so the overlay comes back with the same
 * engine, voice and rate.
 *
 * IMPORTANT: [isEnabled] defaults to false. Nothing is ever spoken,
 * focus-requested or unpacked until the player turns Voice Guide on in
 * Settings, or taps the Speaker control in the overlay. That first tap is the
 * consent moment.
 */
object VoiceGuideSettings {

    /** Engine preference: let the manager pick the best available one. */
    const val ENGINE_AUTO = "auto"

    /** Force sherpa-onnx (Kokoro, then Piper). Needs an installed voice pack. */
    const val ENGINE_SHERPA = "sherpa"

    /** Force the platform TextToSpeech service. Needs no voice pack. */
    const val ENGINE_SYSTEM = "system"

    /** Kokoro v0_19 speaker ids (female / male). */
    const val VOICE_KOKORO_FEMALE = "af_heart"
    const val VOICE_KOKORO_MALE = "am_adam"

    /** Piper en_US speaker names (female / male). */
    const val VOICE_PIPER_FEMALE = "lessac-medium"
    const val VOICE_PIPER_MALE = "ryan-medium"

    /** Spec: three playback rates, 1.0 default. */
    const val DEFAULT_RATE = 1.0f
    const val MIN_RATE = 0.85f
    const val MAX_RATE = 1.25f

    /** The three selectable rates, derived from the bounds so they cannot drift apart. */
    val RATES: List<Float> = listOf(MIN_RATE, DEFAULT_RATE, MAX_RATE)

    private const val PREFS_NAME = "mlbb_plus_voice_guide"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_ENGINE = "engine"
    private const val KEY_VOICE = "voice"
    private const val KEY_RATE = "rate"
    private const val KEY_AUTO_SPEAK = "auto_speak"
    private const val KEY_ANNOUNCE_SPELLS = "announce_enemy_spells"
    private const val KEY_MODEL = "installed_model"
    private const val KEY_NOTICE = "fallback_notice"

    private fun prefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Bumped on every USER-FACING write. The overlay service and the Settings
     * screen each own a [VoiceGuideManager], and both observe this through a
     * snapshotFlow, so flipping a switch in Settings reaches the in-game coach
     * immediately instead of at the next process start.
     *
     * Bookkeeping writes (installed-model pointer, one-shot notices) deliberately
     * do NOT bump it: the manager performs those itself, and echoing them back
     * would make it release its engine mid-sentence.
     */
    private val revisionState = mutableIntStateOf(0)

    /** Snapshot-observable counter of user-driven setting changes. */
    val revision: Int
        get() = revisionState.intValue

    private fun bump() {
        revisionState.intValue++
    }

    // ---------------------------------------------------------------
    // Master switch
    // ---------------------------------------------------------------

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
        bump()
    }

    // ---------------------------------------------------------------
    // Engine / voice / rate
    // ---------------------------------------------------------------

    fun engine(context: Context): String = prefs(context).getString(KEY_ENGINE, ENGINE_AUTO) ?: ENGINE_AUTO

    fun setEngine(context: Context, engine: String) {
        prefs(context).edit { putString(KEY_ENGINE, engine) }
        bump()
    }

    fun voice(context: Context): String = prefs(context).getString(KEY_VOICE, VOICE_KOKORO_FEMALE) ?: VOICE_KOKORO_FEMALE

    fun setVoice(context: Context, voice: String) {
        prefs(context).edit { putString(KEY_VOICE, voice) }
        bump()
    }

    fun rate(context: Context): Float = prefs(context).getFloat(KEY_RATE, DEFAULT_RATE).coerceIn(MIN_RATE, MAX_RATE)

    fun setRate(context: Context, rate: Float) {
        prefs(context).edit { putFloat(KEY_RATE, rate.coerceIn(MIN_RATE, MAX_RATE)) }
        bump()
    }

    /** Speak automatically whenever the guide advances to a new step. Default on,
     *  but only ever reached once [isEnabled] is true. */
    fun autoSpeak(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO_SPEAK, true)

    fun setAutoSpeak(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_AUTO_SPEAK, value) }
        bump()
    }

    /** Enemy battle-spell announcements. Spec: default OFF, max one per spell per
     *  cooldown cycle. */
    fun announceSpells(context: Context): Boolean = prefs(context).getBoolean(KEY_ANNOUNCE_SPELLS, false)

    fun setAnnounceSpells(context: Context, value: Boolean) {
        prefs(context).edit { putBoolean(KEY_ANNOUNCE_SPELLS, value) }
        bump()
    }

    // ---------------------------------------------------------------
    // Installed model bookkeeping
    // ---------------------------------------------------------------

    /** Id of the voice pack currently on disk, or "" when nothing is installed.
     *  See [VoiceModelInstaller.models] for the valid ids. */
    fun installedModel(context: Context): String = prefs(context).getString(KEY_MODEL, "") ?: ""

    fun setInstalledModel(context: Context, modelId: String) {
        prefs(context).edit { putString(KEY_MODEL, modelId) }
    }

    /**
     * One-shot user-facing notice, e.g. "Kokoro was missing, switched to Piper".
     * The overlay shows it once and clears it; nothing is persisted afterwards.
     */
    fun fallbackNotice(context: Context): String = prefs(context).getString(KEY_NOTICE, "") ?: ""

    fun setFallbackNotice(context: Context, notice: String) {
        prefs(context).edit { putString(KEY_NOTICE, notice) }
    }

    fun clearFallbackNotice(context: Context) {
        prefs(context).edit { remove(KEY_NOTICE) }
    }

    /** Wipes the model pointer (the files themselves go via [VoiceModelInstaller.delete]). */
    fun resetModel(context: Context) {
        prefs(context).edit { remove(KEY_MODEL) }
        bump()
    }
}
