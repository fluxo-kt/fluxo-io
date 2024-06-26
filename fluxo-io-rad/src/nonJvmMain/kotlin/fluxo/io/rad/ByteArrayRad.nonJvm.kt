package fluxo.io.rad

import fluxo.io.internal.BasicRad
import fluxo.io.internal.EMPTY_BYTE_ARRAY
import fluxo.io.internal.ThreadSafe
import fluxo.io.internal.checkOffsetAndCount
import fluxo.io.internal.checkPosOffsetAndMaxLength
import fluxo.io.internal.checkPositionAndMaxLength
import fluxo.io.internal.toIntChecked
import kotlin.math.min

/**
 * [RadByteArrayAccessor] implementation backed by a [ByteArray].
 *
 * @param array the underlying data
 * @param offset the offset of the section
 * @param length the length of the section
 */
@ThreadSafe
internal actual class ByteArrayRad
actual constructor(
    private val array: ByteArray,
    private val offset: Int,
    private val length: Int,
) : BasicRad() {

    override val size: Long get() = length.toLong()

    init {
        checkOffsetAndCount(array.size, offset, length)
    }


    override fun subsection(position: Long, length: Long): RandomAccessData {
        checkOffsetAndCount(size, position, length)
        return ByteArrayRad(array, offset + position.toIntChecked(), length.toInt())
    }


    override fun readAllBytes(): ByteArray =
        array.copyOfRange(offset, offset + length)

    override fun readFrom(position: Long, maxLength: Int): ByteArray {
        checkPositionAndMaxLength(size = size, position = position, maxLength = maxLength)
        val positionInt = position.toInt()
        val len = min(maxLength, length - positionInt)
        if (len <= 0) {
            return EMPTY_BYTE_ARRAY
        }
        val pos = this.offset + positionInt
        return array.copyOfRange(pos, pos + len)
    }

    override fun read(buffer: ByteArray, position: Long, offset: Int, maxLength: Int): Int {
        checkPosOffsetAndMaxLength(size, buffer, position, offset, maxLength)
        val srcLen = length
        if (position >= srcLen) {
            return -1
        }
        val destLen = buffer.size
        val positionInt = position.toInt()
        val len = min(maxLength, min(srcLen - positionInt, destLen - offset))
        if (len <= 0) {
            return 0
        }
        val pos = this.offset + positionInt
        array.copyInto(buffer, offset, pos, pos + len)
        return len
    }


    override fun close() {}
}
