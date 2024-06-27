@file:JvmMultifileClass
@file:JvmName("Rad")
@file:Suppress("FunctionName", "DeprecatedCallableAddReplaceWith", "DEPRECATION")

package fluxo.io.rad

import androidx.annotation.RequiresApi
import fluxo.io.internal.Blocking
import java.io.File
import java.nio.channels.AsynchronousFileChannel


/**
 * Creates a new [RandomAccessData] instance backed by the given [AsynchronousFileChannel].
 *
 * WARNING: [AsynchronousFileChannel] is super slow for all platforms.
 * Seems to be the slowest possible IO API for JVM/Android.
 * Also, it often has [OutOfMemoryError] (Direct buffer memory) problems.
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * @param data the underlying channel
 * @param offset the offset of the section
 * @param size the length of the section
 */
@Blocking
@JvmOverloads
@RequiresApi(26)
@JvmName("forAsyncFileChannel")
@Deprecated("Not recommended for usage, slow and can has OOM problems.")
public fun RadAsyncFileChannelAccessor(
    data: AsynchronousFileChannel,
    offset: Long = 0L,
    size: Long = data.size() - offset,
): RandomAccessData = AsyncFileChannelRad(data, offset = offset, size = size)

/**
 * Creates a new [RandomAccessData] instance backed by the given [file][data].
 *
 * WARNING: [AsynchronousFileChannel] is super slow for all platforms.
 * Seems to be the slowest possible IO API for JVM/Android.
 * Also, it often has [OutOfMemoryError] (Direct buffer memory) problems.
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * @param data the underlying file
 * @param offset the offset of the section
 * @param size the length of the section
 */
@Blocking
@JvmOverloads
@RequiresApi(26)
@JvmName("forAsyncFileChannel")
@Deprecated("Not recommended for usage, slow and can has OOM problems.")
public fun RadAsyncFileChannelAccessor(
    data: File,
    offset: Long = 0L,
    size: Long = data.length() - offset,
): RandomAccessData = AsyncFileChannelRad(data, offset = offset, size = size)
