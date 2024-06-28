package fluxo.io.rad

import fluxo.io.internal.BasicRad
import fluxo.io.internal.Blocking
import fluxo.io.internal.ThreadSafe

/**
 * [RandomAccessData] implementation backed by a [ByteArray].
 *
 * @param array the underlying data
 * @param offset the offset of the section
 * @param length the length of the section
 */
@ThreadSafe
internal expect class ByteArrayRad(
    array: ByteArray,
    offset: Int,
    length: Int,
) : BasicRad {

    override val size: Long

    @Blocking
    override fun subsection(position: Long, length: Long): RandomAccessData

    @Blocking
    override fun readAllBytes(): ByteArray

    @Blocking
    override fun readFrom(position: Long, maxLength: Int): ByteArray

    @Blocking
    override fun read(buffer: ByteArray, position: Long, offset: Int, maxLength: Int): Int
}
