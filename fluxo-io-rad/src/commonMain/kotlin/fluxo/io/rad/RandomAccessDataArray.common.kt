package fluxo.io.rad

import fluxo.io.internal.ThreadSafe

/**
 * [RandomAccessData] implementation backed by a [ByteArray].
 *
 * @param array the underlying data
 * @param offset the offset of the section
 * @param length the length of the section
 */
@ThreadSafe
public expect class RandomAccessDataArray(
    array: ByteArray,
    offset: Int = 0,
    length: Int = array.size - offset,
) : BasicRad
