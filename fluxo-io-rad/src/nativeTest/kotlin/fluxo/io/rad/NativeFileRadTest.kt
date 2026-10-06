@file:OptIn(ExperimentalForeignApi::class)

package fluxo.io.rad

import fluxo.io.IOException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.addressOf
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite
import platform.posix.getenv
import platform.posix.remove

/**
 * The POSIX (`pread`) and Windows (`ReadFile`) file implementations over real temp files.
 * Files are written with C stdio, which every Native target has, so one test serves all.
 */
internal class NativeFileRadTest {

    @Test
    fun contract() {
        val paths = ArrayList<String>()
        try {
            RadContract.verify { bytes -> RandomAccessData.open(tempFile(bytes).also(paths::add)) }
        } finally {
            paths.forEach { remove(it) }
        }
    }

    @Test
    fun missingFileAndDirectoryFailAtOpen() {
        val missing = tempDir() + "/does-not-exist-" + Random.nextLong()
        assertFailsWith<IOException> { RandomAccessData.open(missing) }
        assertFailsWith<IOException> { RandomAccessData.open(tempDir()) }
    }

    /**
     * Over 260 characters, with `..` and mixed separators, resolving to a short path. Windows
     * needs `\\?\` beyond 260, and that prefix leaves `..` unresolved unless the path is resolved
     * first; POSIX resolves it natively.
     */
    @Test
    fun longPathWithDotDotSegmentsOpens() {
        val path = tempFile(RadContract.bytes())
        try {
            val dir = tempDir()
            val name = dir.substring(dir.lastIndexOfAny(charArrayOf('/', '\\')) + 1)
            var detour = dir
            while (detour.length < 300) detour += "/../$name"
            val long = detour + "/" + path.substring(dir.length + 1)
            val size = RandomAccessData.open(long).use { it.size }
            assertEquals(RadContract.bytes().size.toLong(), size)
        } finally {
            remove(path)
        }
    }

    private fun tempDir(): String =
        (getenv("TMPDIR") ?: getenv("TEMP") ?: getenv("TMP"))
            ?.toKString()?.trimEnd('/', '\\') ?: "/tmp"

    private fun tempFile(bytes: ByteArray): String {
        val path = tempDir() + "/fluxo-rad-" + Random.nextLong().toULong() + ".bin"
        val file = checkNotNull(fopen(path, "wb")) { "cannot create $path" }
        try {
            if (bytes.isNotEmpty()) {
                val written = bytes.usePinned {
                    fwrite(it.addressOf(0), 1u, bytes.size.convert(), file)
                }
                check(written.toInt() == bytes.size) { "short write to $path" }
            }
        } finally {
            fclose(file)
        }
        return path
    }
}
