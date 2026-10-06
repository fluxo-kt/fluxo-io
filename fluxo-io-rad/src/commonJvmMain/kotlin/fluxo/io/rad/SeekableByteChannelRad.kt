package fluxo.io.rad

import androidx.annotation.RequiresApi
import fluxo.io.internal.AccessorAwareRad
import fluxo.io.internal.RadHandle
import fluxo.io.internal.SharedDataAccessor
import fluxo.io.nio.readAtMost
import fluxo.io.rad.SeekableByteChannelRad.SeekableChannelAccess
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import javax.annotation.concurrent.ThreadSafe

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
private constructor(
    access: SeekableChannelAccess,
    offset: Long,
    size: Long,
    owner: RadHandle? = null,
) :
    AccessorAwareRad<SeekableChannelAccess>(access, offset, size, owner) {

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


    override val hasNativeBufferRead: Boolean get() = true

    override fun view0(
        access: SeekableChannelAccess, globalPosition: Long, length: Long, owner: RadHandle?,
    ) = SeekableByteChannelRad(access, globalPosition, length, owner)

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


    internal class SeekableChannelAccess(
        private val api: SeekableByteChannel,
        resources: Array<out AutoCloseable>,
    ) : SharedDataAccessor(resources) {

        private val monitor = Any()

        override val size: Long = api.size()

        // The monitor serialises seek+read on the shared channel position; the lease keeps the
        // channel open until the read ends.
        @Throws(IOException::class)
        override fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int =
            read(ByteBuffer.wrap(bytes, offset, length), position)

        @Throws(IOException::class)
        internal fun read(buffer: ByteBuffer, position: Long): Int = withLease {
            synchronized(monitor) {
                api.position(position)
                api.read(buffer)
            }
        }
    }
}
