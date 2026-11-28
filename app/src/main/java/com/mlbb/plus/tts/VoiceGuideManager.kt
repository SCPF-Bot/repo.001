package com.mlbb.plus.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * The single owner of "what is being spoken right now". The overlay widget and
 * [com.mlbb.plus.ui.WidgetOverlayService] only ever talk to this class plus
 * [VoiceUiState]; they never see an engine, an AudioTrack or a model install.
 *
 * Rules this class enforces (feature spec §3, §4, §6):
 *  - Nothing happens until Voice Guide is on. The overlay's Speaker tap is the
 *    consent moment; the overlay never speaks on launch.
 *  - Auto-speak fires once per step change, keyed on [SpokenStep.key], so a
 *    recomposition or a 1 Hz timer tick can never repeat a line.
 *  - Rapid Next/Prev cancels the previous utterance instead of queueing.
 *  - Audio focus is `GAIN_TRANSIENT_MAY_DUCK`: the game is ducked, not paused,
 *    and focus is handed back the moment the line finishes.
 *  - A phone call (AUDIOFOCUS_LOSS / LOSS_TRANSIENT) stops the coach at once.
 *  - TalkBack wins: touch exploration pauses Voice Guide and shows a hint, so
 *    two voices never talk over each other.
 *  - The neural engine is released after 30 s of silence (Kokoro holds roughly
 *    200 MB of native memory) and reloaded on the next line.
 *
 * Threading: every public method is called from the main thread (Compose
 * callbacks and the service). The scope uses [Dispatchers.Main.immediate];
 * engines and the installer move their own heavy work to IO.
 */
class VoiceGuideManager(private val context: Context) {

    /** Everything the UI needs, in one observable snapshot. */
    data class VoiceUiState(
        val enabled: Boolean = false,
        val phase: Phase = Phase.IDLE,
        val progress: Float = 0f,
        val progressLabel: String = "",
        val engineLabel: String = "",
        val message: String = "",
        val spokenText: String = "",
        val speakingKey: String = "",
        val talkBackOn: Boolean = false,
        val modelInstalled: Boolean = false,
        val modelLabel: String = "",
        val supportsPause: Boolean = false,
        val installing: Boolean = false
    )

    enum class Phase { IDLE, PREPARING, DOWNLOADING, SPEAKING, PAUSED, ERROR, UNAVAILABLE }

    var state by mutableStateOf(VoiceUiState())
        private set

    val isSpeaking: Boolean get() = state.phase == Phase.SPEAKING

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val installer = VoiceModelInstaller(context)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val accessibility = context.getSystemService(AccessibilityManager::class.java)

    private var sherpa: SherpaOnnxVoiceEngine? = null
    private var system: SystemTtsVoiceEngine? = null
    private var active: VoiceEngine? = null
    private var speakJob: Job? = null
    private var idleJob: Job? = null
    private var installJob: Job? = null
    private var focusHeld = false

    /** True when the neural voice failed for the line in hand and the system voice is speaking instead. */
    private var neuralFellBack = false
    private var lastSpokenKey: String = ""
    private var lastStep: SpokenStep? = null
    private val spellCooldowns = HashMap<String, Long>()

    /** Declared before [init] on purpose: init registers it. */
    private val talkBackListener = object : AccessibilityManager.TouchExplorationStateChangeListener {
        override fun onTouchExplorationStateChanged(enabled: Boolean) {
            if (enabled) {
                stop()
                state = state.copy(talkBackOn = true, message = TALKBACK_HINT)
            } else {
                state = state.copy(talkBackOn = false, message = "")
            }
        }
    }

    init {
        refreshFromPrefs()
        accessibility?.addTouchExplorationStateChangeListener(talkBackListener, mainHandler)
        if (talkBackActive()) state = state.copy(talkBackOn = true, message = TALKBACK_HINT)
        // Follow user-driven setting changes made by the OTHER manager instance
        // (the Settings screen has its own), so the in-game coach picks up a new
        // voice, rate or engine without a restart. drop(1) skips the initial
        // snapshot: refreshFromPrefs() above already handled it.
        scope.launch {
            snapshotFlow { VoiceGuideSettings.revision }
                .drop(1)
                .distinctUntilChanged()
                .collect { onSettingsChanged() }
        }
    }

