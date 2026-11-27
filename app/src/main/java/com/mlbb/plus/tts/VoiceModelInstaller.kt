package com.mlbb.plus.tts

import android.content.Context
import android.os.StatFs
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.json.JSONObject

/**
 * mlbb/tts/VoiceModelInstaller.kt
 * GitHub path: app/src/main/java/com/mlbb/plus/tts/VoiceModelInstaller.kt
 *
 * Offline voice pack installation. The voice packs ship INSIDE the APK as
 * `.tar.bz2` archives under `assets/tts/` (bundled at build time by the
 * `bundleTtsModels` Gradle task), so first use needs no network at all: the
 * selected pack is unpacked straight from the APK into `filesDir/tts/`.
 *
 * Design notes
 * ------------
 * - The catalogue is not hard-coded: [models] is parsed from
 *   `assets/tts/tts-models.json`, the manifest maintained by
 *   `scripts/update_tts_models.py`. Updating a voice pack is a manifest change,
 *   never a code change.
 * - Extraction is streamed: APK asset -> sha256 -> bzip2 -> tar -> disk, so
 *   peak disk usage is the extracted model only (158 MB Kokoro / 37 MB Piper),
 *   not the archive plus the model.
 * - Extraction goes to `<dir>.partial-<rand>` and is renamed only after the
 *   digest of the whole compressed stream matched [VoiceModel.sha256]. A marker
 *   file records that, so a half-written directory can never look installed.
 * - Every checksum and byte size in the manifest was verified against the
 *   upstream `checksum.txt` of the `tts-models` release (see TTS_IMPLEMENTATION.md).
 * - sherpa-onnx is pinned to 1.13.8 on purpose: 2.0.x removed the espeak-ng
 *   frontend, which makes the Piper packs unusable unless a separate
 *   `lexicon-en-us.txt` is shipped for Kokoro. Upgrading needs both changes.
 */
class VoiceModelInstaller(private val context: Context) {

    /** Overall state of one [install] call, for the progress UI. */
    sealed class Progress {
        data object Idle : Progress()

        /** Bytes written to disk; [total] is the archive's uncompressed size. */
        data class Extracting(val written: Long, val total: Long) : Progress()

        data object Verifying : Progress()
        data class Done(val model: VoiceModel, val dir: File) : Progress()
        data class Failed(val message: String, val retryable: Boolean) : Progress()
        data object Cancelled : Progress()
    }

    private val cancelled = AtomicBoolean(false)

    /** The voice packs compiled into this APK, in fallback order. */
    val models: List<VoiceModel> = loadModels(context)

    /** `filesDir/tts` - private to the app, survives restarts, no permission needed. */
    fun rootDir(): File = File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun modelDir(model: VoiceModel): File = File(rootDir(), model.dirName)

    /** True only when extraction completed AND the digest matched. */
    fun isInstalled(model: VoiceModel): Boolean = marker(model).isFile

    fun isInstalled(modelId: String): Boolean = models.firstOrNull { it.id == modelId }?.let { isInstalled(it) } ?: false

    /** Bytes currently occupied by installed packs, for the Settings screen. */
    fun installedBytes(): Long = models.filter { isInstalled(it) }.sumOf { dirBytes(modelDir(it)) }

    fun byId(id: String): VoiceModel? = models.firstOrNull { it.id == id }

    /** The pack that provides [voiceId], or null for the platform engine. */
    fun forVoice(voiceId: String): VoiceModel? = models.firstOrNull { voiceId in it.speakers }

    /** Fallback chain for a wanted pack: the pack itself, then the rest in order. */
    fun fallbackChain(preferred: VoiceModel): List<VoiceModel> = (listOf(preferred) + models.filter { it != preferred })

