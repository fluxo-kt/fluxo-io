package fluxo.io.rad

/**
 * Creates a new [RandomAccessData] instance backed by the given [ByteArray].
 *
 * @param array the underlying data
 * @param offset the offset of the section
 * @param length the length of the section
 */
public fun RandomAccessData(
    array: ByteArray,
    offset: Int = 0,
    length: Int = array.size - offset,
) : RandomAccessData = RandomAccessDataArray(array, offset, length)