    // ---------------------------------------------------------------
    // Public API used by the overlay widget
    // ---------------------------------------------------------------

    /**
     * Auto-speak hook: call whenever the guide advances. [SpokenStep.key] makes
     * it idempotent, so recompositions and timer ticks are ignored.
     */
    fun onStepAdvanced(step: SpokenStep) {
        if (!canSpeak()) return
        val proceed = VoiceGuideTransitions.shouldAutoSpeak(
            autoSpeakEnabled = VoiceGuideSettings.autoSpeak(context),
            newKey = step.key,
            lastKey = lastSpokenKey,
            fragmentsChanged = step.fragments != lastStep?.fragments
        )
        lastStep = step
        if (!proceed) return
        speakInternal(step, forceRepeat = false)
    }

    /**
     * Registers the step the overlay is showing without speaking it, so the
     * notification's Play action always has something current to say. The widget
     * calls this whenever the guide advances.
     */
    fun setCurrentStep(step: SpokenStep?) {
        lastStep = step
    }

    /**
     * Notification Play/Pause. The service owns no guide state, so this drives
     * whatever the overlay last registered.
     */
    fun toggleFromNotification() {
        when (state.phase) {
            Phase.SPEAKING -> pause()

            Phase.PAUSED -> resume()

            else -> {
                val step = lastStep
                if (step == null) {
                    state = state.copy(message = "Open the overlay first: there is no step to read yet.")
                } else {
                    speakInternal(step, forceRepeat = true)
                }
            }
        }
    }

    /** Notification Stop Voice: silence the coach, keep the overlay running. */
    fun stopFromNotification() = stop()

    /** Speaker tap: speak [step] now, downloading the voice pack first if it is missing. */
    fun speak(step: SpokenStep) {
        lastStep = step
        speakInternal(step, forceRepeat = true)
    }

    /** Next / Prev: speak a neighbouring step without touching the game timer. */
    fun speakRelative(steps: List<SpokenStep>, currentIndex: Int, delta: Int) {
        if (steps.isEmpty()) return
        val target = (currentIndex + delta).coerceIn(0, steps.lastIndex)
        lastStep = steps[target]
        speakInternal(steps[target], forceRepeat = true)
    }

    /** Repeat the last line. Also restarts it after Pause on engines without one. */
    fun repeat() {
        val step = lastStep ?: return
        speakInternal(step, forceRepeat = true)
    }

    fun pause() {
        if (state.phase != Phase.SPEAKING) return
        val engine = active ?: return
        if (engine.supportsPause) {
            engine.pause()
            state = state.copy(phase = Phase.PAUSED, message = "")
        } else {
            // android.speech.tts has no pause: stop, keep the line, let Repeat
            // say it again. The widget words the button accordingly.
            engine.stop()
            state = state.copy(phase = Phase.PAUSED, message = PAUSE_FALLBACK_HINT)
        }
    }

    fun resume() {
        if (state.phase != Phase.PAUSED) return
        val engine = active
        if (engine != null && engine.supportsPause) {
            engine.resume()
            state = state.copy(phase = Phase.SPEAKING, message = "")
        } else {
            repeat()
        }
    }

    /**
     * Stop speaking and hand audio focus straight back to the game.
     *
     * Deliberately does NOT touch an in-flight voice-pack install: audio focus loss
     * (an incoming call), TalkBack and the settings toggle all call stop(), and none
     * of them should throw away a 20-100 MB download. Only [cancelInstall] does that.
     */
    fun stop() {
        speakJob?.cancel()
        speakJob = null
        active?.stop()
        abandonFocus()
        state = state.copy(
            phase = Phase.IDLE,
            progress = 0f,
            progressLabel = "",
            speakingKey = "",
            message = ""
        )
        scheduleIdleRelease()
    }

