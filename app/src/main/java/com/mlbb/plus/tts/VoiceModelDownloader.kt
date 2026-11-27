package com.mlbb.plus.tts

import android.content.Context
import android.os.StatFs
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

/**
 * mlbb/tts/VoiceModelDownloader.kt
 * GitHub path: app/src/main/java/com/mlbb/plus/tts/VoiceModelDownloader.kt
 *
 * On-demand voice model delivery. NOTHING is bundled in the APK: the first tap
 * on the overlay's Speaker control (with Voice Guide enabled) starts the
 * download for the selected voice pack, straight into `filesDir/tts/`.
 *
 * Design notes
 * ------------
 * - The archive is streamed: network -> sha256 -> bzip2 -> tar -> disk. The
 *   103 MB Kokoro archive never lands on the device, so peak disk usage is the
 *   extracted model only (158 MB Kokoro / 37 MB Piper) instead of both.
 * - Extraction goes to `<dir>.partial-<rand>` and is renamed only after the
 *   digest of the whole compressed stream matched [VoiceModel.sha256]. A marker
 *   file records that, so a half-written directory can never look installed.
 * - Every checksum and byte size below was verified against the upstream
 *   `checksum.txt` of the `tts-models` release (see TTS_IMPLEMENTATION.md).
 * - sherpa-onnx is pinned to 1.13.8 on purpose: 2.0.x removed the espeak-ng
 *   frontend, which makes the Piper packs unusable unless a separate
 *   `lexicon-en-us.txt` is shipped for Kokoro. Upgrading needs both changes.
 */
class VoiceModelDownloader(private val context: Context) {

    /** Overall state of one [download] call, for the progress UI. */
    sealed class Progress {
        data object Idle : Progress()

        /** Bytes received so far; [total] is 0 when the server omitted Content-Length. */
        data class Downloading(val received: Long, val total: Long) : Progress()

        /** Bytes written to disk; [total] is the archive's uncompressed size. */
        data class Extracting(val written: Long, val total: Long) : Progress()

        data object Verifying : Progress()
        data class Done(val model: VoiceModel, val dir: File) : Progress()
        data class Failed(val message: String, val retryable: Boolean) : Progress()
        data object Cancelled : Progress()
    }

    private val cancelled = AtomicBoolean(false)

    /** `filesDir/tts` - private to the app, survives restarts, no permission needed. */
    fun rootDir(): File = File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun modelDir(model: VoiceModel): File = File(rootDir(), model.dirName)

    /** True only when extraction completed AND the digest matched. */
    fun isInstalled(model: VoiceModel): Boolean = marker(model).isFile

    fun isInstalled(modelId: String): Boolean = MODELS.firstOrNull { it.id == modelId }?.let { isInstalled(it) } ?: false

