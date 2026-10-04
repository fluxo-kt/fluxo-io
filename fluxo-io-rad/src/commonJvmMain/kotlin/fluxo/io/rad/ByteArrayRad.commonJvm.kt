package fluxo.io.rad

import fluxo.io.internal.BasicRad
import fluxo.io.internal.RadHandle
import fluxo.io.internal.Blocking
import fluxo.io.internal.ThreadSafe
import fluxo.io.nio.writeFully
import fluxo.io.util.EMPTY_BYTE_ARRAY
import fluxo.io.util.MAX_BYTE
import fluxo.io.util.checkOffsetAndCount
import fluxo.io.util.checkPosOffsetAndMaxLength
import fluxo.io.util.checkPositionAndMaxLength
import fluxo.io.util.toIntChecked
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel
import java.util.Arrays
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
private constructor(
    private val array: ByteArray,
    private val offset: Int,
    private val length: Int,
    owner: RadHandle?,
) : BasicRad(owner) {

    actual constructor(array: ByteArray, offset: Int, length: Int) :
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


    @Blocking
    override fun readAllBytes0(): ByteArray =
        Arrays.copyOfRange(array, offset, offset + length)

    @Blocking
    override fun readFrom0(position: Long, maxLength: Int): ByteArray {
        checkPositionAndMaxLength(size = size, position = position, maxLength = maxLength)
        val positionInt = position.toInt()
        val len = min(maxLength, length - positionInt)
        if (len <= 0) {
            return EMPTY_BYTE_ARRAY
        }
        val pos = this.offset + positionInt
        return Arrays.copyOfRange(array, pos, pos + len)
    }

    @Blocking
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
        System.arraycopy(this.array, pos, buffer, offset, len)
        return len
    }


    @Suppress("MagicNumber")
    override fun readByteAt0(position: Long): Int =
        array[offset + position.toInt()].toInt() and MAX_BYTE


    @Blocking
    override fun read0(buffer: ByteBuffer, position: Long): Int {
        val srcLen = length
        if (position < 0L) {
            throw IndexOutOfBoundsException("srcPos=$position, srcLen=$srcLen")
        }
        if (position >= srcLen) {
            return -1
        }
        val positionInt = position.toInt()
        val destLen = buffer.remaining()
        val len = min(srcLen - positionInt, destLen)
        if (len <= 0) {
            return 0
        }
        val pos = offset + positionInt
        buffer.put(array, pos, len)
        return len
    }


    override fun transferTo0(
        channel: WritableByteChannel, bufferSize: Int, directBuffer: Boolean,
    ): Long {
        val srcLen = length
        if (srcLen == 0) {
            return 0L
        }
        channel.writeFully(ByteBuffer.wrap(array, offset, srcLen))
        return srcLen.toLong()
    }

    override fun transferTo0(stream: OutputStream, bufferSize: Int): Long {
        val srcLen = length
        if (srcLen != 0) {
            stream.write(array, offset, srcLen)
        }
        return srcLen.toLong()
    }
}