    /** Master switch, also driven from Settings. */
    fun setEnabled(enabled: Boolean) {
        VoiceGuideSettings.setEnabled(context, enabled)
        if (enabled) {
            state = state.copy(enabled = true, message = "")
            // Turning Voice Guide on downloads the selected neural voice (once). The platform
            // voice speaks until the download finishes.
            startInstallIfNeeded()
        } else {
            stop()
            releaseEngines()
            state = state.copy(enabled = false, engineLabel = "", spokenText = "")
        }
    }

    fun enable() = setEnabled(true)

    fun setRate(rate: Float) {
        VoiceGuideSettings.setRate(context, rate)
        active?.setSpeechRate(rate)
    }

    /** Voice / engine / auto-speak changed in Settings: reload on the next line. */
    fun onSettingsChanged() {
        refreshFromPrefs()
        releaseEngines()
        lastSpokenKey = ""
    }

    /**
     * Enemy battle-spell announcement: off by default, and at most once per
     * spell per cooldown cycle.
     */
    fun announceSpell(spellName: String, cooldownSeconds: Int) {
        if (!canSpeak() || !VoiceGuideSettings.announceSpells(context)) return
        val now = System.currentTimeMillis()
        val window = cooldownSeconds.coerceAtLeast(1) * MILLIS_PER_SECOND
        if (now - (spellCooldowns[spellName] ?: 0L) < window) return
        spellCooldowns[spellName] = now
        interruptFor(TtsTextNormalizer.spoken(listOf("Enemy $spellName used"), SPELL_WORDS), "spell:$spellName")
    }

    /** Test line for the Settings screen. */
    fun playTestLine() {
        if (!state.enabled) setEnabled(true)
        interruptFor(
            TtsTextNormalizer.spoken(
                listOf("Voice Guide is ready", "Turtle spawns at two minutes, Lord at eight"),
                TtsTextNormalizer.MAX_WORDS
            ),
            key = KEY_TEST
        )
    }

    /** Downloads and unpacks the pack for the selected voice, reporting progress into [state]. */
    fun installSelectedModel(onFinished: ((Boolean) -> Unit)? = null) {
        val model = installer.forVoice(VoiceGuideSettings.voice(context))
        if (model == null) {
            state = state.copy(message = "The system voice needs no voice pack.")
            onFinished?.invoke(true)
            return
        }
        if (installer.isInstalled(model)) {
            refreshFromPrefs()
            onFinished?.invoke(true)
            return
        }
        installJob?.cancel()
        installer.resetCancel()
        state = state.copy(installing = true)
        installJob = scope.launch {
            var result: VoiceModelInstaller.Progress = VoiceModelInstaller.Progress.Cancelled
            try {
                result = installer.install(model) { publish(it) }
            } finally {
                // Runs on cancellation too (install() now rethrows it), so the spinner
                // always clears even when the job is replaced or the manager is released.
                val ok = result is VoiceModelInstaller.Progress.Done
                refreshFromPrefs()
                state = state.copy(
                    installing = false,
                    message = when {
                        ok -> "${model.label} installed."
                        result is VoiceModelInstaller.Progress.Cancelled -> "Install cancelled."
                        else -> state.message
                    }
                )
                if (ok) releaseEngines() // pick up the new pack on the next line
                onFinished?.invoke(ok)
            }
        }
    }

    fun cancelInstall() {
        installer.cancel()
    }

    /** Frees an installed pack; the next line falls back down the chain. */
    fun deleteModel(model: VoiceModel, onFinished: (() -> Unit)? = null) {
        scope.launch {
            stop()
            releaseEngines()
            installer.delete(model)
            if (VoiceGuideSettings.installedModel(context) == model.id) {
                VoiceGuideSettings.resetModel(context)
            }
            refreshFromPrefs()
            val remaining = installer.models.firstOrNull { installer.isInstalled(it) }
            state = state.copy(
                message = if (remaining != null) {
                    "${model.label} removed. Falling back to ${remaining.label}."
                } else {
                    "${model.label} removed. Voice Guide now uses the system voice."
                }
            )
            onFinished?.invoke()
        }
    }

