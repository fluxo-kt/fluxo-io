package fluxo.io.internal

import fluxo.io.rad.RandomAccessData
import fluxo.io.rad.RadByteArrayAccessor

/**
 * Common methods for [RandomAccessData] implementations.
 *
 * Public read methods are `final` and delegate to protected `…0` hooks, so rules applied
 * at the public layer cover every implementation's paths (see the platform actuals).
 */
@ThreadSafe
@InternalFluxoIoApi
internal expect abstract class BasicRad
internal constructor() : RandomAccessData {

    @Blocking
    final override fun readAllBytes(): ByteArray

    protected open fun readAllBytes0(): ByteArray

    @Blocking
    final override fun readFrom(position: Long, maxLength: Int): ByteArray

    protected abstract fun readFrom0(position: Long, maxLength: Int): ByteArray

    @Blocking
    final override fun read(buffer: ByteArray, position: Long, offset: Int, maxLength: Int): Int

    protected abstract fun read0(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int

    @Blocking
    final override fun readFully(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int


    final override suspend fun readAsync(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int

    final override suspend fun readFullyAsync(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int
}
