@file:Suppress("RedundantSuppression")

package fluxo.io.internal

import fluxo.io.rad.RandomAccessData
import fluxo.io.util.readAllBytesImpl
import fluxo.io.util.readFullyImpl

/**
 * Common logic for [RandomAccessData] implementations
 */
@ThreadSafe
@InternalFluxoIoApi
internal actual abstract class BasicRad
internal actual constructor(owner: RadHandle?) : RadHandle(owner) {

    @Blocking
    actual final override fun readAllBytes(): ByteArray {
        ensureOpen()
        return readAllBytes0()
    }

    protected actual open fun readAllBytes0(): ByteArray = readAllBytesImpl()

    @Blocking
    actual final override fun readFrom(position: Long, maxLength: Int): ByteArray {
        ensureOpen()
        return readFrom0(position, maxLength)
    }

    protected actual abstract fun readFrom0(position: Long, maxLength: Int): ByteArray

    @Blocking
    actual final override fun read(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int {
        ensureOpen()
        return read0(buffer, position, offset, maxLength)
    }

    protected actual abstract fun read0(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int

    @Blocking
    actual final override fun readFully(
        buffer: ByteArray,
        position: Long,
        offset: Int,
        maxLength: Int,
    ): Int = readFullyImpl(buffer, position, offset, maxLength)


    @Blocking
    @Deprecated("Suspends but blocks the calling thread for the whole read. Use asAsync(dispatcher), e.g. rad.asAsync(Dispatchers.IO).read(…).", level = DeprecationLevel.ERROR)
    actual final override suspend fun readAsync(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int {
        @Suppress("BlockingMethodInNonBlockingContext")
        return read(buffer, position, offset, maxLength)
    }

    @Deprecated("Suspends but blocks the calling thread for the whole read. Use asAsync(dispatcher), e.g. rad.asAsync(Dispatchers.IO).read(…).", level = DeprecationLevel.ERROR)
    actual final override suspend fun readFullyAsync(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int = readFully(buffer, position, offset, maxLength)
}
