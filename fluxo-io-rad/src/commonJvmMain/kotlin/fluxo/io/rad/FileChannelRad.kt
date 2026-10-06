package fluxo.io.rad

import fluxo.io.internal.AccessorAwareRad
import fluxo.io.internal.RadHandle
import fluxo.io.internal.SharedDataAccessor
import fluxo.io.nio.readAtMost
import fluxo.io.rad.FileChannelRad.FileChannelAccess
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.ClosedChannelException
import java.nio.channels.FileChannel
import java.nio.channels.WritableByteChannel
import javax.annotation.concurrent.ThreadSafe

/**
 * [RandomAccessData] implementation backed by a [FileChannel].
 *
 * @param access provides access to the underlying channel
 * @param offset the offset of the section
 * @param size the length of the section
 */
@ThreadSafe
internal class FileChannelRad
private constructor(access: FileChannelAccess, offset: Long, size: Long, owner: RadHandle? = null) :
    AccessorAwareRad<FileChannelAccess>(access, offset, size, owner) {

    constructor(
        channel: FileChannel,
        offset: Long,
        size: Long,
        resources: Array<out AutoCloseable>,
    ) : this(FileChannelAccess(channel, resources), offset, size)


    override fun view0(
        access: FileChannelAccess, globalPosition: Long, length: Long, owner: RadHandle?,
    ) = FileChannelRad(access, globalPosition, length, owner)

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
    ): Long {
        val srcLen = size
        if (srcLen == 0L) {
            return 0L
        }
        val offset = offset
        var position = 0L
        while (true) {
            val written = access.transferTo(position + offset, srcLen - position, channel)
            if (written <= 0) {
                // FileChannel.transferTo returns 0 when the file shrank below the position or
                // the target is a non-blocking channel with no room; retrying would spin forever.
                throw IOException(
                    "transferTo made no progress at $position of $srcLen: the file was " +
                        "truncated or the target channel is non-blocking",
                )
            }
            position += written
            if (position == srcLen) {
                break
            }
        }
        return position
    }


    // Every channel call holds a lease: a concurrent close defers closing the channel until
    // in-flight reads end instead of aborting them with AsynchronousCloseException, and reads
    // after the last close fail with the same IOException as every other implementation.
    internal class FileChannelAccess(
        private val api: FileChannel,
        resources: Array<out AutoCloseable>,
    ) : SharedDataAccessor(resources) {

        override val size: Long = api.size()

        @Throws(IOException::class)
        override fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int =
            leased { it.read(ByteBuffer.wrap(bytes, offset, length), position) }

        @Throws(IOException::class)
        fun read(buffer: ByteBuffer, position: Long): Int = leased { it.read(buffer, position) }

        @Throws(IOException::class)
        fun transferTo(position: Long, count: Long, target: WritableByteChannel): Long =
            leased { it.transferTo(position, count, target) }

        /**
         * Under a lease only an interrupt can have closed the channel: a FileChannel closes
         * itself when any thread reading it is interrupted, for every handle sharing it. The
         * interrupted reader gets [ClosedByInterruptException]; everyone else gets this
         * explanation instead of a bare ClosedChannelException.
         */
        private inline fun <T> leased(block: (FileChannel) -> T): T = withLease {
            try {
                block(api)
            } catch (e: ClosedByInterruptException) {
                throw e
            } catch (e: ClosedChannelException) {
                // Thrown for a closed transferTo target too; that error is the caller's own.
                if (api.isOpen) throw e
                throw IOException(
                    "FileChannel was closed because a thread reading it was interrupted; " +
                        "open the data again, or use Rad.forRandomAccessFile where reader " +
                        "threads get interrupted",
                    e,
                )
            }
        }
    }
}
