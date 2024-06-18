package fluxo.io.rad

import fluxo.io.internal.AccessorAwareRad
import fluxo.io.internal.SharedDataAccessor
import fluxo.io.internal.toIntChecked
import fluxo.io.nio.*
import fluxo.io.rad.RandomAccessDataByteBuffer.ByteBufferAccess
import java.io.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import java.nio.channels.WritableByteChannel
import javax.annotation.concurrent.ThreadSafe
import kotlin.math.min

/**
 * [RandomAccessData] implementation backed by a [ByteBuffer].
 *
 * @param access provides access to the underlying buffer
 * @param offset the offset of the section
 * @param size the length of the section
 */
@ThreadSafe
internal class RandomAccessDataByteBuffer
private constructor(access: ByteBufferAccess, offset: Int, size: Int) :
    AccessorAwareRad<ByteBufferAccess, RandomAccessDataByteBuffer>(
        access, offset.toLong(), size.toLong(),
    ) {

    /**
     * Create a new [RandomAccessDataByteBuffer] backed by the specified [array].
     * @param array the underlying array
     */
    constructor(array: ByteArray, offset: Int = 0, size: Int = array.size - offset)
        : this(ByteBuffer.wrap(array), offset, size, null)

    /**
     * Create a new [RandomAccessDataByteBuffer] backed by the specified [buffer].
     * @param buffer the underlying buffer
     */
    constructor(
        buffer: ByteBuffer,
        offset: Int = buffer.position(),
        size: Int = buffer.limit() - offset,
        resource: Closeable? = null,
    ) : this(ByteBufferAccess(buffer, resource), offset, size)

    /**
     * Create a new [RandomAccessDataByteBuffer] backed by the memory mapped [channel].
     * @param channel the underlying channel
     */
    constructor(channel: FileChannel, offset: Int = 0, size: Int = channel.size().toIntChecked()) :
        this(
            ByteBufferAccess(
                channel.map(MapMode.READ_ONLY, offset.toLong(), size.toLong()), channel,
            ),
            offset, size,
        )

    /**
     * Create a new [RandomAccessDataByteBuffer] backed by the memory mapped [FileDescriptor].
     * @param fd the underlying channel
     */
    constructor(fd: FileDescriptor, offset: Int = 0) : this(FileInputStream(fd).channel, offset)

    /**
     * Create a new [RandomAccessDataByteBuffer] backed by the memory mapped [file].
     * @param file the underlying file
     */
    constructor(file: File, offset: Int = 0, size: Int = file.length().toIntChecked())
        : this(file.inputStream().channel, offset, size)


    override fun getSubsection0(
        access: ByteBufferAccess, globalPosition: Long, length: Long,
    ) = RandomAccessDataByteBuffer(access, globalPosition.toInt(), length.toInt())


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
