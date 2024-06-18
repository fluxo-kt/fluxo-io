@file:Suppress("RedundantSuppression")

package fluxo.io.rad

import fluxo.io.internal.Blocking
import fluxo.io.internal.InternalForInheritanceApi
import fluxo.io.internal.ThreadSafe
import fluxo.io.internal.readAllBytesImpl
import fluxo.io.internal.readFullyAsyncImpl
import fluxo.io.internal.readFullyImpl

/**
 * Common logic for [RandomAccessData] implementations
 */
@ThreadSafe
@SubclassOptInRequired(InternalForInheritanceApi::class)
public actual abstract class BasicRad : RandomAccessData {

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
