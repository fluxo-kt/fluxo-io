@file:Suppress("KDocUnresolvedReference")

package fluxo.io.internal

import fluxo.io.EOFException
import fluxo.io.rad.RandomAccessData
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmField
import kotlin.math.min


@JvmField
internal val EMPTY_BYTE_ARRAY = ByteArray(0)


/**
 * Helper method for bound check.
 *
 * @see java.util.Arrays.checkOffsetAndCount
 */
internal fun checkOffsetAndCount(dataLength: Int, offset: Int, count: Int) {
    if (offset or count < 0 || offset > dataLength || dataLength - offset < count) {
        throw IndexOutOfBoundsException(
            "dataLength=$dataLength; regionStart=$offset; regionLength=$count",
        )
    }
}

/**
 * Helper method for bound check.
 *
 * @see java.util.Arrays.checkOffsetAndCount
 */
internal fun checkOffsetAndCount(dataLength: Long, offset: Long, count: Long) {
    if (offset or count < 0L || offset > dataLength || dataLength - offset < count) {
        throw IndexOutOfBoundsException(
            "dataLength=$dataLength; regionStart=$offset; regionLength=$count",
        )
    }
}


internal fun checkPositionAndMaxLength(size: Long, position: Long, maxLength: Int) {
    if (position < 0L || maxLength < 0 || position > size) {
        throw IndexOutOfBoundsException("srcPos=$position, maxLen=$maxLength, srcLen=$size")
    }
}

internal fun checkPosOffsetAndMaxLength(
    size: Long,
    buffer: ByteArray,
    position: Long,
    offset: Int,
    maxLength: Int,
) {
    val destLen = buffer.size
    if (position < 0L || offset or maxLength < 0 || offset > destLen) {
        throw IndexOutOfBoundsException(
            "srcPos=$position, destPos=$offset, maxLen=$maxLength" +
                ", srcLen=$size, destLen=$destLen",
        )
    }
}

internal fun calcLength(
    srcLen: Long,
    buffer: ByteArray,
    position: Long,
    offset: Int,
    maxLength: Int,
): Int {
    checkPosOffsetAndMaxLength(srcLen, buffer, position, offset, maxLength)
    if (position >= srcLen) {
        return -1
    }
    val destLen = buffer.size
    val len =
        min(srcLen - position, min(maxLength, destLen - offset).toLong()).toInt()
    return if (len <= 0) 0 else len
}


internal fun RandomAccessData.readAllBytesImpl(): ByteArray {
    val srcLen = size.toIntChecked()
    if (srcLen == 0) {
        return EMPTY_BYTE_ARRAY
    }
    return ByteArray(size = srcLen).also { result ->
        check(readFully(result, position = 0L, offset = 0, srcLen) == srcLen) {
            "Failed to read $srcLen bytes from $this"
        }
    }
}


internal fun RandomAccessData.readFullyImpl(
    buffer: ByteArray,
    position: Long,
    offset: Int,
    maxLength: Int,
): Int = readFully0(size, buffer, position, offset, maxLength) { pos, offs, maxLen ->
    read(buffer, pos, offs, maxLen)
}

internal suspend fun RandomAccessData.readFullyAsyncImpl(
    buffer: ByteArray,
    position: Long,
    offset: Int,
    maxLength: Int,
): Int = readFully0(size, buffer, position, offset, maxLength) { pos, offs, maxLen ->
    readAsync(buffer, pos, offs, maxLen)
}

@Suppress("LongParameterList")
private inline fun readFully0(
    size: Long,
    buffer: ByteArray,
    position: Long,
    offset: Int,
    maxLength: Int,
    readCall: (pos: Long, offset: Int, maxLen: Int) -> Int,
): Int {
    contract {
        callsInPlace(readCall, InvocationKind.UNKNOWN)
    }
    val len = calcLength(size, buffer, position, offset, maxLength)
    if (len <= 0) {
        return len
    }
    var n = 0
    do {
        val count = readCall(position + n, offset + n, len - n)
        if (count < 0) {
            throw EOFException(
                "Unexpected end of data at ${position + n}, expected $size bytes",
            )
        }
        n += count
    } while (n < len)
    return n
}



@Throws(ArithmeticException::class)
internal fun Long.toIntChecked(): Int {
    val result = toInt()
    if (this != result.toLong()) {
        throw ArithmeticException("Long -> Int overflow: $this -> $result")
    }
    return result
}

/**
 * Ensures that the value lies in the specified range [min]..[max].
 *
 * Seems a bit faster on phones than "Math.min/max"-based implementation.
 *
 * @see kotlin.ranges.coerceIn
 */
internal fun Long.normIn(min: Long, max: Long) =
    if (this >= max) max else if (this >= min) this else min


internal const val MAX_INT_LONG = Int.MAX_VALUE.toLong()
