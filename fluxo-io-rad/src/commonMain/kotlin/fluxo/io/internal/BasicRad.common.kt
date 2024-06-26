package fluxo.io.internal

import fluxo.io.rad.RandomAccessData
import fluxo.io.rad.RadByteArrayAccessor

/**
 * Common methods for [RadByteArrayAccessor] implementations.
 */
@ThreadSafe
@InternalFluxoIoApi
internal expect abstract class BasicRad
internal constructor() : RandomAccessData {

    @Blocking
    override fun readAllBytes(): ByteArray

    @Blocking
    override fun readFully(buffer: ByteArray, position: Long, offset: Int, maxLength: Int): Int


    override suspend fun readAsync(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int

    override suspend fun readFullyAsync(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int
}
