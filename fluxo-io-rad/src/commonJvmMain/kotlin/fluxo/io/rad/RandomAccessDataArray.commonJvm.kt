package fluxo.io.rad

import fluxo.io.internal.EMPTY_BYTE_ARRAY
import fluxo.io.internal.ThreadSafe
import fluxo.io.internal.checkOffsetAndCount
import fluxo.io.internal.checkPosOffsetAndMaxLength
import fluxo.io.internal.checkPositionAndMaxLength
import fluxo.io.internal.toIntChecked
import java.io.ByteArrayInputStream
import java.io.InputStream
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
public actual class RandomAccessDataArray
actual constructor(
    private val array: ByteArray,
    private val offset: Int,
    private val length: Int,
) : BasicRad() {

    override val size: Long get() = length.toLong()

    init {
        checkOffsetAndCount(array.size, offset, length)
    }


    override fun getInputStream(): InputStream =
        ByteArrayInputStream(array, offset, length)

    override fun getSubsection(position: Long, length: Long): RandomAccessDataArray {
        checkOffsetAndCount(size, position, length)
        return RandomAccessDataArray(array, offset + position.toIntChecked(), length.toInt())
    }


    override fun readAllBytes(): ByteArray =
        Arrays.copyOfRange(array, offset, offset + length)

    override fun readFrom(position: Long, maxLength: Int): ByteArray {
        checkPositionAndMaxLength(size = size, position = position, maxLength = maxLength)
        val positionInt = position.toInt()
        val len = min(maxLength, length - positionInt)
        if (len <= 0) {
            return EMPTY_BYTE_ARRAY
        }
        val pos = this.offset + positionInt
        return Arrays.copyOfRange(array, pos, pos + len)
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
        System.arraycopy(this.array, pos, buffer, offset, len)
        return len
    }


    @Suppress("MagicNumber")
    override fun readByteAt0(position: Long): Int =
        array[offset + position.toInt()].toInt() and 0xFF


    override fun read(buffer: ByteBuffer, position: Long): Int {
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


    override fun transferTo(
        channel: WritableByteChannel, bufferSize: Int, directBuffer: Boolean,
    ): Long {
        val srcLen = length
        if (srcLen == 0) {
            return 0L
        }
        val buffer = ByteBuffer.wrap(array, offset, srcLen)
        var written = 0
        do {
            written += channel.write(buffer)
        } while (written < srcLen)
        return written.toLong()
    }

    override fun transferTo(stream: OutputStream, bufferSize: Int): Long {
        val srcLen = length
        if (srcLen != 0) {
            stream.write(array, offset, srcLen)
        }
        return srcLen.toLong()
    }


    override fun close() {}
}
