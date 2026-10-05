package fluxo.io.rad

import fluxo.io.internal.BasicRad
import fluxo.io.internal.RadHandle
import fluxo.io.util.EMPTY_BYTE_ARRAY
import fluxo.io.util.checkOffsetAndCount
import fluxo.io.util.checkPosOffsetAndMaxLength
import fluxo.io.util.checkPositionAndMaxLength
import fluxo.io.util.toIntChecked
import kotlin.math.min

internal actual fun byteArrayRad(array: ByteArray, offset: Int, length: Int): RandomAccessData =
    ByteArrayRad(array, offset, length)

/**
 * [RandomAccessData] implementation backed by a [ByteArray].
 *
 * @param array the underlying data
 * @param offset the offset of the section
 * @param length the length of the section
 */
internal class ByteArrayRad
private constructor(
    private val array: ByteArray,
    private val offset: Int,
    private val length: Int,
    owner: RadHandle?,
) : BasicRad(owner) {

    constructor(array: ByteArray, offset: Int, length: Int) :
        this(array, offset, length, owner = null)

    override val size: Long get() = length.toLong()

    init {
        checkOffsetAndCount(array.size, offset, length)
    }


    override fun view(position: Long, length: Long, owner: RadHandle?): RandomAccessData =
        ByteArrayRad(array, offset + position.toIntChecked(), length.toInt(), owner)

    // A heap array has nothing to retain or release: the GC frees it once unreachable.
    override fun acquireShared() {}

    override fun releaseShared() {}


    override fun readAllBytes0(): ByteArray =
        array.copyOfRange(offset, offset + length)

    override fun readFrom0(position: Long, maxLength: Int): ByteArray {
        checkPositionAndMaxLength(size = size, position = position, maxLength = maxLength)
        val positionInt = position.toInt()
        val len = min(maxLength, length - positionInt)
        if (len <= 0) {
            return EMPTY_BYTE_ARRAY
        }
        val pos = this.offset + positionInt
        return array.copyOfRange(pos, pos + len)
    }

    override fun read0(buffer: ByteArray, position: Long, offset: Int, maxLength: Int): Int {
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
}
