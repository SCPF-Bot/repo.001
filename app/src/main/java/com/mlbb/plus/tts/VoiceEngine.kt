package com.mlbb.plus.tts

/**
 * The only speech API the overlay and [WidgetOverlayService] ever touch. Two
 * implementations exist:
 *
 *  - [SherpaOnnxVoiceEngine]: on-device neural TTS (Kokoro first, Piper as the
 *    lighter fallback). Voice packs are downloaded once, when Voice Guide is turned on, and then work offline.
 *  - [SystemTtsVoiceEngine]: the platform `android.speech.tts.TextToSpeech`
 *    service. Zero download, works out of the box, lower quality and depends on
 *    whatever TTS engine the device ships (some Chinese-market ROMs ship none).
 *
 * [VoiceGuideManager] picks between them and owns the queue, so swapping or
 * adding an engine never means touching UI code.
 *
 * Threading contract: `speak` is called from the manager's single-threaded
 * dispatcher. Implementations must suspend until the utterance finishes or is
 * cancelled, must return promptly from [stop]/[pause]/[resume], and must be safe
 * to call [release] from any thread.
 */
interface VoiceEngine {

    /** Human-readable engine label for Settings and logs ("Kokoro", "System TTS"). */
    val name: String

    /** True while an utterance is being synthesised or played. */
    val isSpeaking: Boolean

    /**
     * Whether [pause]/[resume] really suspend mid-utterance. sherpa-onnx does
     * (we own the AudioTrack); `android.speech.tts` has no pause API, so the
     * manager degrades Pause to "stop and keep the line" and lets Repeat say it
     * again. The widget uses this to word its controls honestly.
     */
    val supportsPause: Boolean
        get() = false

    /**
     * Loads whatever the engine needs (native libs, model files, the platform
     * TTS service binding). Returns false when this engine cannot be used on
     * this device right now, so the manager can fall back instead of failing
     * silently. Cheap to call repeatedly; implementations cache the result.
     */
    suspend fun prepare(): Boolean

    /**
     * Speaks [text] and suspends until playback ends, is stopped, or fails.
     * [onDone] runs exactly once on the main thread when the utterance finishes
     * naturally or is cancelled, which lets the manager advance its queue.
     */
    suspend fun speak(text: String, onDone: (() -> Unit)? = null)

    /** Cancels the current utterance immediately and drops anything queued inside the engine. */
    fun stop()

    /** Pauses mid-utterance where the engine supports it; a no-op otherwise. */
    fun pause()

    /** Resumes after [pause]; a no-op when not paused. */
    fun resume()

    /**
     * Playback rate multiplier. Clamped by the implementation to
     * [VoiceGuideSettings.MIN_RATE]..[VoiceGuideSettings.MAX_RATE].
     * For sherpa-onnx this becomes `1 / lengthScale` (Kokoro) or `speed`
     * (Piper) at generate time; for the platform engine it maps to
     * `TextToSpeech.setSpeechRate`.
     */
    fun setSpeechRate(newRate: Float)

    /** Frees native memory / unbinds the service. The manager calls this after
     *  [VoiceGuideManager.IDLE_RELEASE_MS] of silence and on service destroy. */
    fun release()
}
