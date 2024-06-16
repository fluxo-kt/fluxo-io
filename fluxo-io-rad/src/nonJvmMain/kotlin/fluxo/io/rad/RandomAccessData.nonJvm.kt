@file:Suppress("KDocUnresolvedReference")

package fluxo.io.rad

import fluxo.io.internal.ThreadSafe
import kotlinx.io.IOException

@ThreadSafe
public actual interface RandomAccessData : AutoCloseable {

    public actual val size: Long


    public actual fun getSubsection(position: Long, length: Long): RandomAccessData


    @Throws(IOException::class)
    public actual fun readAllBytes(): ByteArray

    @Throws(IOException::class)
    public actual fun readFrom(position: Long, maxLength: Int): ByteArray


    @Throws(IOException::class)
    public actual fun read(
        buffer: ByteArray,
        position: Long,
        offset: Int,
        maxLength: Int,
    ): Int

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


    @Throws(IOException::class)
    public actual fun readByteAt(position: Long): Int
}
