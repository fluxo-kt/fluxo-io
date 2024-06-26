@file:JvmName("Rad")
@file:Suppress("FunctionName")

package fluxo.io.rad

import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads


// RandomAccessData constructors


/**
 * Creates a new [RandomAccessData] instance backed by the given [ByteArray].
 *
 * @param base the underlying data array
 * @param offset the offset of the section
 * @param size the length of the section
 */
@JvmOverloads
@JvmName("forArray")
public fun RadByteArrayAccessor(
    base: ByteArray,
    offset: Int = 0,
    size: Int = base.size - offset,
): RandomAccessData = ByteArrayRad(base, offset, size)
