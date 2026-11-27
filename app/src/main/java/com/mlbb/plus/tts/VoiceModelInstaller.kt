package com.mlbb.plus.tts

import android.content.Context
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Downloads and installs the neural voice packs on demand.
 *
 * Packs are fetched from the upstream `tts-models` release URLs listed in
 * `assets/tts/tts-models.json`. Nothing is bundled into the APK. The download streams
 * through [VoicePackUnpacker] straight to disk and is only promoted to "installed" after
 * its SHA-256 matches the manifest pin.
 *
 * Storage: `Android/data/<package>/files/tts/` on shared storage (see [rootDir]). That
 * needs no storage permission on API 29+, is removed when the app is uninstalled, and is
 * excluded from backup. It is not visible to other apps.
 *
 * Design notes
 * ------------
 * - The catalogue is parsed from the manifest. Updating a pack is a manifest change, not a
 *   code change (`scripts/update_tts_models.py` maintains it).
 * - The pack is not resumable. A failed download restarts from zero on the next attempt.
 * - sherpa-onnx is pinned to 1.13.8 on purpose: 2.0.x removed the espeak-ng frontend that
 *   the Piper packs need.
 */
class VoiceModelInstaller(private val context: Context) {

    /** Overall state of one [install] call, for the progress UI. */
    sealed class Progress {
        data object Idle : Progress()

        /** Compressed bytes received so far; [total] is the archive size from the manifest. */
        data class Downloading(val received: Long, val total: Long) : Progress()

        data object Verifying : Progress()
        data class Done(val model: VoiceModel, val dir: File) : Progress()
        data class Failed(val message: String, val retryable: Boolean) : Progress()
        data object Cancelled : Progress()
    }

    private val cancelled = AtomicBoolean(false)

    /** The in-flight connection, so [cancel] can abort a blocked socket read at once. */
    private val activeConnection = AtomicReference<HttpURLConnection?>(null)

    /** The voice packs listed in the manifest, in fallback order. */
    val models: List<VoiceModel> = loadModels(context)

