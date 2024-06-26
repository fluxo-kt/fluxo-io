@file:Suppress("KDocUnresolvedReference")

package fluxo.io.rad

import fluxo.io.IOException
import fluxo.io.internal.Blocking
import fluxo.io.internal.InternalFluxoIoApi
import fluxo.io.internal.ThreadSafe

@ThreadSafe
@SubclassOptInRequired(InternalFluxoIoApi::class)
public actual interface RandomAccessData : AutoCloseable {

    public actual val size: Long


    public actual fun subsection(position: Long, length: Long): RandomAccessData


    @Blocking
    @Throws(IOException::class)
    public actual fun readAllBytes(): ByteArray

    @Blocking
    @Throws(IOException::class)
    public actual fun readFrom(position: Long, maxLength: Int): ByteArray


    @Blocking
    @Throws(IOException::class)
    public actual fun read(
        buffer: ByteArray,
        position: Long,
        offset: Int,
        maxLength: Int,
    ): Int

    @Blocking
    @Throws(IOException::class)
    public actual fun readFully(
        buffer: ByteArray,
        position: Long,
        offset: Int,
        maxLength: Int,
    ): Int


    public actual suspend fun readAsync(
        buffer: ByteArray,
        position: Long,
        offset: Int,
        maxLength: Int,
    ): Int

    public actual suspend fun readFullyAsync(
        buffer: ByteArray,
        position: Long,
        offset: Int,
        maxLength: Int,
    ): Int
}
