package fluxo.io.rad

import fluxo.io.IOException
import fluxo.io.util.MAX_INT_LONG
import fluxo.io.util.normIn
import java.io.InputStream
import javax.annotation.concurrent.NotThreadSafe
import kotlin.math.min

/**
 * [InputStream] implementation that wraps the [RadByteArrayAccessor].
 *
 * @see java.io.ByteArrayInputStream
 */
@NotThreadSafe
internal class RandomAccessDataInputStream(
    private val rad: RandomAccessData,
) : InputStream() {

    private var position = 0L
    private var mark = 0L

    override fun available(): Int {
        // TODO: Can it cause Int overflow?
        return (rad.size - position).normIn(0L, MAX_INT_LONG).toInt()
    }

    @Throws(IOException::class)
    override fun read(): Int {
        val read = rad.readByteAt(position)
        if (read > -1) {
            moveOn(1)
        }
        return read
    }

    /**
     * Perform the actual read.
     *
     * @param b the bytes to read or `null` when reading a single byte
     * @param off the offset of the byte array
     * @param len the length of data to read
     * @return the number of bytes read into `b` or the actual read byte if
     * `b` is `null`. Returns -1 when the end of the stream is reached.
     *
     * @throws IOException in case of I/O errors
     */
    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) {
            return 0
        }
        val cappedLen = cap(len.toLong())
        return when {
            cappedLen <= 0L -> -1
            else -> {
                val read = rad.read(b, position, off, cappedLen.toInt())
                when {
                    read <= 0 -> read
                    else -> moveOn(read.toLong()).toInt()
                }
            }
        }
    }

    override fun skip(n: Long): Long =
        if (n <= 0L) 0L else moveOn(cap(n))


    /**
     * Cap the specified value such that it can't exceed the number of bytes remaining.
     * @param n the value to cap
     * @return the capped value
     */
    private fun cap(n: Long): Long =
        min(rad.size - position, n)

    /**
     * Move the stream position forwards the specified amount.
     * @param amount the amount to move
     * @return the amount moved
     */
    private fun moveOn(amount: Long): Long {
        position += amount
        return amount
    }


    override fun markSupported(): Boolean = true

    override fun mark(readAheadLimit: Int) {
        mark = position
    }

    override fun reset() {
        position = mark
    }
}
