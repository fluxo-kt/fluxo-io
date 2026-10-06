@file:OptIn(ExperimentalForeignApi::class)

package fluxo.io.rad

import fluxo.io.IOException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.toKString
import platform.posix.O_RDWR
import platform.posix.close
import platform.posix.getenv
import platform.posix.mkfifo
import platform.posix.open
import platform.posix.remove

/** POSIX-only file kinds: none has a fixed size to read at positions. */
internal class PosixFileRadTest {

    /**
     * A FIFO is rejected. The test holds it open read-write itself, so a regression fails here
     * instead of hanging in an open that waits for a writer.
     */
    @Test
    fun fifoIsRejected() {
        val dir = getenv("TMPDIR")?.toKString()?.trimEnd('/') ?: "/tmp"
        val path = "$dir/fluxo-rad-fifo-" + Random.nextLong().toULong()
        assertEquals(0, mkfifo(path, FIFO_MODE.convert()))
        val holder = open(path, O_RDWR)
        try {
            assertTrue(holder >= 0, "cannot hold the FIFO open")
            assertFailsWith<IOException> { RandomAccessData.open(path) }
        } finally {
            close(holder)
            remove(path)
        }
    }

    /** A device reports size 0: opening it would silently read as empty data. */
    @Test
    fun deviceIsRejected() {
        assertFailsWith<IOException> { RandomAccessData.open("/dev/zero") }
    }
}

private const val FIFO_MODE = 0x180 // 0600
