@file:JvmMultifileClass
@file:JvmName("Rad")
@file:Suppress("FunctionName")

package fluxo.io.rad

import fluxo.io.internal.Blocking
import fluxo.io.util.checkOffsetAndCount
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.nio.channels.SeekableByteChannel


/**
 * Creates a new [RandomAccessData] instance backed by the given NIO [SeekableByteChannel].
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * **WARNING:
 * This implementation uses [synchronized] blocks to ensure thread safety!*
 *
 * @param data the underlying [SeekableByteChannel]
 * @param offset the offset of the section
 * @param size the optional length of the section. -1 means the rest of the file.
 * @param resources the optional resources to close when finished
 *
 * @see java.nio.channels.FileChannel
 * @see jdk.nio.zipfs.ByteArrayChannel
 */
@Blocking
@JvmOverloads
@JvmName("forSeekableByteChannel")
public fun RadSeekableByteChannelAccessor(
    data: SeekableByteChannel,
    offset: Long = 0L,
    size: Long = -1L,
    vararg resources: AutoCloseable = arrayOf(data),
): RandomAccessData {
    val dataLength = data.size()
    val size0 = if (size == -1L) dataLength - offset else size
    checkOffsetAndCount(dataLength, offset, size0)
    return SeekableByteChannelRad(data, offset = offset, size = size0, resources = resources)
}

/**
 * Creates a new [SeekableByteChannel]-based [RandomAccessData] instance
 * from the given [FileInputStream].
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * **WARNING:
 * This implementation uses [synchronized] blocks to ensure thread safety!*
 *
 * @param data the underlying [FileInputStream]
 * @param offset the optional offset of the section
 * @param size the optional length of the section. -1 means the rest of the file.
 */
@Blocking
@JvmOverloads
@JvmName("forSeekableByteChannel")
public fun RadSeekableByteChannelAccessor(
    data: FileInputStream,
    offset: Long = 0L,
    size: Long = -1L,
): RandomAccessData {
    val channel = data.channel
    return RadSeekableByteChannelAccessor(channel, offset = offset, size = size, channel, data)
}

/**
 * Creates a new [SeekableByteChannel]-based [RandomAccessData] instance
 * for the given [File].
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * **WARNING:
 * This implementation uses [synchronized] blocks to ensure thread safety!*
 *
 * @param data the underlying [File]
 * @param offset the optional offset of the section
 * @param size the optional length of the section. -1 means the rest of the file.
 */
@Blocking
@JvmOverloads
@JvmName("forSeekableByteChannel")
public fun RadSeekableByteChannelAccessor(
    data: File,
    offset: Long = 0L,
    size: Long = -1L,
): RandomAccessData =
    RadSeekableByteChannelAccessor(FileInputStream(data), offset = offset, size = size)

/**
 * Creates a new [SeekableByteChannel]-based [RandomAccessData] instance
 * from the given [FileDescriptor].
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * **WARNING:
 * This implementation uses [synchronized] blocks to ensure thread safety!*
 *
 * @param data the underlying [FileDescriptor]
 * @param offset the optional offset of the section
 * @param size the optional length of the section. -1 means the rest of the file.
 */
@Blocking
@JvmOverloads
@JvmName("forSeekableByteChannel")
public fun RadSeekableByteChannelAccessor(
    data: FileDescriptor,
    offset: Long = 0L,
    size: Long = -1L,
): RandomAccessData =
    RadSeekableByteChannelAccessor(FileInputStream(data), offset = offset, size = size)
