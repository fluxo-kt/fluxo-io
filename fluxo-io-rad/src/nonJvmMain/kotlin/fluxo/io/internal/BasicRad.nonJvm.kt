@file:Suppress("RedundantSuppression")

package fluxo.io.internal

import fluxo.io.rad.RandomAccessData
import fluxo.io.util.readAllBytesImpl
import fluxo.io.util.readFullyAsyncImpl
import fluxo.io.util.readFullyImpl

/**
 * Common logic for [RandomAccessData] implementations
 */
@ThreadSafe
@InternalFluxoIoApi
internal actual abstract class BasicRad : RandomAccessData {

    @Blocking
    actual final override fun readAllBytes(): ByteArray = readAllBytes0()

    protected actual open fun readAllBytes0(): ByteArray = readAllBytesImpl()

    @Blocking
    actual final override fun readFrom(position: Long, maxLength: Int): ByteArray =
        readFrom0(position, maxLength)

    protected actual abstract fun readFrom0(position: Long, maxLength: Int): ByteArray

    @Blocking
    actual final override fun read(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int = read0(buffer, position, offset, maxLength)

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
    actual final override suspend fun readAsync(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int {
        @Suppress("BlockingMethodInNonBlockingContext")
        return read(buffer, position, offset, maxLength)
    }

    actual final override suspend fun readFullyAsync(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int {
        return readFullyAsyncImpl(buffer, position, offset, maxLength)
    }
}
