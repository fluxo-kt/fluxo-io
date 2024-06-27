@file:JvmMultifileClass
@file:JvmName("Rad")
@file:Suppress("FunctionName")

package fluxo.io.rad

import java.io.InputStream


/**
 * Returns [InputStream] implementation wrapping the [RandomAccessData].
 *
 * **WARNING: Remember to close the stream when finished!**
 */
@JvmName("asInputStream")
public fun InputStreamFromRad(rad: RandomAccessData): InputStream = RandomAccessDataInputStream(rad)

/**
 * Returns [InputStream] implementation wrapping the [RandomAccessData].
 *
 * **WARNING: Remember to close the stream when finished!**
 *
 * @see InputStreamFromRad
 */
@Deprecated(
    message = "Use InputStreamFromRad instead",
    replaceWith = ReplaceWith("InputStreamFromRad"),
    level = DeprecationLevel.ERROR,
)
@JvmName("asInputStreamOld")
public fun RadInputStream(rad: RandomAccessData): InputStream = InputStreamFromRad(rad)
