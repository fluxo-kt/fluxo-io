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