    /** Shows the "switched to X" notice once, then clears it. */
    fun consumeNotice() {
        val notice = VoiceGuideSettings.fallbackNotice(context)
        if (notice.isNotEmpty()) {
            VoiceGuideSettings.clearFallbackNotice(context)
            state = state.copy(message = notice)
        }
    }

    /** Service onDestroy. */
    fun release() {
        stop()
        try {
            accessibility?.removeTouchExplorationStateChangeListener(talkBackListener)
        } catch (t: Throwable) {
            Log.w(TAG, "could not unregister the TalkBack listener", t)
        }
        releaseEngines()
        idleJob?.cancel()
        installJob?.cancel()
        scope.cancel()
    }

    // ---------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------

    private fun canSpeak(): Boolean {
        if (!state.enabled) return false
        if (state.talkBackOn) {
            if (state.message != TALKBACK_HINT) state = state.copy(message = TALKBACK_HINT)
            return false
        }
        return true
    }

    private fun speakInternal(step: SpokenStep, forceRepeat: Boolean) {
        // TalkBack always wins, even for an explicit tap.
        if (state.talkBackOn) {
            state = state.copy(message = TALKBACK_HINT)
            return
        }
        if (!state.enabled) {
            // First Speaker tap is the consent moment: turn the feature on.
            VoiceGuideSettings.setEnabled(context, true)
            state = state.copy(enabled = true)
        }
        val text = compose(step)
        if (text.isBlank()) {
            state = state.copy(message = "Nothing to say for this step yet.")
            return
        }
        if (!forceRepeat && step.key == lastSpokenKey && state.phase == Phase.SPEAKING) return
        lastSpokenKey = step.key
        interruptFor(text, step.key)
    }

    /** Game-clock prefix, then the fragments condensed into the word budget. */
    private fun compose(step: SpokenStep): String = VoiceGuideTransitions.spokenLine(step.fragments, step.startSeconds)

    /** Cancels anything in flight and speaks [text] on the best available engine. */
    private fun interruptFor(text: String, key: String) {
        speakJob?.cancel()
        idleJob?.cancel()
        idleJob = null
        active?.stop()
        speakJob = scope.launch {
            startInstallIfNeeded()
            val engine = resolveEngine()
            if (engine == null) {
                abandonFocus()
                // Say why, in the engine's own words when it has them, instead of a generic line.
                val reason = system?.lastError ?: sherpa?.lastError
                state = state.copy(
                    phase = Phase.UNAVAILABLE,
                    speakingKey = "",
                    progressLabel = "",
                    message = if (reason != null) "$UNAVAILABLE_MESSAGE $reason" else UNAVAILABLE_MESSAGE
                )
                scheduleIdleRelease()
                return@launch
            }
            active = engine
            if (!requestFocus()) {
                // Visible in the overlay status line and in Settings; the engine stays loaded
                // for the idle timer to release, so a retry does not reload the model.
                Log.w(TAG, "speech skipped: audio focus denied for $key")
                state = state.copy(phase = Phase.ERROR, speakingKey = "", message = FOCUS_DENIED_MESSAGE)
                scheduleIdleRelease()
                return@launch
            }
            engine.setSpeechRate(VoiceGuideSettings.rate(context))
            // Keep a notice that explains a fallback (neural voice failed, system voice speaks).
            val fellBack = engine is SystemTtsVoiceEngine && neuralFellBack
            val keepMessage = if (installJob?.isActive == true || fellBack) state.message else ""
            state = state.copy(
                phase = Phase.SPEAKING,
                spokenText = text,
                speakingKey = key,
                engineLabel = engine.name,
                supportsPause = engine.supportsPause,
                message = keepMessage
            )
            engine.speak(text, null)
            // speak() returns once the line finished, was stopped, or failed.
            val failure = engine.lastError
            if (failure != null && state.speakingKey == key) {
                // A failed line must say so: silence with no message reads as a broken app.
                state = state.copy(phase = Phase.ERROR, speakingKey = "", message = failure)
            } else if (state.speakingKey == key && state.phase == Phase.SPEAKING) {
                state = state.copy(phase = Phase.IDLE, speakingKey = "")
            }
            abandonFocus()
            scheduleIdleRelease()
        }
    }

