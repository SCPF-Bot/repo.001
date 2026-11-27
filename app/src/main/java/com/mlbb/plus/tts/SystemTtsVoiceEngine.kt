package com.mlbb.plus.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * mlbb/tts/SystemTtsVoiceEngine.kt
 * GitHub path: app/src/main/java/com/mlbb/plus/tts/SystemTtsVoiceEngine.kt
 *
 * Zero-download engine: delegates to the platform [TextToSpeech] service.
 * It is the fallback when no sherpa-onnx voice pack is installed, and the
 * guaranteed baseline on devices where the AAR's native libs cannot load.
 *
 * Behaviour notes (see TTS_IMPLEMENTATION.md "Known limitations"):
 *  - [pause] / [resume] are NOT supported by the platform API. They are no-ops;
 *    the overlay's Pause therefore stops the line and Repeat speaks it again.
 *  - Some ROMs ship no English TTS data. [prepare] then returns false and
 *    [VoiceGuideManager] reports "no voice engine available" instead of failing
 *    silently.
 *  - Audio attributes are speech on the media usage so the manager's
 *    AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK request ducks the game rather than
 *    pausing it.
 */
class SystemTtsVoiceEngine(private val context: Context) : VoiceEngine {

    override val name: String
        get() = "System TTS"

    private val ttsRef = AtomicReference<TextToSpeech?>(null)
    private val initResult = AtomicReference<Boolean?>(null)
    private val ready = AtomicBoolean(false)
    private val active = AtomicReference<Utterance?>(null)
    private var rate = VoiceGuideSettings.DEFAULT_RATE
    private var resolvedLocale: Locale? = null

    override val isSpeaking: Boolean
        get() = active.get()?.finished?.get() == false

    private val initLock = Mutex()

    override suspend fun prepare(): Boolean = initLock.withLock {
        initResult.get()?.let { return@withLock it }
        val ok = init()
        initResult.set(ok)
        ready.set(ok)
        ok
    }

    /**
     * Binds the platform service. The constructor runs on the main thread and
     * `ttsRef` is assigned there too, so the init callback - also delivered on
     * the main looper - can never fire in between and see a null reference.
     */
    private suspend fun init(): Boolean {
        val status = withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine<Int> { cont ->
                try {
                    val tts = TextToSpeech(context.applicationContext) { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                    ttsRef.set(tts)
                } catch (e: Exception) {
                    // Missing framework TTS, restricted profile, or a dead service.
                    Log.w(TAG, "platform TextToSpeech unavailable", e)
                    if (cont.isActive) cont.resume(TextToSpeech.ERROR)
                }
            }
        }
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "platform TextToSpeech init failed with status $status")
            return false
        }
        return configure(ttsRef.get())
    }

    private fun configure(tts: TextToSpeech?): Boolean {
        if (tts == null) return false
        tts.setOnUtteranceProgressListener(progressListener)
        // Speech on the media usage: the focus request (owned by the manager)
        // ducks the game audio instead of pausing it.
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        tts.setSpeechRate(rate)
        resolvedLocale = pickLocale(tts) ?: return false
        return true
    }

    /** en-US first, then any English, then the device default if it is supported. */
    private fun pickLocale(tts: TextToSpeech): Locale? {
        val candidates = listOf(Locale.US, Locale.UK, Locale.ENGLISH, Locale.getDefault())
        for (candidate in candidates) {
            val result = tts.setLanguage(candidate)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                return candidate
            }
        }
        Log.w(TAG, "no usable locale for platform TTS")
        return null
    }

    override suspend fun speak(text: String, onDone: (() -> Unit)?) {
        val spoken = text.trim()
        if (spoken.isEmpty()) {
            onDone?.invoke()
            return
        }
        if (!ready.get() && !prepare()) {
            Log.w(TAG, "speak() called before the engine was ready")
            onDone?.invoke()
            return
        }
        val tts = ttsRef.get()
        if (tts == null) {
            onDone?.invoke()
            return
        }
        // Rapid Next/Prev must cancel the previous line, not queue behind it.
        stop()
        suspendCancellableCoroutine<Unit> { cont ->
            val utterance = Utterance(cont, onDone)
            active.set(utterance)
            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            val result = try {
                tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, params, utterance.id)
            } catch (e: Exception) {
                Log.e(TAG, "platform speak() threw", e)
                TextToSpeech.ERROR
            }
            if (result != TextToSpeech.SUCCESS) utterance.finish()
            cont.invokeOnCancellation { utterance.finish() }
        }
    }

    override fun stop() {
        active.getAndSet(null)?.finish()
        try {
            ttsRef.get()?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stop() failed", e)
        }
    }

    /** Not supported by android.speech.tts: documented no-op. */
    override fun pause() {
        Log.d(TAG, "pause() is not supported by the platform engine")
    }

    /** Not supported by android.speech.tts: documented no-op. */
    override fun resume() {
        Log.d(TAG, "resume() is not supported by the platform engine")
    }

    override fun setSpeechRate(newRate: Float) {
        rate = newRate.coerceIn(VoiceGuideSettings.MIN_RATE, VoiceGuideSettings.MAX_RATE)
        try {
            ttsRef.get()?.setSpeechRate(rate)
        } catch (e: Exception) {
            Log.w(TAG, "setSpeechRate() failed", e)
        }
    }

    override fun release() {
        stop()
        ready.set(false)
        initResult.set(null)
        resolvedLocale = null
        try {
            ttsRef.getAndSet(null)?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "shutdown() failed", e)
        }
    }

    /** True when the platform service bound and an English locale resolved. */
    fun isUsable(): Boolean = ready.get() && resolvedLocale != null

    /**
     * One utterance = one continuation + one onDone, both fired at most once.
     * The platform reports completion on a binder thread, cancellation comes
     * from the manager's dispatcher, so every path funnels through [finish].
     */
    private class Utterance(
        private val continuation: CancellableContinuation<Unit>,
        private val onDone: (() -> Unit)?
    ) {
        val id: String = "mlbb-vc-" + counter.incrementAndGet()
        val finished = AtomicBoolean(false)

        fun finish() {
            if (!finished.compareAndSet(false, true)) return
            onDone?.invoke()
            if (continuation.isActive) continuation.resume(Unit)
        }

        companion object {
            private val counter = AtomicLong(0)
        }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            active.get()?.takeIf { it.id == utteranceId }?.finish()
        }

        @Deprecated("Required override; superseded by onStop(utteranceId, interrupted).")
        override fun onError(utteranceId: String?) {
            Log.w(TAG, "platform TTS error for $utteranceId")
            active.get()?.takeIf { it.id == utteranceId }?.finish()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.w(TAG, "platform TTS error $errorCode for $utteranceId")
            active.get()?.takeIf { it.id == utteranceId }?.finish()
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            active.get()?.takeIf { it.id == utteranceId }?.finish()
        }
    }

    companion object {
        private const val TAG = "SystemTtsEngine"
    }
}
