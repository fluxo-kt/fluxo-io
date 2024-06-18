package fluxo.io.rad

import fluxo.io.internal.Blocking
import fluxo.io.internal.InternalForInheritanceApi
import fluxo.io.internal.ThreadSafe

/**
 * Common methods for [RandomAccessData] implementations.
 */
@ThreadSafe
@SubclassOptInRequired(InternalForInheritanceApi::class)
public expect abstract class BasicRad
public constructor() : RandomAccessData {

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
