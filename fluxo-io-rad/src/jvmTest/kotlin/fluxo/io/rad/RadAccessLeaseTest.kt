package fluxo.io.rad

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every accessor method that touches its resource must go through the lease, so a read after
 * the last owner closed fails with the lease's error instead of touching a released resource
 * (unmapped buffer: SIGABRT; recycled fd: someone else's file). Handle-level tests cannot see
 * a missing lease: the handle's own closed check throws first. Resources the accessor does not
 * own stay open here, so only the lease can refuse the read.
 */
internal class RadAccessLeaseTest {

    private val file = File.createTempFile("lease", "tmp").apply { writeBytes(ByteArray(16)) }
    private val open = ArrayList<AutoCloseable>()

    @AfterTest
    fun cleanup() {
        open.forEach(AutoCloseable::close)
        file.delete()
    }

    private fun channel() = FileChannel.open(file.toPath()).also(open::add)

    @Test
    @Suppress("DEPRECATION")
    fun closedAccessorsRefuseEveryRead() {
        val calls = buildMap<String, () -> Any> {
            FileChannelRad.FileChannelAccess(channel(), emptyArray()).closed().let {
                this["FileChannel.read(bytes)"] = { it.read(ByteArray(1), 0, 0, 1) }
                this["FileChannel.read(buffer)"] = { it.read(ByteBuffer.allocate(1), 0) }
                this["FileChannel.transferTo"] = { it.transferTo(0, 1, sink()) }
            }
            ByteBufferRad.ByteBufferAccess(ByteBuffer.allocate(16), emptyArray()).closed().let {
                this["ByteBuffer.readByteAt"] = { it.readByteAt(0) }
                this["ByteBuffer.read(bytes)"] = { it.read(ByteArray(1), 0, 0, 1) }
                this["ByteBuffer.read(buffer)"] = { it.read(ByteBuffer.allocate(1), 0) }
                this["ByteBuffer.transferTo"] = { it.transferTo(0, 1, sink()) }
            }
            SeekableByteChannelRad.SeekableChannelAccess(channel(), emptyArray()).closed().let {
                this["Seekable.read(bytes)"] = { it.read(ByteArray(1), 0, 0, 1) }
                this["Seekable.read(buffer)"] = { it.read(ByteBuffer.allocate(1), 0) }
            }
            // These own their resource, so a missing lease fails with the resource's own error.
            RandomAccessFileRad.RafAccess(RandomAccessFile(file, "r")).closed().let {
                this["Raf.read(bytes)"] = { it.read(ByteArray(1), 0, 0, 1) }
                this["Raf.readByte"] = { it.readByte(0) }
            }
            AsyncFileChannelRad.AsyncFileChannelAccess(AsynchronousFileChannel.open(file.toPath()))
                .closed()
                .let { this["AsyncFileChannel.read(bytes)"] = { it.read(ByteArray(1), 0, 0, 1) } }
        }
        val unleased = calls.filter { (_, call) ->
            val e = try {
                call()
                return@filter true
            } catch (e: IOException) {
                e
            }
            e.message != LEASE_REFUSED
        }.keys
        assertEquals(emptySet(), unleased, "these reach the resource without a lease")
    }

    private fun <T : AutoCloseable> T.closed(): T = apply { close() }

    private fun sink() = Channels.newChannel(ByteArrayOutputStream())

    private companion object {
        const val LEASE_REFUSED = "RandomAccessData is already closed"
    }
}
