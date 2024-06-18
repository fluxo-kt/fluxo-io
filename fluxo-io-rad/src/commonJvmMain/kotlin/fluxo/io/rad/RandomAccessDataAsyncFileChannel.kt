@file:Suppress("BlockingMethodInNonBlockingContext", "DEPRECATION")

package fluxo.io.rad

import androidx.annotation.RequiresApi
import fluxo.io.internal.AccessorAwareRad
import fluxo.io.internal.SharedDataAccessor
import fluxo.io.internal.checkPosOffsetAndMaxLength
import fluxo.io.nio.aRead
import fluxo.io.nio.limitCompat
import fluxo.io.rad.RandomAccessDataAsyncFileChannel.AsyncFileChannelAccess
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import javax.annotation.concurrent.ThreadSafe
import kotlin.math.min
import kotlinx.coroutines.runBlocking

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
internal class RandomAccessDataAsyncFileChannel
private constructor(access: AsyncFileChannelAccess, offset: Long, size: Long) :
    AccessorAwareRad<AsyncFileChannelAccess, RandomAccessDataAsyncFileChannel>(
        access, offset, size,
    ) {

    /**
     * Create a new [RandomAccessDataAsyncFileChannel] backed by the specified [channel].
     * @param channel the underlying channel
     */
    constructor(
        channel: AsynchronousFileChannel, offset: Long = 0L, size: Long = channel.size() - offset,
    ) :
        this(AsyncFileChannelAccess(channel), offset, size)

    /**
     * Create a new [RandomAccessDataAsyncFileChannel] backed by the channel for specified [file].
     * @param file the underlying file
     */
    constructor(file: File, offset: Long = 0L, size: Long = file.length() - offset) :
        this(AsynchronousFileChannel.open(file.toPath()), offset, size)


    override fun getSubsection0(
        access: AsyncFileChannelAccess, globalPosition: Long, length: Long,
    ) = RandomAccessDataAsyncFileChannel(access, globalPosition, length)


    override suspend fun readAsync(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int {
        checkPosOffsetAndMaxLength(size, buffer, position, offset, maxLength)
        val buf = ByteBuffer.wrap(buffer, offset, min(maxLength, buffer.size - offset))
        return readAsync(buf, position)
    }


    override fun read(buffer: ByteBuffer, position: Long): Int {
        return runBlocking {
            readAsync(buffer, position)
        }
    }

    override suspend fun readAsync(buffer: ByteBuffer, position: Long): Int {
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
        val read = access.api.aRead(buffer, offset + position)
        if (destLen > len) {
            buffer.limitCompat(bufLimit)
        }
        return read
    }


    class AsyncFileChannelAccess
    internal constructor(public override val api: AsynchronousFileChannel) : SharedDataAccessor() {

        override val size: Long get() = api.size()

        @Throws(IOException::class)
        override fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int {
            return runBlocking {
                api.aRead(ByteBuffer.wrap(bytes, offset, length), position)
            }
        }
    }
}