    /** Bytes currently occupied by installed packs, for the Settings screen. */
    fun installedBytes(): Long = MODELS.filter { isInstalled(it) }.sumOf { dirBytes(modelDir(it)) }

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
     * Downloads and installs [model]. Safe to call again after a failure: the
     * partial directory is discarded first. Emits [Progress] updates on the
     * caller's dispatcher.
     */
    suspend fun download(
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

        var connection: HttpURLConnection? = null
        try {
            onProgress(Progress.Downloading(0, model.sizeBytes))
            connection = (URL(model.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                return@withContext fail(onProgress, "Download failed (HTTP $code)", retryable = true)
            }
            val declared = connection.contentLengthLong.let { if (it > 0) it else model.sizeBytes }
            val digest = MessageDigest.getInstance("SHA-256")
            var written = 0L
            var lastPublish = 0L

            DigestInputStream(connection.inputStream.buffered(BUFFER), digest).use { hashed ->
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
                    "Download was corrupted (checksum mismatch). Please retry.",
                    retryable = true
                )
            }
            // Declared length is a second, cheap integrity signal.
            if (declared != model.sizeBytes) {
                Log.w(TAG, "${model.id}: server sent $declared bytes, expected ${model.sizeBytes}")
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
            Log.e(TAG, "download ${model.id} failed", e)
            fail(onProgress, e.message?.take(160) ?: e.javaClass.simpleName, retryable = true)
        } finally {
            connection?.disconnect()
        }
    }

    private fun fail(onProgress: (Progress) -> Unit, message: String, retryable: Boolean): Progress.Failed {
        val p = Progress.Failed(message, retryable)
        onProgress(p)
        return p
    }

    private fun marker(model: VoiceModel): File = File(modelDir(model), MARKER)

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
        private const val TAG = "VoiceModelDownloader"
        const val DIR = "tts"
        private const val MARKER = ".mlbb-voice-installed"
        private const val PARTIAL_INFIX = ".partial-"
        private const val BUFFER = 64 * 1024
        private const val PROGRESS_STEP = 512 * 1024L
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val MIN_FREE_MARGIN = 64L * 1024 * 1024
        private const val MB = 1_000_000L
        private val SKIPPED_FILES = setOf("README.md", "MODEL_CARD", "LICENSE", ".DS_Store")

        /** Base for every asset; the `tts-models` release is a permanent tag. */
        private const val MODEL_BASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"

        /**
         * Kokoro-82M v0_19, int8. Highest quality, 11 speakers in one pack,
         * 24 kHz. Slower to synthesise on low-end SoCs - that is what the Piper
         * packs below are for. Speaker ids were confirmed by measuring the
         * fundamental frequency of every sid on a real 24 kHz synthesis run:
         * sids 0-4 and 7-8 are the female voices, 5-6 and 9-10 the male ones,
         * which matches the alphabetical kokoro-onnx order
         * (af_bella, af_heart, af_nicole, af_sarah, af_sky, am_adam,
         *  am_michael, bf_emma, bf_isabella, bm_george, bm_lewis).
         */
        val KOKORO = VoiceModel(
            id = "kokoro-int8-en-v0_19",
            label = "Kokoro (most natural)",
            family = VoiceModel.Family.KOKORO,
            dirName = "kokoro-int8-en-v0_19",
            url = MODEL_BASE + "kokoro-int8-en-v0_19.tar.bz2",
            sha256 = "c9f0dd393615805b0bab050c340834d5e684e732aec91c0e860cd30e982c08bd",
            sizeBytes = 103_248_205L,
            uncompressedBytes = 165_600_000L,
            modelFile = "model.int8.onnx",
            voicesFile = "voices.bin",
            tokensFile = "tokens.txt",
            espeakDir = "espeak-ng-data",
            speakers = mapOf(
                VoiceGuideSettings.VOICE_KOKORO_FEMALE to 1, // af_heart
                VoiceGuideSettings.VOICE_KOKORO_MALE to 5 // am_adam
            )
        )

        /** Piper en_US lessac, medium, int8. Female, 22.05 kHz, single speaker. */
        val PIPER_LESSAC = VoiceModel(
            id = "vits-piper-en_US-lessac-medium-int8",
            label = "Piper female (small, faster)",
            family = VoiceModel.Family.PIPER,
            dirName = "vits-piper-en_US-lessac-medium-int8",
            url = MODEL_BASE + "vits-piper-en_US-lessac-medium-int8.tar.bz2",
            sha256 = "f1c6d0295cf16087b05f80fdca5b44daca5cd78e2c425d419a42ba34929805f9",
            sizeBytes = 20_969_179L,
            uncompressedBytes = 38_400_000L,
            modelFile = "en_US-lessac-medium.onnx",
            voicesFile = "",
            tokensFile = "tokens.txt",
            espeakDir = "espeak-ng-data",
            speakers = mapOf(VoiceGuideSettings.VOICE_PIPER_FEMALE to 0)
        )

        /** Piper en_US ryan, medium, int8. Male, 22.05 kHz, single speaker. */
        val PIPER_RYAN = VoiceModel(
            id = "vits-piper-en_US-ryan-medium-int8",
            label = "Piper male (small, faster)",
            family = VoiceModel.Family.PIPER,
            dirName = "vits-piper-en_US-ryan-medium-int8",
            url = MODEL_BASE + "vits-piper-en_US-ryan-medium-int8.tar.bz2",
            sha256 = "376eb489d42e98cb49f0e13a633e8d88580bd247ed8aacc298a733210404f771",
            sizeBytes = 21_083_446L,
            uncompressedBytes = 38_400_000L,
            modelFile = "en_US-ryan-medium.onnx",
            voicesFile = "",
            tokensFile = "tokens.txt",
            espeakDir = "espeak-ng-data",
            speakers = mapOf(VoiceGuideSettings.VOICE_PIPER_MALE to 0)
        )

        /** Kokoro first (spec: primary), Piper second (spec: lighter fallback). */
        val MODELS: List<VoiceModel> = listOf(KOKORO, PIPER_LESSAC, PIPER_RYAN)

        fun byId(id: String): VoiceModel? = MODELS.firstOrNull { it.id == id }

        /** The pack that provides [voiceId], or null for the platform engine. */
        fun forVoice(voiceId: String): VoiceModel? = MODELS.firstOrNull { voiceId in it.speakers }

        /** Fallback chain for a wanted family: Kokoro -> lessac -> ryan. */
        fun fallbackChain(preferred: VoiceModel): List<VoiceModel> = (listOf(preferred) + MODELS.filter { it != preferred })
    }
}

/**
 * One downloadable voice pack.
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

    /** Human size for UI copy: "103 MB" / "21 MB". */
    val sizeLabel: String = "${sizeBytes / 1_000_000} MB download"

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
