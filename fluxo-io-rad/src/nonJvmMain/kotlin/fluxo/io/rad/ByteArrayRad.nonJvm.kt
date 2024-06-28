package fluxo.io.rad

import fluxo.io.internal.BasicRad
import fluxo.io.internal.Blocking
import fluxo.io.internal.ThreadSafe
import fluxo.io.util.EMPTY_BYTE_ARRAY
import fluxo.io.util.checkOffsetAndCount
import fluxo.io.util.checkPosOffsetAndMaxLength
import fluxo.io.util.checkPositionAndMaxLength
import fluxo.io.util.toIntChecked
import kotlin.math.min

/**
 * [RandomAccessData] implementation backed by a [ByteArray].
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

    actual override val size: Long get() = length.toLong()

    init {
        checkOffsetAndCount(array.size, offset, length)
    }


    @Blocking
    actual override fun subsection(position: Long, length: Long): RandomAccessData {
        checkOffsetAndCount(size, position, length)
        return ByteArrayRad(array, offset + position.toIntChecked(), length.toInt())
    }


    @Blocking
    actual override fun readAllBytes(): ByteArray =
        array.copyOfRange(offset, offset + length)

    @Blocking
    actual override fun readFrom(position: Long, maxLength: Int): ByteArray {
        checkPositionAndMaxLength(size = size, position = position, maxLength = maxLength)
        val positionInt = position.toInt()
        val len = min(maxLength, length - positionInt)
        if (len <= 0) {
            return EMPTY_BYTE_ARRAY
        }
        val pos = this.offset + positionInt
        return array.copyOfRange(pos, pos + len)
    }

    @Blocking
    actual override fun read(buffer: ByteArray, position: Long, offset: Int, maxLength: Int): Int {
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


    actual override fun close() {}
}
