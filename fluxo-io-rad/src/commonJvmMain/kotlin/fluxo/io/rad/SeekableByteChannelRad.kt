package fluxo.io.rad

import androidx.annotation.RequiresApi
import fluxo.io.internal.AccessorAwareRad
import fluxo.io.internal.SharedDataAccessor
import fluxo.io.nio.limitCompat
import fluxo.io.rad.SeekableByteChannelRad.SeekableChannelAccess
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import javax.annotation.concurrent.ThreadSafe
import kotlin.math.min

/**
 * [RadByteArrayAccessor] implementation backed by a [SeekableByteChannel].
 *
 * @param access provides access to the underlying channel
 * @param offset the offset of the section
 * @param size the length of the section
 */
@ThreadSafe
@RequiresApi(24)
internal class SeekableByteChannelRad
private constructor(access: SeekableChannelAccess, offset: Long, size: Long) :
    AccessorAwareRad<SeekableChannelAccess, SeekableByteChannelRad>(
        access, offset, size,
    ) {

    /**
     * Create a new [SeekableByteChannelRad] backed by the specified [channel].
     * @param channel the underlying channel
     */
    constructor(
        channel: FileChannel,
        offset: Long = 0L,
        size: Long = channel.size() - offset,
    ) : this(SeekableChannelAccess(channel), offset, size)

    /**
     * Create a new [SeekableByteChannelRad] backed
     * by the channel for specified [stream].
     *
     * @param stream file stream to create a channel from.
     */
    constructor(stream: FileInputStream, offset: Long = 0L) : this(stream.channel, offset)

    /**
     * Create a new [SeekableByteChannelRad] backed
     * by the channel for specified [FileDescriptor].
     *
     * @param fd the underlying file stream
     */
    constructor(fd: FileDescriptor, offset: Long = 0L)
        : this(FileInputStream(fd).channel, offset)

    /**
     * Create a new [SeekableByteChannelRad] backed
     * by the channel for specified [file].
     *
     * @param file the file to open a channel from.
     */
    constructor(file: File, offset: Long = 0L, size: Long = file.length() - offset)
        : this(FileInputStream(file).channel, offset, size)


    override fun getSubsection0(
        access: SeekableChannelAccess, globalPosition: Long, length: Long,
    ): SeekableByteChannelRad =
        SeekableByteChannelRad(access, globalPosition, length)


    @Suppress("ReturnCount")
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


    internal class SeekableChannelAccess
    internal constructor(
        override val api: SeekableByteChannel,
    ) : SharedDataAccessor() {

        private val monitor = Any()

        override val size: Long get() = api.size()

        @Throws(IOException::class)
        override fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int {
            val api = api
            synchronized(monitor) {
                api.position(position)
                return api.read(ByteBuffer.wrap(bytes, offset, length))
            }
        }

        @Throws(IOException::class)
        internal fun read(buffer: ByteBuffer, position: Long): Int {
            val api = api
            synchronized(monitor) {
                api.position(position)
                return api.read(buffer)
            }
        }
    }
}
