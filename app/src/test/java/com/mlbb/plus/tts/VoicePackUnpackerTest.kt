package com.mlbb.plus.tts

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Random
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * app/src/test/java/com/mlbb/plus/tts/VoicePackUnpackerTest.kt
 *
 * The pack download path has no device test in this sandbox, so these tests build real
 * `.tar.bz2` and `.tar.gz` archives in memory and run them through [VoicePackUnpacker].
 * They cover the digest over the whole compressed stream (including the trailer after the
 * tar end marker), prefix stripping, the path-escape guard, cancellation and progress.
 */
class VoicePackUnpackerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val tokens = "a 1\nb 2\n".toByteArray()
    private val onnx = ByteArray(2 * 1024 * 1024).also { Random(7).nextBytes(it) }

    /** Builds `pack/` with a README (skipped), tokens, a model, and an espeak folder. */
    private fun archive(wrap: (OutputStream) -> OutputStream): ByteArray {
        val raw = ByteArrayOutputStream()
        TarArchiveOutputStream(wrap(raw)).use { tar ->
            tar.putArchiveEntry(TarArchiveEntry("pack/"))
            tar.closeArchiveEntry()
            addFile(tar, "pack/tokens.txt", tokens)
            addFile(tar, "pack/en_US-test.onnx", onnx)
            addFile(tar, "pack/README.md", "not needed".toByteArray())
            tar.putArchiveEntry(TarArchiveEntry("pack/espeak-ng-data/"))
            tar.closeArchiveEntry()
            addFile(tar, "pack/espeak-ng-data/phontab", "phon".toByteArray())
            // Attempts to climb out of the work directory must be dropped.
            addFile(tar, "pack/../../escaped.txt", "escape".toByteArray())
        }
        return raw.toByteArray()
    }

    private fun addFile(tar: TarArchiveOutputStream, name: String, bytes: ByteArray) {
        val entry = TarArchiveEntry(name)
        entry.size = bytes.size.toLong()
        tar.putArchiveEntry(entry)
        tar.write(bytes)
        tar.closeArchiveEntry()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `bzip2 pack unpacks, strips the prefix and digests the whole compressed stream`() = runBlocking {
        val bytes = archive { BZip2CompressorOutputStream(it) }
        val work = tmp.newFolder("work")

        val sha = VoicePackUnpacker.unpack(
            ByteArrayInputStream(bytes), "pack.tar.bz2", "pack", work, { false }, {}
        )

        assertEquals(sha256(bytes), sha)
        assertArrayEquals(tokens, File(work, "tokens.txt").readBytes())
        assertArrayEquals(onnx, File(work, "en_US-test.onnx").readBytes())
        assertTrue(File(work, "espeak-ng-data/phontab").isFile)
        assertFalse(File(work, "README.md").exists())
        assertFalse(File(tmp.root, "escaped.txt").exists())
    }

    @Test
    fun `gzip pack unpacks with the same result`() = runBlocking {
        val bytes = archive { GzipCompressorOutputStream(it) }
        val work = tmp.newFolder("work-gz")

        val sha = VoicePackUnpacker.unpack(
            ByteArrayInputStream(bytes), "pack.tar.gz", "pack", work, { false }, {}
        )

        assertEquals(sha256(bytes), sha)
        assertArrayEquals(onnx, File(work, "en_US-test.onnx").readBytes())
    }

    @Test
    fun `cancellation stops the unpack and returns null`() = runBlocking {
        val bytes = archive { BZip2CompressorOutputStream(it) }
        val work = tmp.newFolder("work-cancel")

        val sha = VoicePackUnpacker.unpack(
            ByteArrayInputStream(bytes), "pack.tar.bz2", "pack", work, { true }, {}
        )

        assertNull(sha)
    }

    @Test
    fun `progress reports rise monotonically and stay within the archive size`() = runBlocking {
        val bytes = archive { BZip2CompressorOutputStream(it) }
        val reports = ArrayList<Long>()

        VoicePackUnpacker.unpack(
            ByteArrayInputStream(bytes), "pack.tar.bz2", "pack", tmp.newFolder("work-progress"),
            { false }, { reports.add(it) }
        )

        assertTrue("expected several progress reports for a 2 MB pack", reports.size >= 2)
        assertEquals(reports.sorted(), reports)
        assertTrue(reports.last() <= bytes.size)
    }

    @Test(expected = IOException::class)
    fun `an unknown archive type is rejected`() = runBlocking {
        VoicePackUnpacker.unpack(
            ByteArrayInputStream(ByteArray(8)), "pack.zip", "pack", tmp.newFolder("work-zip"), { false }, {}
        )
        Unit
    }
}
