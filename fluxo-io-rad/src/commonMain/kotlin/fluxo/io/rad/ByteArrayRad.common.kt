package fluxo.io.rad

import fluxo.io.internal.BasicRad
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
) : BasicRad
