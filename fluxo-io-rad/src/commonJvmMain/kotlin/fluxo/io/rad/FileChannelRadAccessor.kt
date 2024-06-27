@file:JvmMultifileClass
@file:JvmName("Rad")
@file:Suppress("FunctionName")

package fluxo.io.rad

import fluxo.io.internal.Blocking
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.nio.channels.FileChannel


/**
 * Creates a new [RandomAccessData] instance backed by the given [FileChannel].
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * @param data the underlying [FileChannel]
 * @param offset the offset of the section
 * @param size the length of the section
 * @param resources the optional resources to close when finished
 */
@Blocking
@JvmOverloads
@JvmName("forFileChannel")
public fun RadFileChannelAccessor(
    data: FileChannel,
    offset: Long = 0L,
    size: Long = -1L,
    vararg resources: AutoCloseable = arrayOf(data),
): RandomAccessData {
    val size0 = if (size == -1L) data.size() - offset else size
    return FileChannelRad(data, offset = offset, size = size0, resources = resources)
}

/**
 * Creates a new [FileChannel]-based [RandomAccessData] instance
 * from the given [FileInputStream].
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * @param data the underlying [FileInputStream]
 * @param offset the optional offset of the section
 * @param size the optional length of the section
 */
@Blocking
@JvmOverloads
@JvmName("forFileChannel")
public fun RadFileChannelAccessor(
    data: FileInputStream,
    offset: Long = 0L,
    size: Long = -1L,
): RandomAccessData {
    val channel = data.channel
    return RadFileChannelAccessor(channel, offset = offset, size = size, channel, data)
}

/**
 * Creates a new [FileChannel]-based [RandomAccessData] instance
 * for the given [File].
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * @param data the underlying [File]
 * @param offset the optional offset of the section
 * @param size the optional length of the section
 *
 * @TODO: Would it be better to use [FileChannel.size] instead of [File.length]?
 */
@Blocking
@JvmOverloads
@JvmName("forFileChannel")
public fun RadFileChannelAccessor(
    data: File,
    offset: Long = 0L,
    size: Long = data.length() - offset,
): RandomAccessData = RadFileChannelAccessor(FileInputStream(data), offset = offset, size = size)

/**
 * Creates a new [FileChannel]-based [RandomAccessData] instance
 * from the given [FileDescriptor].
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * @param data the underlying [FileDescriptor]
 * @param offset the optional offset of the section
 * @param size the optional length of the section
 */
@Blocking
@JvmOverloads
@JvmName("forFileChannel")
public fun RadFileChannelAccessor(
    data: FileDescriptor,
    offset: Long = 0L,
    size: Long = -1L,
): RandomAccessData = RadFileChannelAccessor(FileInputStream(data), offset = offset, size = size)
