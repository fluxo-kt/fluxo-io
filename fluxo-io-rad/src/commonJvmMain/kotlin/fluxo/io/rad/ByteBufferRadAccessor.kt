@file:JvmMultifileClass
@file:JvmName("Rad")
@file:Suppress("FunctionName")

package fluxo.io.rad

import fluxo.io.internal.Blocking
import fluxo.io.util.checkOffsetAndCount
import fluxo.io.util.toIntChecked
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode


/**
 * Creates a new [RandomAccessData] instance backed by the given [ByteBuffer].
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * @param data the underlying [ByteBuffer]
 * @param offset the offset of the section
 * @param size the length of the section
 * @param resources the optional resources to close when finished
 */
@JvmOverloads
@JvmName("forByteBuffer")
public fun RadByteBufferAccessor(
    data: ByteBuffer,
    offset: Int = data.position(),
    size: Int = data.limit() - offset,
    vararg resources: AutoCloseable,
): RandomAccessData = ByteBufferRad(data, offset = offset, size = size, resources = resources)

/**
 * Creates a new [ByteBuffer]-based [RandomAccessData] instance backed by the given [array][data].
 *
 * @param data the underlying [ByteArray]
 * @param offset the offset of the section
 * @param size the length of the section
 */
@JvmOverloads
@JvmName("forByteBuffer")
public fun RadByteBufferAccessor(
    data: ByteArray,
    offset: Int = 0,
    size: Int = data.size - offset,
): RandomAccessData = ByteBufferRad(data, offset = offset, size = size)


private fun byteBufferMmapRad0(
    channel: FileChannel,
    offset: Long,
    size: Int,
    vararg resources: AutoCloseable,
): RandomAccessData {
    val dataLength = channel.size()
    val size0 = if (size == -1) (dataLength - offset).toIntChecked() else size
    checkOffsetAndCount(dataLength, offset, size0.toLong())
    val byteBuffer = channel.map(MapMode.READ_ONLY, offset, size0.toLong())
    return ByteBufferRad(byteBuffer, offset = 0, size = size0, resources = resources)
}

/**
 * Creates a new memory-mapping [ByteBuffer]-based [RandomAccessData] instance
 * from the given [FileInputStream].
 *
 * It uses memory-mapped IO for the given section
 * (maps a region of this channel's file directly into memory).
 *
 * **WARNING: Only files under [2 GB][Int.MAX_VALUE] can be mapped into memory!**
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * @param data the underlying [FileInputStream]
 * @param offset the optional offset of the section
 * @param size the optional length of the section. -1 means the rest of the file.
 *
 * @see java.nio.channels.FileChannel.map
 */
@Blocking
@JvmOverloads
@JvmName("forByteBuffer")
public fun RadByteBufferAccessor(
    data: FileInputStream,
    offset: Long = 0L,
    size: Int = -1,
): RandomAccessData {
    val channel = data.channel
    return byteBufferMmapRad0(channel, offset = offset, size = size, channel, data)
}

/**
 * Creates a new memory-mapping [ByteBuffer]-based [RandomAccessData] instance
 * from the given [FileChannel].
 *
 * It uses memory-mapped IO for the given section
 * (maps a region of this channel's file directly into memory).
 *
 * **WARNING: Only files under [2 GB][Int.MAX_VALUE] can be mapped into memory!**
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * @param data the underlying [FileChannel]
 * @param offset the offset of the section
 * @param size the optional length of the section. -1 means the rest of the file.
 *
 * @see java.nio.channels.FileChannel.map
 */
@Blocking
@JvmOverloads
@JvmName("forByteBuffer")
public fun RadByteBufferAccessor(
    data: FileChannel,
    offset: Long = 0L,
    size: Int = -1,
): RandomAccessData = byteBufferMmapRad0(data, offset = offset, size = size, data)

/**
 * Creates a new memory-mapping [ByteBuffer]-based [RandomAccessData] instance
 * for the given [File].
 *
 * It uses memory-mapped IO for the given section
 * (maps a region of this channel's file directly into memory).
 *
 * **WARNING: Only files under [2 GB][Int.MAX_VALUE] can be mapped into memory!**
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * @param data the underlying [File]
 * @param offset the offset of the section
 * @param size the optional length of the section. -1 means the rest of the file.
 *
 * @see java.nio.channels.FileChannel.map
 */
@Blocking
@JvmOverloads
@JvmName("forByteBuffer")
public fun RadByteBufferAccessor(
    data: File,
    offset: Long = 0L,
    size: Int = -1,
): RandomAccessData {
    val stream = FileInputStream(data)
    val channel = stream.channel
    return byteBufferMmapRad0(channel, offset = offset, size = size, channel, stream)
}

/**
 * Creates a new memory-mapping [ByteBuffer]-based [RandomAccessData] instance
 * from by the given [FileDescriptor].
 *
 * It uses memory-mapped IO for the given section
 * (maps a region of this channel's file directly into memory).
 *
 * **WARNING: Only files under [2 GB][Int.MAX_VALUE] can be mapped into memory!**
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * @param data the underlying [FileDescriptor]
 * @param offset the offset of the section
 * @param size the optional length of the section. -1 means the rest of the file.
 *
 * @see java.nio.channels.FileChannel.map
 */
@Blocking
@JvmOverloads
@JvmName("forByteBuffer")
public fun RadByteBufferAccessor(
    data: FileDescriptor,
    offset: Long = 0L,
    size: Int = -1,
): RandomAccessData {
    val stream = FileInputStream(data)
    val channel = stream.channel
    return byteBufferMmapRad0(channel, offset = offset, size = size, channel, stream)
}


@Blocking
@JvmOverloads
@JvmName("forMemoryMappedFile")
@Deprecated(
    message = "Use RadByteBufferAccessor instead",
    replaceWith = ReplaceWith("RadByteBufferAccessor"),
)
public fun RadMemoryMappedAccessor(
    data: FileChannel,
    offset: Long = 0L,
    size: Int = -1,
): RandomAccessData = RadByteBufferAccessor(data, offset = offset, size = size)

@Blocking
@JvmOverloads
@JvmName("forMemoryMappedFile")
@Deprecated(
    message = "Use RadByteBufferAccessor instead",
    replaceWith = ReplaceWith("RadByteBufferAccessor"),
)
public fun RadMemoryMappedAccessor(
    data: File,
    offset: Long = 0L,
    size: Int = -1,
): RandomAccessData = RadByteBufferAccessor(data, offset = offset, size = size)
