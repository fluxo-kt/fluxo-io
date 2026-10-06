package fluxo.io.rad

import fluxo.io.IOException
import fluxo.io.internal.AccessorAwareRad
import fluxo.io.internal.RadHandle
import fluxo.io.internal.SharedDataAccessor
import fluxo.io.nio.limitCompat
import fluxo.io.nio.positionCompat
import fluxo.io.nio.readAtMost
import fluxo.io.nio.releaseCompat
import fluxo.io.nio.writeFully
import fluxo.io.rad.ByteBufferRad.ByteBufferAccess
import fluxo.io.util.EMPTY_AUTO_CLOSEABLE_ARRAY
import fluxo.io.util.MAX_BYTE
import fluxo.io.util.toIntChecked
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.WritableByteChannel
import javax.annotation.concurrent.ThreadSafe
import kotlin.math.min

/**
 * [RandomAccessData] implementation backed by a [ByteBuffer].
 * Can be used for memory-mapped IO via [FileChannel] or direct buffer access.
 *
 * Reads never move the shared buffer's cursor (absolute `get` or a private `duplicate()` view),
 * so concurrent reads run in parallel without a monitor. Each read holds a lease, so the
 * buffer is unmapped/freed only after the last in-flight read ends: no use-after-unmap crash.
 *
 * @param access provides access to the underlying buffer
 * @param offset the offset of the section
 * @param size the length of the section
 */
@ThreadSafe
internal class ByteBufferRad
private constructor(access: ByteBufferAccess, offset: Int, size: Int, owner: RadHandle? = null) :
    AccessorAwareRad<ByteBufferAccess>(access, offset.toLong(), size.toLong(), owner) {

    constructor(array: ByteArray, offset: Int, size: Int)
        : this(ByteBuffer.wrap(array), offset, size, EMPTY_AUTO_CLOSEABLE_ARRAY)

    constructor(
        buffer: ByteBuffer,
        offset: Int,
        size: Int,
        resources: Array<out AutoCloseable>,
        ownsBuffer: Boolean = false,
    ) : this(ByteBufferAccess(buffer, resources, ownsBuffer), offset, size)


    override fun view0(
        access: ByteBufferAccess, globalPosition: Long, length: Long, owner: RadHandle?,
    ) = ByteBufferRad(access, globalPosition.toInt(), length.toInt(), owner)

    private fun toAccessPos(position: Long) =
        (offset + position).toIntChecked()


    override fun readByteAt0(position: Long): Int =
        access.readByteAt(toAccessPos(position))

    @Throws(IOException::class)
    override fun read0(buffer: ByteBuffer, position: Long): Int {
        val srcLen = size
        if (position < 0L) {
            throw IndexOutOfBoundsException("srcPos=$position, srcLen=$srcLen")
        }
        if (position >= srcLen) {
            return -1
        }
        return buffer.readAtMost(srcLen - position) { access.read(it, offset + position) }
    }

    @Throws(IOException::class)
    override fun transferTo0(
        channel: WritableByteChannel, bufferSize: Int, directBuffer: Boolean,
    ): Long = access.transferTo(offset.toInt(), size.toInt(), channel)


    /**
     * [ownsBuffer]: the buffer was mapped here, so close unmaps it. A caller's buffer is never
     * freed: they may still use it, and touching a freed direct buffer kills the JVM. Callers who
     * want it freed pass `{ buffer.releaseCompat() }` in `resources`.
     */
    internal class ByteBufferAccess(
        private val api: ByteBuffer,
        resources: Array<out AutoCloseable>,
        private val ownsBuffer: Boolean = false,
    ) : SharedDataAccessor(resources) {

        // The data ends at the limit: bytes past it are not the caller's data, and absolute
        // `get` (readByteAt) refuses them, so every read path stops there too.
        private val limit = api.limit()
        override val size: Long = limit.toLong()

        fun readByteAt(position: Int): Int = withLease {
            api.get(position).toInt() and MAX_BYTE
        }

        @Throws(IndexOutOfBoundsException::class)
        override fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int =
            withLease {
                val buf = api.duplicate()
                buf.limitCompat(limit)
                buf.positionCompat(position.toInt())
                val len = min(buf.remaining(), length)
                buf.get(bytes, offset, len)
                len
            }

        @Throws(IOException::class)
        internal fun read(buffer: ByteBuffer, position: Long): Int = withLease {
            val pos = position.toInt()
            val buf = api.duplicate()
            val len = min(limit - pos, buffer.remaining())
            buf.limitCompat(pos + len)
            buf.positionCompat(pos)
            buffer.put(buf)
            len
        }

        @Throws(IOException::class)
        internal fun transferTo(position: Int, count: Int, channel: WritableByteChannel): Long {
            val len = min(limit - position, count)
            if (len == 0) {
                return 0
            }
            withLease {
                val buf = api.duplicate()
                buf.limitCompat(position + len)
                buf.positionCompat(position)
                channel.writeFully(buf)
            }
            return len.toLong()
        }

        // Runs only after the last lease ended (see SharedCloseable), so no read can be
        // touching the buffer while it is unmapped.
        override fun releaseApi() {
            if (ownsBuffer) api.releaseCompat()
        }
    }
}
