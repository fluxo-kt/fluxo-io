@file:Suppress("BlockingMethodInNonBlockingContext", "DEPRECATION")

package fluxo.io.rad

import androidx.annotation.RequiresApi
import fluxo.io.internal.AccessorAwareRad
import fluxo.io.internal.RadHandle
import fluxo.io.internal.SharedDataAccessor
import fluxo.io.nio.aRead
import fluxo.io.nio.readAtMost
import fluxo.io.rad.AsyncFileChannelRad.AsyncFileChannelAccess
import fluxo.io.util.checkPosOffsetAndMaxLength
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import javax.annotation.concurrent.ThreadSafe
import kotlin.math.min
import kotlinx.coroutines.runBlocking

// TODO: Avoid runBlocking usage, block without coroutine suspension (use Semaphore?)

/**
 * [RandomAccessData] implementation backed by a [AsynchronousFileChannel].
 *
 * WARNING: [AsynchronousFileChannel] is super slow for all platforms.
 * Seems to be the slowest possible IO API for JVM/Android.
 * Also, it often has [OutOfMemoryError] (Direct buffer memory) problems.
 *
 * @param access provides access to the underlying channel
 * @param offset the offset of the section
 * @param size the length of the section
 */
@ThreadSafe
@RequiresApi(26)
@Deprecated("Not recommended for usage, it's super slow and often has OOM problems.")
internal class AsyncFileChannelRad
private constructor(
    access: AsyncFileChannelAccess,
    offset: Long,
    size: Long,
    owner: RadHandle? = null,
) :
    AccessorAwareRad<AsyncFileChannelAccess>(access, offset, size, owner) {

    /**
     * Create a new [AsyncFileChannelRad] backed by the specified [channel].
     * @param channel the underlying channel
     */
    constructor(
        channel: AsynchronousFileChannel,
        offset: Long,
        size: Long,
    ) : this(AsyncFileChannelAccess(channel), offset, size)


    override val hasNativeBufferRead: Boolean get() = true

    override fun view0(
        access: AsyncFileChannelAccess, globalPosition: Long, length: Long, owner: RadHandle?,
    ) = AsyncFileChannelRad(access, globalPosition, length, owner)

    override suspend fun readAsync0(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int {
        checkPosOffsetAndMaxLength(size, buffer, position, offset, maxLength)
        val buf = ByteBuffer.wrap(buffer, offset, min(maxLength, buffer.size - offset))
        return readAsync0(buf, position)
    }


    override fun read0(buffer: ByteBuffer, position: Long): Int {
        return runBlocking {
            readAsync0(buffer, position)
        }
    }

    override suspend fun readAsync0(buffer: ByteBuffer, position: Long): Int {
        val srcLen = size
        if (position < 0L) {
            throw IndexOutOfBoundsException("srcPos=$position, srcLen=$srcLen")
        }
        if (position >= srcLen) {
            return -1
        }
        return buffer.readAtMost(srcLen - position) { access.read(it, offset + position) }
    }


    internal class AsyncFileChannelAccess(
        private val api: AsynchronousFileChannel,
    ) : SharedDataAccessor(resources = arrayOf(api)) {

        override val size: Long = api.size()

        @Throws(IOException::class)
        override fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int =
            runBlocking { read(ByteBuffer.wrap(bytes, offset, length), position) }

        // The lease spans the suspension, so the channel cannot be closed under a pending read.
        @Throws(IOException::class)
        suspend fun read(buffer: ByteBuffer, position: Long): Int =
            withLease { api.aRead(buffer, position) }
    }
}