    /**
     * Starts the download of the selected neural voice when it is missing. Runs when Voice
     * Guide is switched on and again on the first speak, so a failed download is retried.
     * Speech is instant either way: the platform voice carries the lines until the neural
     * pack is ready.
     */
    private fun startInstallIfNeeded() {
        if (VoiceGuideSettings.engine(context) == VoiceGuideSettings.ENGINE_SYSTEM) return
        if (installJob?.isActive == true) return
        val model = installer.forVoice(VoiceGuideSettings.voice(context)) ?: return
        if (installer.isInstalled(model)) return
        state = state.copy(message = "Downloading ${model.label} (${model.sizeLabel}).")
        installSelectedModel()
    }

    /**
     * AUTO prefers sherpa-onnx when a pack is installed and falls back to the
     * platform voice otherwise; an explicit choice is honoured even when it
     * cannot be satisfied, so the player learns why nothing was said.
     */
    private suspend fun resolveEngine(): VoiceEngine? {
        val preference = VoiceGuideSettings.engine(context)
        neuralFellBack = false
        if (preference == VoiceGuideSettings.ENGINE_SYSTEM) return systemEngine()

        val installed = installer.models.firstOrNull { installer.isInstalled(it) }
        if (installed != null) {
            state = state.copy(phase = Phase.PREPARING, progressLabel = "Loading voice model")
            val engine = sherpaEngine()
            if (engine != null && engine.prepare()) {
                consumeNotice()
                return engine
            }
            neuralFellBack = true
            state = state.copy(
                message = sherpa?.lastError?.let { "$it Using the system voice." } ?: MODEL_LOAD_FAILED_MESSAGE
            )
            if (preference == VoiceGuideSettings.ENGINE_SHERPA) return null
        } else if (preference == VoiceGuideSettings.ENGINE_SHERPA) {
            state = state.copy(message = NO_MODEL_MESSAGE)
            return null
        }
        return systemEngine()
    }

    private fun sherpaEngine(): SherpaOnnxVoiceEngine? {
        sherpa?.let { return it }
        // Guarded: without the sherpa-onnx AAR, or on an ABI it does not ship,
        // even resolving this class throws.
        return try {
            SherpaOnnxVoiceEngine(context, installer).also { sherpa = it }
        } catch (t: Throwable) {
            Log.e(TAG, "sherpa-onnx engine unavailable", t)
            null
        }
    }

    private suspend fun systemEngine(): SystemTtsVoiceEngine? {
        val engine = system ?: SystemTtsVoiceEngine(context).also { system = it }
        state = state.copy(phase = Phase.PREPARING, progressLabel = "Starting system voice")
        if (!engine.prepare()) {
            Log.w(TAG, "platform TTS is not usable on this device: ${engine.lastError}")
            return null
        }
        return engine
    }

