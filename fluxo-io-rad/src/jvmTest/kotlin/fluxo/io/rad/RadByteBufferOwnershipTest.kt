package fluxo.io.rad

import fluxo.io.nio.limitCompat
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

internal class RadByteBufferOwnershipTest {

    /**
     * Closing must not unmap a buffer the caller mapped: they may keep reading it, and a read
     * of an unmapped buffer faults (InternalError at best, a JVM crash at worst).
     */
    @Test
    fun closingLeavesTheCallersBufferUsable() {
        val file = File.createTempFile("owned", "tmp").apply { writeBytes(ByteArray(16) { 7 }) }
        try {
            val mapped = FileChannel.open(file.toPath()).use {
                it.map(FileChannel.MapMode.READ_ONLY, 0, 16)
            }
            RadByteBufferAccessor(mapped).close()
            assertEquals(7, mapped.get(15).toInt())
        } finally {
            file.delete()
        }
    }

    /** The data ends at the buffer's limit, for every read path, so a range past it fails. */
    @Test
    fun dataEndsAtTheLimitNotTheCapacity() {
        val buffer = ByteBuffer.allocate(16).apply { limitCompat(8) }
        assertFails { RadByteBufferAccessor(buffer, 0, 16) }
        RadByteBufferAccessor(buffer).use { assertEquals(8L, it.size) }
    }
}
