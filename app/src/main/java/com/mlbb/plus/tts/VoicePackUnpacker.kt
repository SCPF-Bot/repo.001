package com.mlbb.plus.tts

import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

/**
 * Streams one voice-pack archive (`.tar.bz2` or `.tar.gz`) from the network into a work
 * directory. It has no Android dependencies, so the JVM unit tests exercise the real code.
 *
 * The SHA-256 is computed over the compressed bytes exactly as they arrive, so the caller
 * can compare it with the pin in the manifest before the unpacked tree is promoted.
 */
internal object VoicePackUnpacker {

    private const val BUFFER = 64 * 1024
    private const val PROGRESS_STEP = 512 * 1024L
    private val SKIPPED_FILES = setOf("README.md", "MODEL_CARD", "LICENSE", ".DS_Store")

    /**
     * Decompresses [network] into [work] and returns the lower-case hex SHA-256 of the compressed
     * stream. Returns null when [isCancelled] turns true; partial files are left for the caller
     * to delete. [onReceived] gets the cumulative compressed byte count, throttled to about
     * every 512 KB.
     *
     * Entries are written with the leading `<dirName>/` prefix removed, so the result sits
     * directly in [work]. Paths that would escape [work] are dropped.
     */
    suspend fun unpack(
        network: InputStream,
        archiveName: String,
        dirName: String,
        work: File,
        isCancelled: () -> Boolean,
        onReceived: (Long) -> Unit
    ): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        // Hash at the source: every byte the decoder takes from the network goes through here,
        // including bytes it skips, so the digest always covers the whole compressed stream.
        val hashed = HashingInputStream(network, digest, onReceived)
        decompressor(hashed, archiveName).use { decoded ->
            TarArchiveInputStream(decoded).use { tar ->
                var entry = tar.nextEntry
                while (entry != null) {
                    coroutineContext.ensureActive()
                    if (isCancelled()) return null
                    val out = safeChild(work, dirName, entry.name, entry.isDirectory)
                    if (out != null && !entry.isDirectory) {
                        out.parentFile?.mkdirs()
                        FileOutputStream(out).use { sink ->
                            val buf = ByteArray(BUFFER)
                            while (true) {
                                val n = tar.read(buf)
                                if (n <= 0) break
                                sink.write(buf, 0, n)
                            }
                        }
                    }
                    entry = tar.nextEntry
                }
                // Tar stops at its end-of-archive marker, but the compressor's trailer follows
                // it. Read the rest through the digest, or the hash would not cover the whole
                // file and a valid pack would fail its check.
                val rest = ByteArray(BUFFER)
                while (hashed.read(rest) >= 0) {
                    coroutineContext.ensureActive()
                    if (isCancelled()) return null
                }
            }
        }
        return digest.digest().toHex()
    }

    private fun decompressor(input: InputStream, archiveName: String): InputStream = when {
        archiveName.endsWith(".tar.bz2") -> BZip2CompressorInputStream(input)
        archiveName.endsWith(".tar.gz") || archiveName.endsWith(".tgz") -> GzipCompressorInputStream(input)
        else -> throw IOException("Unsupported voice pack archive: $archiveName")
    }

    /**
     * Resolves one tar entry to a file inside [work], or null to skip it. Rejects any path that
     * would land outside [work] and drops metadata files the engine does not read.
     */
    private fun safeChild(work: File, dirName: String, rawName: String, isDir: Boolean): File? {
        if (rawName.isBlank()) return null
        var name = rawName.replace('\\', '/').trimStart('/')
        val prefix = "$dirName/"
        if (name.startsWith(prefix)) name = name.removePrefix(prefix)
        if (name.isEmpty() || name == dirName) return null
        if (name.split('/').any { it == ".." }) return null
        if (name.substringAfterLast('/') in SKIPPED_FILES) return null
        val child = File(work, name)
        if (!child.canonicalPath.startsWith(work.canonicalPath + File.separator)) return null
        return if (isDir) child.apply { mkdirs() } else child
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /**
     * Feeds every compressed byte into [digest] and reports the running total in steps. Both
     * read paths and skip() go through here: DigestInputStream does not hash skipped bytes,
     * and the gzip reader skips header fields.
     */
    private class HashingInputStream(
        input: InputStream,
        private val digest: MessageDigest,
        private val onReceived: (Long) -> Unit
    ) : FilterInputStream(input) {
        private var total = 0L
        private var lastReported = 0L

        override fun read(): Int {
            val b = super.read()
            if (b >= 0) {
                digest.update(b.toByte())
                account(1)
            }
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            if (n > 0) {
                digest.update(b, off, n)
                account(n)
            }
            return n
        }

        override fun skip(n: Long): Long {
            if (n <= 0) return 0
            val buffer = ByteArray(minOf(n, BUFFER.toLong()).toInt())
            var remaining = n
            while (remaining > 0) {
                val read = read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                if (read < 0) break
                remaining -= read
            }
            return n - remaining
        }

        override fun markSupported(): Boolean = false

        override fun mark(readlimit: Int) = Unit

        override fun reset() {
            throw IOException("mark/reset not supported")
        }

        private fun account(n: Int) {
            total += n
            if (total - lastReported >= PROGRESS_STEP) {
                lastReported = total
                onReceived(total)
            }
        }
    }
}
