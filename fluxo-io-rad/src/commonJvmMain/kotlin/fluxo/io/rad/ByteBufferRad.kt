package fluxo.io.rad

import fluxo.io.Closeable
import fluxo.io.IOException
import fluxo.io.internal.AccessorAwareRad
import fluxo.io.internal.SharedDataAccessor
import fluxo.io.internal.toIntChecked
import fluxo.io.nio.BufferUtil
import fluxo.io.nio.limitCompat
import fluxo.io.nio.positionCompat
import fluxo.io.rad.ByteBufferRad.ByteBufferAccess
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.WritableByteChannel
import javax.annotation.concurrent.ThreadSafe
import kotlin.math.min

/**
 * [RandomAccessData] implementation backed by a [ByteBuffer].
 * Can be used for memory-mapped IO via [FileChannel] or direct buffer access.
 *
 * @param access provides access to the underlying buffer
 * @param offset the offset of the section
 * @param size the length of the section
 */
@ThreadSafe
internal class ByteBufferRad
private constructor(access: ByteBufferAccess, offset: Int, size: Int) :
    AccessorAwareRad<ByteBufferAccess, ByteBufferRad>(
        access, offset.toLong(), size.toLong(),
    ) {

    constructor(array: ByteArray, offset: Int, size: Int)
        : this(ByteBuffer.wrap(array), offset, size, null)

    constructor(buffer: ByteBuffer, offset: Int, size: Int, resource: Closeable?)
        : this(ByteBufferAccess(buffer, resource), offset, size)


    override fun getSubsection0(
        access: ByteBufferAccess, globalPosition: Long, length: Long,
    ) = ByteBufferRad(access, globalPosition.toInt(), length.toInt())


    private fun toAccessPos(position: Long) = (offset + position).toIntChecked()


    override fun readByteAt0(position: Long): Int {
        return access.api.get(toAccessPos(position)).toInt() and 0xFF
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteBuffer, position: Long): Int {
        val srcLen = size
        if (position < 0L) {
            throw IndexOutOfBoundsException("srcPos=$position, srcLen=$srcLen")
        }
        if (position >= srcLen) {
            return -1
        }
        val bufLimit = buffer.limit()
        val bufPos = buffer.position()
        val destLen = (bufLimit - bufPos).toLong()
        val len = min(srcLen - position, destLen)
        if (len <= 0L) {
            return 0
        }
        if (destLen > len) {
            buffer.limitCompat(bufPos + len.toInt())
        }
        val read = access.read(buffer, offset + position)
        if (destLen > len) {
            buffer.limitCompat(bufLimit)
        }
        return read
    }

    @Throws(IOException::class)
    override fun transferTo(
        channel: WritableByteChannel, bufferSize: Int, directBuffer: Boolean,
    ): Long {
        return access.transferTo(offset.toInt(), size.toInt(), channel)
    }


    class ByteBufferAccess
    internal constructor(
        public override val api: ByteBuffer,
        private val resource: Closeable?,
    ) : SharedDataAccessor() {

        private val monitor = Any()

        override val size: Long get() = api.capacity().toLong()

        @Throws(IndexOutOfBoundsException::class)
        override fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int {
            val buf = api
            synchronized(monitor) {
                buf.limitCompat(buf.capacity())
                buf.positionCompat(position.toInt())
                val len = min(buf.remaining(), length)
                buf.get(bytes, offset, len)
                return len
            }
        }

        @Throws(IOException::class)
        internal fun read(buffer: ByteBuffer, position: Long): Int {
            val buf = api
            val pos = position.toInt()
            synchronized(monitor) {
                val capacity = buf.capacity()
                var len = capacity - pos
                buf.limitCompat(pos + len)
                buf.positionCompat(pos)
                val remaining = buffer.remaining()
                if (remaining < len) {
                    len = remaining
                    buf.limitCompat(pos + len)
                }
                buffer.put(buf)
                return len
            }
        }

        @Throws(IOException::class)
        internal fun transferTo(position: Int, count: Int, channel: WritableByteChannel): Long {
            val buf = api
            val len = min(buf.capacity() - position, count)
            if (len == 0) {
                return 0
            }
            synchronized(monitor) {
                buf.limitCompat(position + len)
                buf.positionCompat(position)
                var written = 0
                do {
                    written += channel.write(buf)
                } while (written < len)
            }
            return len.toLong()
        }

        override fun onSharedClose() {
            BufferUtil.releaseBuffer(api)
            resource?.closeQuietly()
        }
    }
}