    private fun releaseEngines() {
        try {
            sherpa?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "sherpa release failed", t)
        }
        try {
            system?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "system release failed", t)
        }
        sherpa = null
        system = null
        active = null
    }

    // ---------------------------------------------------------------
    // Audio focus: duck the game, never pause it
    // ---------------------------------------------------------------

    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(speechAttributes)
            .setOnAudioFocusChangeListener(focusListener, mainHandler)
            // We keep talking while other audio ducks; we are not the ones
            // pausing, so do not advertise that we would.
            .setWillPauseWhenDucked(false)
            .build()
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            // Incoming call, alarm, or another app taking the stream.
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.i(TAG, "audio focus lost ($change), stopping the coach")
                stop()
            }

            else -> Unit
        }
    }

    private fun requestFocus(): Boolean {
        val am = audioManager ?: return false
        return try {
            val granted = am.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            focusHeld = granted
            if (!granted) Log.w(TAG, "audio focus was not granted")
            granted
        } catch (t: Throwable) {
            Log.e(TAG, "requestAudioFocus failed", t)
            false
        }
    }

    private fun abandonFocus() {
        if (!focusHeld) return
        focusHeld = false
        try {
            audioManager?.abandonAudioFocusRequest(focusRequest)
        } catch (t: Throwable) {
            Log.w(TAG, "abandonAudioFocus failed", t)
        }
    }

    // ---------------------------------------------------------------
    // Idle release
    // ---------------------------------------------------------------

    private fun scheduleIdleRelease() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_RELEASE_MS)
            if (state.phase != Phase.SPEAKING && state.phase != Phase.PAUSED) {
                Log.i(TAG, "idle ${IDLE_RELEASE_MS / MILLIS_PER_SECOND}s, releasing the voice engine")
                releaseEngines()
            }
        }
    }

    // ---------------------------------------------------------------
    // TalkBack
    // ---------------------------------------------------------------

    private fun talkBackActive(): Boolean = accessibility?.let { it.isEnabled && it.isTouchExplorationEnabled } ?: false

    // ---------------------------------------------------------------
    // Prefs / progress
    // ---------------------------------------------------------------

    private fun refreshFromPrefs() {
        val model = installer.forVoice(VoiceGuideSettings.voice(context))
        val installed = model != null && installer.isInstalled(model)
        val anyInstalled = installer.models.firstOrNull { installer.isInstalled(it) }
        state = state.copy(
            enabled = VoiceGuideSettings.isEnabled(context),
            talkBackOn = talkBackActive(),
            modelInstalled = installed || anyInstalled != null,
            modelLabel = when {
                installed -> model.label
                anyInstalled != null -> anyInstalled.label
                else -> "Not installed"
            },
            supportsPause = active?.supportsPause ?: false
        )
        if (installed) VoiceGuideSettings.setInstalledModel(context, model.id)
    }

    private fun publish(progress: VoiceModelInstaller.Progress) {
        when (progress) {
            is VoiceModelInstaller.Progress.Downloading -> state = state.copy(
                phase = Phase.DOWNLOADING,
                progress = ratio(progress.received, progress.total),
                progressLabel = "Downloading ${progress.received / MB} of ${progress.total / MB} MB"
            )

            VoiceModelInstaller.Progress.Verifying -> state = state.copy(progressLabel = "Checking files")

            is VoiceModelInstaller.Progress.Done -> state = state.copy(
                phase = Phase.IDLE,
                progress = 1f,
                progressLabel = ""
            )

            is VoiceModelInstaller.Progress.Failed -> state = state.copy(
                phase = Phase.ERROR,
                progress = 0f,
                installing = false,
                message = progress.message
            )

            VoiceModelInstaller.Progress.Cancelled -> state = state.copy(
                phase = Phase.IDLE,
                progress = 0f,
                progressLabel = "",
                installing = false
            )

            VoiceModelInstaller.Progress.Idle -> Unit
        }
    }

    private fun ratio(done: Long, total: Long): Float = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f

    companion object {
        private const val TAG = "VoiceGuideManager"

        /** Spec: release the engine after 30 s of silence. */
        const val IDLE_RELEASE_MS = 30_000L

        /** Spell announcements stay shorter than a guide line. */
        private const val SPELL_WORDS = 8

        private const val MB = 1_000_000L
        private const val MILLIS_PER_SECOND = 1000L
        private const val KEY_TEST = "voice-test"

        const val TALKBACK_HINT = "Voice Guide is paused while TalkBack is on."
        const val PAUSE_FALLBACK_HINT = "Paused. Repeat plays this step again."
        const val UNAVAILABLE_MESSAGE =
            "No voice engine available. Install a voice pack in Settings, or enable a system text-to-speech engine in Android settings."
        const val MODEL_LOAD_FAILED_MESSAGE = "The voice model could not load. Using the system voice."
        const val NO_MODEL_MESSAGE = "No voice pack installed yet."
        const val FOCUS_DENIED_MESSAGE = "Android refused audio focus, so nothing was spoken."
    }
}

/**
 * One guide step reduced to what the coach may say.
 *
 * The widget builds this from its own file-private step model, so the tts
 * package never depends on overlay internals. [key] must be stable for a given
 * step: it is what makes auto-speak fire exactly once per step change.
 * [fragments] are ordered by importance - phase, callout, then the instruction -
 * and [TtsTextNormalizer] keeps as many as fit the 8-18 word budget.
 */