    fun cancel() {
        cancelled.set(true)
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
     * Installs [model] from the APK bundle. Safe to call again after a failure:
     * the partial directory is discarded first. Emits [Progress] updates on the
     * caller's dispatcher.
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
        val free = freeBytes()
        if (free in 1 until model.uncompressedBytes + MIN_FREE_MARGIN) {
            val msg = "Not enough storage: ${model.uncompressedBytes / MB} MB needed, ${free / MB} MB free"
            onProgress(Progress.Failed(msg, retryable = false))
            return@withContext Progress.Failed(msg, retryable = false)
        }

        val target = modelDir(model)
        target.deleteRecursively()
        val work = File(rootDir(), model.dirName + PARTIAL_INFIX + System.nanoTime())
        work.mkdirs()

        try {
            onProgress(Progress.Extracting(0, model.uncompressedBytes))
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            var lastPublish = 0L

            DigestInputStream(context.assets.open(assetPath(model)), digest).use { hashed ->
                BZip2CompressorInputStream(hashed).use { bz2 ->
                    TarArchiveInputStream(bz2).use { tar ->
                        var entry = tar.nextEntry
                        while (entry != null) {
                            coroutineContext.ensureActive()
                            if (cancelled.get()) {
                                work.deleteRecursively()
                                onProgress(Progress.Cancelled)
                                return@withContext Progress.Cancelled
                            }
                            val out = safeChild(work, model, entry.name, entry.isDirectory)
                            if (out != null && !entry.isDirectory) {
                                out.parentFile?.mkdirs()
                                FileOutputStream(out).use { sink ->
                                    val buf = ByteArray(BUFFER)
                                    while (true) {
                                        val n = tar.read(buf)
                                        if (n <= 0) break
                                        sink.write(buf, 0, n)
                                        written += n
                                        if (written - lastPublish > PROGRESS_STEP) {
                                            lastPublish = written
                                            onProgress(
                                                Progress.Extracting(written, model.uncompressedBytes)
                                            )
                                        }
                                    }
                                }
                            }
                            entry = tar.nextEntry
                        }
                    }
                }
            }

            onProgress(Progress.Verifying)
            val actual = digest.digest().toHex()
            if (!actual.equals(model.sha256, ignoreCase = true)) {
                work.deleteRecursively()
                Log.e(TAG, "checksum mismatch for ${model.id}: $actual")
                return@withContext fail(
                    onProgress,
                    "The bundled voice pack failed its integrity check (checksum mismatch).",
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
            Log.i(TAG, "installed ${model.id} ($written bytes) sha256=$actual")
            done
        } catch (e: java.io.InterruptedIOException) {
            work.deleteRecursively()
            onProgress(Progress.Cancelled)
            Progress.Cancelled
        } catch (e: Exception) {
            work.deleteRecursively()
            Log.e(TAG, "install ${model.id} failed", e)
            fail(onProgress, e.message?.take(160) ?: e.javaClass.simpleName, retryable = true)
        }
    }

    private fun fail(onProgress: (Progress) -> Unit, message: String, retryable: Boolean): Progress.Failed {
        val p = Progress.Failed(message, retryable)
        onProgress(p)
        return p
    }

    private fun marker(model: VoiceModel): File = File(modelDir(model), MARKER)

    /** `assets/tts/<archive>` - where bundleTtsModels staged the pack. */
    private fun assetPath(model: VoiceModel): String = ASSET_PREFIX + model.archiveName

    /**
     * Resolves one tar entry to a file inside [work], rejecting anything that
     * would escape it. The archives are prefixed with the model directory name,
     * which is stripped so the result lands directly in [work].
     */
    private fun safeChild(work: File, model: VoiceModel, rawName: String, isDir: Boolean): File? {
        if (rawName.isBlank()) return null
        var name = rawName.replace('\\', '/').trimStart('/')
        val prefix = model.dirName + "/"
        if (name.startsWith(prefix)) name = name.removePrefix(prefix)
        if (name == model.dirName) return null
        if (name.contains("../") || name.endsWith("/..")) {
            Log.w(TAG, "rejected unsafe tar entry: $rawName")
            return null
        }
        // Skip the metadata we do not need at runtime.
        val leaf = name.substringAfterLast('/')
        if (leaf in SKIPPED_FILES) return null
        val child = File(work, name)
        val root = work.canonicalPath
        if (!child.canonicalPath.startsWith(root)) {
            Log.w(TAG, "rejected tar entry outside the target dir: $rawName")
            return null
        }
        return if (isDir) child.apply { mkdirs() } else child
    }

    private fun freeBytes(): Long = try {
        StatFs(rootDir().absolutePath).availableBytes
    } catch (e: Exception) {
        Log.w(TAG, "StatFs failed", e)
        Long.MAX_VALUE
    }

    private fun dirBytes(dir: File): Long = if (!dir.isDirectory) 0L else dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "VoiceModelInstaller"
        const val DIR = "tts"
        private const val MARKER = ".mlbb-voice-installed"
        private const val PARTIAL_INFIX = ".partial-"
        private const val BUFFER = 64 * 1024
        private const val PROGRESS_STEP = 512 * 1024L
        private const val MIN_FREE_MARGIN = 64L * 1024 * 1024
        private const val MB = 1_000_000L
        private val SKIPPED_FILES = setOf("README.md", "MODEL_CARD", "LICENSE", ".DS_Store")

        /** Where bundleTtsModels stages the archives and the manifest lives. */
        private const val ASSET_PREFIX = "tts/"
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
 * One bundled voice pack, described by the `tts-models.json` manifest.
 *
 * [speakers] maps the voice ids used by [VoiceGuideSettings] onto the integer
 * `sid` sherpa-onnx expects; Piper packs are single-speaker so they always map
 * to 0.
 */
data class VoiceModel(
    val id: String,
    val label: String,
    val family: Family,
    val dirName: String,
    val archiveName: String,
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

    /** Human size for UI copy: "103 MB" / "21 MB". */
    val sizeLabel: String = "${sizeBytes / 1_000_000} MB bundled"

    val diskLabel: String = "about ${uncompressedBytes / 1_000_000} MB on disk"

    /**
     * Verifies the extracted tree before it is promoted to "installed". Uses a
     * discovered `.onnx` when the expected file is missing, so a renamed future
     * release degrades to a warning instead of a hard failure.
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
        /** Guards against a truncated/placeholder file passing the layout check. */
        private const val MIN_ONNX_BYTES = 2L * 1024 * 1024
    }
}
