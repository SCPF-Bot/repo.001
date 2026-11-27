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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * mlbb/tts/SherpaOnnxVoiceEngine.kt
 * GitHub path: app/src/main/java/com/mlbb/plus/tts/SherpaOnnxVoiceEngine.kt
 *
 * On-device neural TTS through sherpa-onnx. Kokoro is the primary voice, Piper
 * the lighter fallback, exactly as the feature spec asks.
 *
 * Everything runs offline: the model files come from [VoiceModelDownloader]
 * (`filesDir/tts/`), synthesis happens on a background thread, and audio goes
 * out through a streaming [AudioTrack] so the first sentence starts playing
 * while the rest is still being generated.
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
    private val downloader: VoiceModelDownloader
) : VoiceEngine {

    override val name: String
        get() = loadedModel?.let { "sherpa-onnx \u00b7 ${it.label}" } ?: "sherpa-onnx"

    override val isSpeaking: Boolean
        get() = speaking.get() && !stopRequested.get()

    /** We own the AudioTrack, so a real mid-utterance pause is possible. */
    override val supportsPause: Boolean
        get() = true

    /** The pack currently loaded, for Settings copy and logs. Null before [prepare]. */
    var loadedModel: VoiceModel? = null
        private set

    /** The speaker id resolved for the configured voice. */
    var loadedSid: Int = 0
        private set

    /** Sample rate reported by the model (24000 Kokoro, 22050 Piper). */
    var loadedSampleRate: Int = 0
        private set

    @Volatile
    private var tts: OfflineTts? = null

    @Volatile
    private var track: AudioTrack? = null

    /** Guards the engine/track handover so [release] cannot race [load]. */
    private val stateLock = Any()
    private val speaking = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val loadLock = Mutex()
    private var rate = VoiceGuideSettings.DEFAULT_RATE

    /**
     * Loads the best available pack: the one matching the configured voice,
     * otherwise the first installed pack in the Kokoro -> Piper fallback chain.
     * Returns false when nothing is installed or the native libs cannot load,
     * which is the manager's cue to fall back to [SystemTtsVoiceEngine].
     */
    override suspend fun prepare(): Boolean = loadLock.withLock {
        if (tts != null && track != null) return@withLock true
        val wanted = VoiceModelDownloader.forVoice(VoiceGuideSettings.voice(context))
        val chain = if (wanted != null) VoiceModelDownloader.fallbackChain(wanted) else VoiceModelDownloader.MODELS
        val model = chain.firstOrNull { downloader.isInstalled(it) }
        if (model == null) {
            Log.i(TAG, "no voice pack installed yet")
            return@withLock false
        }
        if (wanted != null && model != wanted) {
            val notice = "${wanted.label} was unavailable, switched to ${model.label}."
            VoiceGuideSettings.setFallbackNotice(context, notice)
            Log.w(TAG, notice)
        }
        load(model)
    }

    private suspend fun load(model: VoiceModel): Boolean = withContext(Dispatchers.IO) {
        releaseInternal()
        val dir = downloader.modelDir(model)
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
            val player = buildTrack(sr)
            synchronized(stateLock) {
                tts = engine
                track = player
                loadedModel = model
                loadedSid = sid
                loadedSampleRate = sr
            }
            Log.i(TAG, "loaded ${model.id}: sr=$sr speakers=$speakers sid=$sid threads=$threads")
            player != null
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
        val engine = tts
        val player = track
        if (engine == null || player == null) {
            if (!prepare()) {
                onDone?.invoke()
                return
            }
        }
        val ttsNow = tts ?: run {
            onDone?.invoke()
            return
        }
        val trackNow = track ?: run {
            onDone?.invoke()
            return
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
     * Tears the native engine down. Runs under [stateLock], and the expensive
     * part of [load] publishes its handles under the same lock, so a release
     * that lands mid-load either happens before the handles are stored (and the
     * load then overwrites them) or after (and cleans them up).
     */
    private fun releaseInternal() = synchronized(stateLock) {
        stopRequested.set(true)
        paused.set(false)
        speaking.set(false)
        try {
            track?.let {
                it.pause()
                it.flush()
                it.release()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "AudioTrack release failed", t)
        }
        track = null
        try {
            tts?.release()
        } catch (t: Throwable) {
            Log.w(TAG, "OfflineTts release failed", t)
        }
        tts = null
        loadedModel = null
        loadedSampleRate = 0
        Unit
    }

    companion object {
        private const val TAG = "SherpaVoiceEngine"
        private const val TRACK_BUFFER_FACTOR = 4
        private const val PAUSE_POLL_MS = 20L

        /**
         * Sherpa's own default. Lower values shorten the gaps between sentences;
         * the guide lines are short, so the default sounds right.
         */
        private const val SILENCE_SCALE = 0.2f
    }
}