    /**
     * `Android/data/<package>/files/tts`. Falls back to private `filesDir/tts` when no
     * external volume is mounted; packs installed in one place are not seen from the other.
     */
    fun rootDir(): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, DIR).apply { if (!exists()) mkdirs() }
    }

    fun modelDir(model: VoiceModel): File = File(rootDir(), model.dirName)

    /** True only when the download completed AND the digest matched. */
    fun isInstalled(model: VoiceModel): Boolean = marker(model).isFile

    fun isInstalled(modelId: String): Boolean = models.firstOrNull { it.id == modelId }?.let { isInstalled(it) } ?: false

    /** Bytes currently occupied by installed packs, for the Settings screen. */
    fun installedBytes(): Long = models.filter { isInstalled(it) }.sumOf { dirBytes(modelDir(it)) }

    fun byId(id: String): VoiceModel? = models.firstOrNull { it.id == id }

    /** The pack that provides [voiceId], or null for the platform engine. */
    fun forVoice(voiceId: String): VoiceModel? = models.firstOrNull { voiceId in it.speakers }

    /** Fallback chain for a wanted pack: the pack itself, then the rest in order. */
    fun fallbackChain(preferred: VoiceModel): List<VoiceModel> = (listOf(preferred) + models.filter { it != preferred })

    /** Stops an install in progress, including a download blocked on the network. */
    fun cancel() {
        cancelled.set(true)
        activeConnection.get()?.disconnect()
    }

    fun resetCancel() {
        cancelled.set(false)
    }

    /** Removes an installed pack (and any partial leftovers). */
    suspend fun delete(model: VoiceModel): Boolean = withContext(Dispatchers.IO) {
        marker(model).delete()
        val ok = modelDir(model).deleteRecursively()
        rootDir().listFiles()?.filter { it.name.startsWith(model.dirName + PARTIAL_INFIX) }
            ?.forEach { it.deleteRecursively() }
        Log.i(TAG, "delete ${model.id} -> $ok")
        ok
    }

    /**
     * Downloads and installs [model]. Safe to call again after a failure: the partial
     * directory is discarded first. Emits [Progress] updates through [onProgress].
     */
    suspend fun install(
        model: VoiceModel,
        onProgress: (Progress) -> Unit
    ): Progress = withContext(Dispatchers.IO) {
        cancelled.set(false)
        if (isInstalled(model)) {
            val done = Progress.Done(model, modelDir(model))
            onProgress(done)
            return@withContext done
        }
        // freeBytes() reports Long.MAX_VALUE when StatFs fails, so "unknown" passes.
        // A genuinely full disk (0 bytes) must fail.
        val free = freeBytes()
        if (free < model.uncompressedBytes + MIN_FREE_MARGIN) {
            val msg = "Not enough storage: ${model.uncompressedBytes / MB} MB needed, ${free / MB} MB free"
            onProgress(Progress.Failed(msg, retryable = false))
            return@withContext Progress.Failed(msg, retryable = false)
        }

        val target = modelDir(model)
        target.deleteRecursively()
        val work = File(rootDir(), model.dirName + PARTIAL_INFIX + System.nanoTime())
        work.mkdirs()

        try {
            onProgress(Progress.Downloading(0, model.sizeBytes))
            val connection = openConnection(model)
            val actual: String? = try {
                connection.inputStream.use { network ->
                    VoicePackUnpacker.unpack(
                        network = network,
                        archiveName = model.archiveName,
                        dirName = model.dirName,
                        work = work,
                        isCancelled = { cancelled.get() },
                        onReceived = { received -> onProgress(Progress.Downloading(received, model.sizeBytes)) }
                    )
                }
            } finally {
                activeConnection.compareAndSet(connection, null)
                connection.disconnect()
            }
            if (actual == null || cancelled.get()) {
                work.deleteRecursively()
                onProgress(Progress.Cancelled)
                return@withContext Progress.Cancelled
            }

            onProgress(Progress.Verifying)
            if (!actual.equals(model.sha256, ignoreCase = true)) {
                work.deleteRecursively()
                Log.e(TAG, "checksum mismatch for ${model.id}: $actual")
                return@withContext fail(
                    onProgress,
                    "The download failed its integrity check (checksum mismatch). Try again.",
                    retryable = true
                )
            }
            if (!model.matchesLayout(work)) {
                work.deleteRecursively()
                return@withContext fail(
                    onProgress,
                    "The voice pack did not contain the expected model files.",
                    retryable = false
                )
            }
            if (!work.renameTo(target)) {
                work.deleteRecursively()
                return@withContext fail(onProgress, "Could not move the voice pack into place.", retryable = true)
            }
            marker(model).writeText(actual)
            val done = Progress.Done(model, target)
            onProgress(done)
            Log.i(TAG, "installed ${model.id} sha256=$actual")
            done
        } catch (e: InterruptedIOException) {
            work.deleteRecursively()
            onProgress(Progress.Cancelled)
            Progress.Cancelled
        } catch (e: CancellationException) {
            // Cooperative cancellation (the manager was released). Propagate it so the
            // caller can clear its spinner.
            work.deleteRecursively()
            throw e
        } catch (e: Exception) {
            work.deleteRecursively()
            if (cancelled.get()) {
                onProgress(Progress.Cancelled)
                return@withContext Progress.Cancelled
            }
            Log.e(TAG, "install ${model.id} failed", e)
            val reason = e.message?.take(120) ?: e.javaClass.simpleName
            fail(onProgress, "Download failed: $reason. Check your connection and try again.", retryable = true)
        }
    }

    /** Opens the pack URL and fails on any non-2xx status before streaming starts. */
    private fun openConnection(model: VoiceModel): HttpURLConnection {
        val connection = URI(model.url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "MLBB-Plus")
        activeConnection.set(connection)
        val status = connection.responseCode
        if (status !in 200..299) {
            connection.disconnect()
            throw IOException("HTTP $status from the download server")
        }
        return connection
    }

    private fun fail(onProgress: (Progress) -> Unit, message: String, retryable: Boolean): Progress.Failed {
        val p = Progress.Failed(message, retryable)
        onProgress(p)
        return p
    }

    private fun marker(model: VoiceModel): File = File(modelDir(model), MARKER)

    private fun freeBytes(): Long = try {
        StatFs(rootDir().absolutePath).availableBytes
    } catch (e: Exception) {
        Log.w(TAG, "StatFs failed", e)
        Long.MAX_VALUE
    }

    private fun dirBytes(dir: File): Long = if (!dir.isDirectory) 0L else dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    companion object {
        private const val TAG = "VoiceModelInstaller"
        const val DIR = "tts"
        private const val MARKER = ".mlbb-voice-installed"
        private const val PARTIAL_INFIX = ".partial-"
        private const val MIN_FREE_MARGIN = 64L * 1024 * 1024
        private const val MB = 1_000_000L
        private const val CONNECT_TIMEOUT_MS = 30_000
        /** Longest silence tolerated mid-download before the attempt is failed as retryable. */
        private const val READ_TIMEOUT_MS = 60_000

        /** The manifest is the only tts asset compiled into the APK. */
        private const val MANIFEST_ASSET = "tts/tts-models.json"

        /** Parsed once per process: the manifest is immutable for an install. */
        @Volatile
        private var cachedModels: List<VoiceModel>? = null

        fun loadModels(context: Context): List<VoiceModel> {
            cachedModels?.let { return it }
            synchronized(this) {
                cachedModels?.let { return it }
                val parsed = try {
                    val text = context.assets.open(MANIFEST_ASSET).use { it.readBytes().decodeToString() }
                    parseManifest(JSONObject(text))
                } catch (t: Throwable) {
                    Log.e(TAG, "could not read $MANIFEST_ASSET - no neural voice available", t)
                    emptyList()
                }
                cachedModels = parsed
                return parsed
            }
        }

        private fun parseManifest(json: JSONObject): List<VoiceModel> {
            val arrays = json.getJSONArray("models")
            val out = ArrayList<VoiceModel>(arrays.length())
            for (i in 0 until arrays.length()) {
                val m = arrays.getJSONObject(i)
                val speakersJson = m.optJSONObject("speakers") ?: JSONObject()
                val speakers = HashMap<String, Int>(speakersJson.length())
                for (key in speakersJson.keys()) speakers[key] = speakersJson.getInt(key)
                out.add(
                    VoiceModel(
                        id = m.getString("id"),
                        label = m.getString("label"),
                        family = when (m.getString("family").lowercase()) {
                            "kokoro" -> VoiceModel.Family.KOKORO
                            else -> VoiceModel.Family.PIPER
                        },
                        dirName = m.getString("dirName"),
                        archiveName = m.getString("archive"),
                        url = m.getString("url"),
                        sha256 = m.getString("sha256"),
                        sizeBytes = m.getLong("sizeBytes"),
                        uncompressedBytes = m.getLong("uncompressedBytes"),
                        modelFile = m.getString("modelFile"),
                        voicesFile = m.optString("voicesFile", ""),
                        tokensFile = m.getString("tokensFile"),
                        espeakDir = m.getString("espeakDir"),
                        speakers = speakers
                    )
                )
            }
            return out
        }
    }
}

