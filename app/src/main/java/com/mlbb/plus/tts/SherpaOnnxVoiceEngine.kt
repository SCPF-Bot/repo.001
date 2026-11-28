package com.mlbb.plus.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.withLock as withThreadLock

/**
 * On-device neural TTS through sherpa-onnx. Kokoro is the primary voice, Piper
 * the lighter fallback, exactly as the feature spec asks.
 *
 * Everything runs offline: the model files are downloaded by
 * [VoiceModelInstaller] (`Android/data/<app>/files/tts/`), synthesis happens on a background
 * thread, and audio goes out through a streaming [AudioTrack] so the first
 * sentence starts playing while the rest is still being generated.
 *
 * Native lifetime: [OfflineTts] and [AudioTrack] are freed only while holding
 * [genLock], the same lock held for the whole of a generation. A release that
 * lands mid-sentence first asks the generator to stop, then waits for it, so a
 * native handle is never freed while sherpa-onnx is still using it.
 *
 * Version pin: sherpa-onnx **1.13.8**. 2.0.x dropped the built-in espeak-ng
 * frontend, which the Piper VITS packs depend on ("no lexicon, and no frontend
 * available"), and Kokoro then needs a separate `lexicon-en-us.txt`. Both packs
 * were verified to synthesise on 1.13.8 from their self-contained archives.
 *
 * Memory: Kokoro keeps roughly 200 MB of native state while loaded, so
 * [VoiceGuideManager] calls [release] after 30 s of silence. `prepare` reloads
 * on demand, which costs about 1-2 s the first time.
 */
