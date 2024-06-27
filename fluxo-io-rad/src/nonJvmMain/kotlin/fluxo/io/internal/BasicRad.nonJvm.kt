@file:Suppress("RedundantSuppression")

package fluxo.io.internal

import fluxo.io.rad.RandomAccessData
import fluxo.io.rad.RadByteArrayAccessor
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
    actual override fun readAllBytes(): ByteArray = readAllBytesImpl()

    @Blocking
    actual override fun readFully(
        buffer: ByteArray,
        position: Long,
        offset: Int,
        maxLength: Int,
    ): Int = readFullyImpl(buffer, position, offset, maxLength)


    @Blocking
    actual override suspend fun readAsync(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int {
        @Suppress("BlockingMethodInNonBlockingContext")
        return read(buffer, position, offset, maxLength)
    }

    actual override suspend fun readFullyAsync(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int {
        return readFullyAsyncImpl(buffer, position, offset, maxLength)
    }
}