/**
 * One voice pack, described by the `tts-models.json` manifest.
 *
 * [speakers] maps the voice ids used by [VoiceGuideSettings] onto the integer `sid`
 * sherpa-onnx expects. Piper packs are single-speaker, so they always map to 0.
 */
data class VoiceModel(
    val id: String,
    val label: String,
    val family: Family,
    val dirName: String,
    val archiveName: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val uncompressedBytes: Long,
    val modelFile: String,
    val voicesFile: String,
    val tokensFile: String,
    val espeakDir: String,
    val speakers: Map<String, Int>
) {
    enum class Family { KOKORO, PIPER }

    /** Download size for UI copy, for example "103 MB". */
    val sizeLabel: String = "${sizeBytes / 1_000_000} MB"

    val diskLabel: String = "about ${uncompressedBytes / 1_000_000} MB on disk"

    /**
     * Verifies the extracted tree before it is promoted to "installed". Uses a discovered
     * `.onnx` when the expected file is missing, so a renamed future release degrades to a
     * warning instead of a hard failure.
     */
    fun matchesLayout(dir: File): Boolean {
        if (!dir.isDirectory) return false
        if (!File(dir, tokensFile).isFile) return false
        if (!File(dir, espeakDir).isDirectory) return false
        val model = resolveModelFile(dir) ?: return false
        return model.length() > MIN_ONNX_BYTES
    }

    /** The ONNX graph to hand to sherpa-onnx, or null when the pack is broken. */
    fun resolveModelFile(dir: File): File? {
        val expected = File(dir, modelFile)
        if (expected.isFile) return expected
        return dir.listFiles()?.filter { it.isFile && it.name.endsWith(".onnx") }
            ?.maxByOrNull { it.length() }
    }

    companion object {
        /** Guards against a truncated or placeholder file passing the layout check. */
        private const val MIN_ONNX_BYTES = 2L * 1024 * 1024
    }
}