class SherpaOnnxVoiceEngine(
    private val context: Context,
    private val installer: VoiceModelInstaller
) : VoiceEngine {

    override val name: String
        get() = loadedModel?.let { "sherpa-onnx \u00b7 ${it.label}" } ?: "sherpa-onnx"

    override val isSpeaking: Boolean
        get() = speaking.get() && !stopRequested.get()

    /** We own the AudioTrack, so a real mid-utterance pause is possible. */
    override val supportsPause: Boolean
        get() = true

    /** The pack currently loaded, for Settings copy and logs. Null before [prepare]. */
    @Volatile
    var loadedModel: VoiceModel? = null
        private set

    /** The speaker id resolved for the configured voice. */
    @Volatile
    var loadedSid: Int = 0
        private set

    /** Sample rate reported by the model (24000 Kokoro, 22050 Piper). */
    @Volatile
    var loadedSampleRate: Int = 0
        private set

    @Volatile
    private var tts: OfflineTts? = null

    @Volatile
    private var track: AudioTrack? = null

    /** Guards the engine/track handover so [release] cannot race [load]. */
    private val stateLock = Any()

    /**
     * Held for the whole of one generation and for the native teardown. Lets a
     * release wait for an in-flight sentence before it frees the native handles.
     */
    private val genLock = ReentrantLock()
    private val speaking = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val loadLock = Mutex()

    /**
     * Model loads run here, not inside [prepare]'s coroutine: a native load cannot be
     * interrupted, so the timeout only stops us waiting for it.
     */
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Bumped whenever a load is abandoned. A load that lands after its generation has
     * been superseded releases what it built instead of publishing it.
     */
    private val loadGeneration = AtomicInteger(0)

    @Volatile
    private var failure: String? = null

    override val lastError: String?
        get() = failure
    @Volatile
    private var rate = VoiceGuideSettings.DEFAULT_RATE

    /**
     * Loads the best available pack: the one matching the configured voice,
     * otherwise the first installed pack in the Kokoro -> Piper fallback chain.
     * Returns false when nothing is installed or the native libs cannot load,
     * which is the manager's cue to fall back to [SystemTtsVoiceEngine].
     */
    override suspend fun prepare(): Boolean = loadLock.withLock {
        if (tts != null && track != null) return@withLock true
        failure = null
        val wanted = installer.forVoice(VoiceGuideSettings.voice(context))
        val chain = if (wanted != null) installer.fallbackChain(wanted) else installer.models
        val model = chain.firstOrNull { installer.isInstalled(it) }
        if (model == null) {
            Log.i(TAG, "no voice pack installed yet")
            return@withLock false
        }
        if (wanted != null && model != wanted) {
            val notice = "${wanted.label} was unavailable, switched to ${model.label}."
            VoiceGuideSettings.setFallbackNotice(context, notice)
            Log.w(TAG, notice)
        }
        val generation = loadGeneration.incrementAndGet()
        val loading = loadScope.async { load(model, generation) }
        val loaded: Boolean? = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
            try {
                loading.await()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Log.e(TAG, "${model.id}: load threw", t)
                false
            }
        }
        if (loaded == true) return@withLock true
        // Abandon this generation: a load still running natively will release itself.
        loadGeneration.incrementAndGet()
        failure = if (loaded == null) {
            "The ${model.label} voice took too long to load."
        } else {
            "The ${model.label} voice could not load on this device."
        }
        Log.e(TAG, "${model.id}: $failure")
        false
    }

    private suspend fun load(model: VoiceModel, generation: Int): Boolean = withContext(Dispatchers.IO) {
        releaseInternal()
        val dir = installer.modelDir(model)
        val onnx = model.resolveModelFile(dir)
        if (onnx == null) {
            Log.e(TAG, "${model.id}: no .onnx graph found in $dir")
            return@withContext false
        }
        val tokens = File(dir, model.tokensFile).absolutePath
        val dataDir = File(dir, model.espeakDir).absolutePath
        val cores = Runtime.getRuntime().availableProcessors()
        val threads = when (model.family) {
            // Kokoro is the heavier graph: sherpa's own helper uses 4 threads for
            // speaker-embedding models and 2 for plain VITS.
            VoiceModel.Family.KOKORO -> cores.coerceIn(1, 4)

            VoiceModel.Family.PIPER -> cores.coerceIn(1, 2)
        }
        val modelConfig = OfflineTtsModelConfig(
            kokoro = if (model.family == VoiceModel.Family.KOKORO) {
                OfflineTtsKokoroModelConfig(
                    model = onnx.absolutePath,
                    voices = File(dir, model.voicesFile).absolutePath,
                    tokens = tokens,
                    dataDir = dataDir
                )
            } else {
                OfflineTtsKokoroModelConfig()
            },
            vits = if (model.family == VoiceModel.Family.PIPER) {
                OfflineTtsVitsModelConfig(
                    model = onnx.absolutePath,
                    lexicon = "",
                    tokens = tokens,
                    dataDir = dataDir
                )
            } else {
                OfflineTtsVitsModelConfig()
            },
            numThreads = threads,
            debug = false,
            provider = "cpu"
        )
        try {
            val engine = OfflineTts(config = OfflineTtsConfig(model = modelConfig, maxNumSentences = 1))
            val sr = engine.sampleRate()
            val speakers = engine.numSpeakers()
            val wantedVoice = VoiceGuideSettings.voice(context)
            val sid = (model.speakers[wantedVoice] ?: model.speakers.values.firstOrNull() ?: 0)
                .coerceIn(0, (speakers - 1).coerceAtLeast(0))
            // Build the track before publishing anything: a missing track used to
            // leave the engine half-loaded (tts set, track null, load() returning false).
            val player = buildTrack(sr)
            if (player == null) {
                engine.release()
                Log.e(TAG, "${model.id}: no AudioTrack for ${sr} Hz, not loading")
                return@withContext false
            }
            // A load the caller already gave up on must not publish a live engine.
            val stale = synchronized(stateLock) {
                if (generation != loadGeneration.get()) {
                    true
                } else {
                    tts = engine
                    track = player
                    loadedModel = model
                    loadedSid = sid
                    loadedSampleRate = sr
                    false
                }
            }
            if (stale) {
                runCatching { player.release() }
                runCatching { engine.release() }
                Log.w(TAG, "${model.id}: finished after the load was abandoned, discarded")
                return@withContext false
            }
            Log.i(TAG, "loaded ${model.id}: sr=$sr speakers=$speakers sid=$sid threads=$threads")
            true
        } catch (t: Throwable) {
            // UnsatisfiedLinkError on an unsupported ABI, OOM on a small device,
            // or a corrupt model. The manager falls back to the platform engine.
            Log.e(TAG, "failed to load ${model.id}", t)
            releaseInternal()
            false
        }
    }

    private fun buildTrack(sampleRate: Int): AudioTrack? {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        if (minBuffer <= 0) {
            Log.e(TAG, "AudioTrack.getMinBufferSize returned $minBuffer for $sampleRate Hz")
            return null
        }
        return try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        // Speech on the media usage: the manager's transient-may-duck
                        // focus request lowers the game instead of pausing it.
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                // 4x the minimum keeps the generator ahead of playback without
                // holding a large buffer while paused.
                .setBufferSizeInBytes(minBuffer * TRACK_BUFFER_FACTOR)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (t: Throwable) {
            Log.e(TAG, "could not create AudioTrack", t)
            null
        }
    }

    override suspend fun speak(text: String, onDone: (() -> Unit)?) {
        val spoken = text.trim()
        if (spoken.isEmpty()) {
            onDone?.invoke()
            return
        }
        if (tts == null || track == null) {
            if (!prepare()) {
                onDone?.invoke()
                return
            }
        }

        // Rapid Next/Prev: drop whatever was playing before starting.
        stop()
        stopRequested.set(false)
        paused.set(false)
        speaking.set(true)
        val fired = AtomicBoolean(false)
        val finish = {
            if (fired.compareAndSet(false, true)) {
                speaking.set(false)
                onDone?.invoke()
            }
        }
        try {
            withContext(Dispatchers.IO) {
                genLock.withThreadLock {
                    // A release or reload may have replaced or freed the handles while we
                    // waited for the lock, so read them only now, under the lock.
                    val ttsNow = tts
                    val trackNow = track
                    if (ttsNow == null || trackNow == null || stopRequested.get()) return@withThreadLock
                    val config = GenerationConfig(
                        silenceScale = SILENCE_SCALE,
                        speed = rate,
                        sid = loadedSid
                    )
                    try {
                        trackNow.play()
                        ttsNow.generateWithConfigAndCallback(spoken, config) { samples ->
                            if (stopRequested.get()) {
                                0 // ask sherpa-onnx to stop generating
                            } else {
                                writeBlocking(trackNow, samples)
                                1
                            }
                        }
                        if (!stopRequested.get()) {
                            // MODE_STREAM: stop() plays out what is already buffered.
                            trackNow.stop()
                        }
                    } catch (t: Throwable) {
                        if (!stopRequested.get()) Log.e(TAG, "generation failed", t)
                    } finally {
                        if (stopRequested.get()) {
                            try {
                                trackNow.pause()
                                trackNow.flush()
                            } catch (t: Throwable) {
                                Log.w(TAG, "flush failed", t)
                            }
                        }
                    }
                }
            }
        } finally {
            finish()
        }
    }

    /**
     * Blocking write that honours pause and stop. Runs on [Dispatchers.IO], so
     * sleeping here does not stall the main thread or the composition.
     */
    private fun writeBlocking(player: AudioTrack, samples: FloatArray) {
        var offset = 0
        while (offset < samples.size) {
            if (stopRequested.get()) return
            while (paused.get() && !stopRequested.get()) {
                try {
                    Thread.sleep(PAUSE_POLL_MS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
            if (stopRequested.get()) return
            val written = try {
                player.write(samples, offset, samples.size - offset, AudioTrack.WRITE_BLOCKING)
            } catch (t: Throwable) {
                Log.e(TAG, "AudioTrack.write failed", t)
                return
            }
            if (written <= 0) {
                Log.w(TAG, "AudioTrack.write returned $written")
                return
            }
            offset += written
        }
    }

    override fun stop() {
        stopRequested.set(true)
        paused.set(false)
        speaking.set(false)
        try {
            track?.let {
                it.pause()
                it.flush()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "stop failed", t)
        }
    }

    override fun pause() {
        if (!speaking.get()) return
        paused.set(true)
        try {
            track?.pause()
        } catch (t: Throwable) {
            Log.w(TAG, "pause failed", t)
        }
    }

    override fun resume() {
        if (!paused.get()) return
        try {
            track?.play()
        } catch (t: Throwable) {
            Log.w(TAG, "resume failed", t)
            return
        }
        paused.set(false)
    }

    override fun setSpeechRate(newRate: Float) {
        rate = newRate.coerceIn(VoiceGuideSettings.MIN_RATE, VoiceGuideSettings.MAX_RATE)
    }

    override fun release() = releaseInternal()

    /**
     * Tears the native engine down. The handles are detached under [stateLock],
     * so a concurrent [load] never sees them half-published. The native objects
     * are freed under [genLock]: a sentence in flight is asked to stop (its
     * callback returns 0, and the blocked AudioTrack write is unblocked by the
     * pause/flush below) and the free waits for it to leave the generator.
     */
    private fun releaseInternal() {
        stopRequested.set(true)
        paused.set(false)
        speaking.set(false)
        val engine: OfflineTts?
        val player: AudioTrack?
        synchronized(stateLock) {
            engine = tts
            player = track
            tts = null
            track = null
            loadedModel = null
            loadedSampleRate = 0
        }
        try {
            player?.pause()
            player?.flush()
        } catch (t: Throwable) {
            Log.w(TAG, "AudioTrack pause/flush failed", t)
        }
        genLock.withThreadLock {
            try {
                player?.release()
            } catch (t: Throwable) {
                Log.w(TAG, "AudioTrack release failed", t)
            }
            try {
                engine?.release()
            } catch (t: Throwable) {
                Log.w(TAG, "OfflineTts release failed", t)
            }
        }
    }

    companion object {
        private const val TAG = "SherpaVoiceEngine"

        /** Kokoro loads in a few seconds on a mid-range phone; past this we use the system voice. */
        private const val LOAD_TIMEOUT_MS = 20_000L
        private const val TRACK_BUFFER_FACTOR = 4
        private const val PAUSE_POLL_MS = 20L

        /**
         * Sherpa's own default. Lower values shorten the gaps between sentences;
         * the guide lines are short, so the default sounds right.
         */
        private const val SILENCE_SCALE = 0.2f
    }
}
