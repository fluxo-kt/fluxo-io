@file:Suppress("KDocUnresolvedReference")

package fluxo.io

import fluxo.io.nio.markCompat
import fluxo.io.nio.resetCompat
import fluxo.io.util.EMPTY_BYTE_ARRAY
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Tests compile against the build JDK's API but run on JDK 8 too (the `jdk8` CI lane): wrap any
 * JDK 9+ call in `if (JAVA_9_PLUS)`. JDK 8 reports "1.8", later JDKs "9", "17", ….
 */
val JAVA_9_PLUS: Boolean = !System.getProperty("java.specification.version").startsWith("1.")

/**
 * Reads this stream completely into a byte array.
 *
 * **Note**: It is the caller's responsibility to close this stream.
 *
 * @param size Expected size of the data. Will use [readBytes] when [size] == `-1`.
 *
 * @see readBytesExact
 * @see kotlin.io.readBytes
 * @see java.io.DataInputStream.readFully
 * @see java.io.InputStream.readNBytes
 *
 * @throws EOFException On unexpected stream end or when stream is not ended on expected size.
 * @throws IllegalArgumentException When [size] < `-1`
 */
@Throws(IOException::class)
fun InputStream.readBytesFully(size: Int): ByteArray {
    if (size < 0) {
        require(size == -1) { "size < -1: $size" }

        // simple reading of stream with unknown size
        return readBytes()
    }

    if (size == 0) {
        return EMPTY_BYTE_ARRAY
    }

    var offset = 0
    var remaining = size
    val result = ByteArray(remaining)
    while (remaining > 0) {
        val read = read(result, offset, remaining)
        if (read < 0) {
            break
        }
        remaining -= read
        offset += read
    }
    if (remaining > 0) {
        throw EOFException("$offset < expected size: $size")
    }
    if (read() != -1) {
        throw EOFException("Stream is not ended at expected size: $size")
    }
    return result
}

/**
 * Reads up to a specified number of bytes from the input stream.
 *
 * **Note**: It is the caller's responsibility to close this stream.
 *
 * @param size Expected size of the data.
 * @param strict Allows disabling exception on the unexpected end of the stream.
 *
 * @see readBytesFully
 * @see java.io.InputStream.readNBytes
 *
 * @throws EOFException On unexpected stream end or when stream
 */
@Throws(IOException::class)
fun InputStream.readBytesExact(size: Int, strict: Boolean = true): ByteArray {
    if (size == 0) {
        return EMPTY_BYTE_ARRAY
    }
    val result = ByteArray(size)
    var offset = 0
    var read: Int
    while (offset < size) {
        read = read(result, offset, size - offset)
        if (read < 0) {
            if (strict) {
                throw EOFException("$offset < expected size: $size")
            }
            return result.copyOf(offset)
        }
        offset += read
    }
    return result
}


fun ByteBuffer.toArray(): ByteArray {
    val array = ByteArray(remaining())
    markCompat()
    get(array)
    resetCompat()
    return array
}

