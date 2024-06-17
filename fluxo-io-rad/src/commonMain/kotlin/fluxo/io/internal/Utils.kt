@file:Suppress("KDocUnresolvedReference")

package fluxo.io.internal

import kotlin.jvm.JvmField


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
 * Seems a bit faster on phones, than "Math.min/max"-based implementation.
 *
 * @see kotlin.ranges.coerceIn
 */
internal fun norm(num: Long, min: Long, max: Long) =
    if (num >= max) max else if (num >= min) num else min
