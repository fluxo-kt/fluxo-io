package fluxo.io.rad

import androidx.annotation.RequiresApi
import fluxo.io.internal.AccessorAwareRad
import fluxo.io.internal.SharedDataAccessor
import fluxo.io.nio.limitCompat
import fluxo.io.rad.SeekableByteChannelRad.SeekableChannelAccess
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import javax.annotation.concurrent.ThreadSafe
import kotlin.math.min

/**
 * [RandomAccessData] implementation backed by a NIO [SeekableByteChannel].
 *
 * **WARNING:
 * This implementation uses [synchronized] blocks to ensure thread safety!*
 *
 * @param access provides access to the underlying channel
 * @param offset the offset of the section
 * @param size the length of the section
 *
 * @see java.nio.channels.FileChannel
 * @see jdk.nio.zipfs.ByteArrayChannel
 */
@ThreadSafe
@RequiresApi(24)
internal class SeekableByteChannelRad
private constructor(access: SeekableChannelAccess, offset: Long, size: Long) :
    AccessorAwareRad<SeekableChannelAccess>(access, offset, size) {

    /**
     * Create a new [SeekableByteChannelRad] backed by the specified [channel].
     * @param channel the underlying channel
     */
    constructor(
        channel: SeekableByteChannel,
        offset: Long,
        size: Long,
        resources: Array<out AutoCloseable>,
    ) : this(SeekableChannelAccess(channel, resources), offset, size)


    override fun getSubsection0(
        access: SeekableChannelAccess, globalPosition: Long, length: Long,
    ) = SeekableByteChannelRad(access, globalPosition, length)


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


    internal class SeekableChannelAccess(
        private val api: SeekableByteChannel,
        resources: Array<out AutoCloseable>,
    ) : SharedDataAccessor(resources) {

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