data class SpokenStep(
    val key: String,
    val startSeconds: Int,
    val fragments: List<String>
)

/**
 * The coach's decision table, extracted from [VoiceGuideManager] so it can be
 * unit-tested on the JVM with no Context, no engine and no coroutines.
 *
 * Everything here is pure: same inputs, same answer, no side effects. The
 * manager and the overlay widget both call it, which is what keeps the
 * notification label, the on-screen button label and the auto-speak gate in
 * agreement.
 */
object VoiceGuideTransitions {

    /**
     * Builds the line the coach says: an optional game-clock prefix plus as many
     * fragments as fit the remaining word budget.
     *
     * `startSeconds = 0` produces no prefix ("At now" is not a sentence), and the
     * budget is reduced by the prefix length so the whole line still lands in the
     * 8-18 word window from the feature spec.
     */
    fun spokenLine(fragments: List<String>, startSeconds: Int): String {
        val prefix = if (startSeconds > 0) {
            "At " + TtsTextNormalizer.spokenTime(startSeconds)
        } else {
            ""
        }
        val budget = (TtsTextNormalizer.MAX_WORDS - TtsTextNormalizer.countWords(prefix))
            .coerceAtLeast(TtsTextNormalizer.MIN_WORDS)
        val body = TtsTextNormalizer.spoken(fragments, budget)
        return when {
            body.isBlank() -> prefix
            prefix.isBlank() -> body
            else -> "$prefix. $body"
        }
    }

    /**
     * Auto-speak fires once per step change and never while the player has asked
     * for silence. A repeated key with unchanged fragments means the guide has
     * not actually moved (a recomposition or a 1 Hz clock tick), so it is
     * ignored; the same key with different fragments means the plan or role
     * changed underneath us, which IS worth saying.
     */
    fun shouldAutoSpeak(
        autoSpeakEnabled: Boolean,
        newKey: String,
        lastKey: String,
        fragmentsChanged: Boolean
    ): Boolean {
        if (!autoSpeakEnabled) return false
        if (newKey.isBlank()) return false
        if (newKey != lastKey) return true
        return fragmentsChanged
    }

    /** Label for the overlay's primary voice button. */
    fun primaryLabel(
        phase: VoiceGuideManager.Phase,
        downloading: Boolean,
        preparing: Boolean
    ): String = when {
        downloading -> "Cancel"
        preparing -> "Loading"
        phase == VoiceGuideManager.Phase.SPEAKING -> "Pause"
        phase == VoiceGuideManager.Phase.PAUSED -> "Resume"
        else -> "Speak"
    }

    /** What tapping that button does. */
    fun primaryAction(
        phase: VoiceGuideManager.Phase,
        downloading: Boolean,
        preparing: Boolean
    ): PrimaryAction = when {
        downloading -> PrimaryAction.CANCEL_DOWNLOAD
        preparing -> PrimaryAction.NONE
        phase == VoiceGuideManager.Phase.SPEAKING -> PrimaryAction.PAUSE
        phase == VoiceGuideManager.Phase.PAUSED -> PrimaryAction.RESUME
        else -> PrimaryAction.SPEAK
    }

    /** Primary-button outcomes, so the widget never re-derives them. */
    enum class PrimaryAction { SPEAK, PAUSE, RESUME, CANCEL_DOWNLOAD, NONE }

    /**
     * Phase after a Pause tap. Engines without a real pause (`android.speech.tts`)
     * still report PAUSED - the difference is only that Repeat restarts the line
     * instead of continuing it, which the UI says in a hint.
     */
    fun phaseAfterPause(current: VoiceGuideManager.Phase): VoiceGuideManager.Phase = if (current == VoiceGuideManager.Phase.SPEAKING) VoiceGuideManager.Phase.PAUSED else current

    /** Phase after a Resume tap. */
    fun phaseAfterResume(current: VoiceGuideManager.Phase): VoiceGuideManager.Phase = if (current == VoiceGuideManager.Phase.PAUSED) VoiceGuideManager.Phase.SPEAKING else current
}
