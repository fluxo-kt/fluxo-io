@file:JvmName("Rad")
@file:Suppress("FunctionName")

package fluxo.io.rad

import androidx.annotation.RequiresApi
import fluxo.io.internal.toIntChecked
import java.io.Closeable
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode


// RandomAccessData constructors


// region ByteBuffer

/**
 * Creates a new [RandomAccessData] instance backed by the given [ByteBuffer].
 *
 * @param data the underlying [ByteBuffer]
 * @param offset the offset of the section
 * @param size the length of the section
 * @param resource an additional optional resource to close when the data is no longer needed.
 */
@JvmOverloads
@JvmName("forByteBuffer")
public fun RadByteBufferAccessor(
    data: ByteBuffer,
    offset: Int = data.position(),
    size: Int = data.limit() - offset,
    resource: Closeable? = null,
): RandomAccessData = ByteBufferRad(data, offset = offset, size = size, resource = resource)

/**
 * Creates a new [ByteBuffer]-based [RandomAccessData] instance backed by the given [array][data].
 *
 * @param data the underlying data array
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


private fun RadByteBufferAccessor0(
    data: FileInputStream,
    offset: Long,
    size: Int,
): RandomAccessData {
    val channel = data.channel
    val byteBuffer = channel.map(MapMode.READ_ONLY, offset, size.toLong())
    return ByteBufferRad(byteBuffer, offset = 0, size = size, resource = data)
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
 * @param data the underlying [FileChannel]
 * @param offset the offset of the section
 * @param size the length of the section
 *
 * @see java.nio.channels.FileChannel.map
 */
@JvmOverloads
@JvmName("forByteBuffer")
public fun RadByteBufferAccessor(
    data: FileInputStream,
    offset: Long = 0L,
): RandomAccessData {
    val stream = FileInputStream(data)
    val channel = stream.channel
    channel.map(MapMode.READ_ONLY, offset.toLong(), size.toLong())
    return RadByteBufferAccessor0(channel, offset = offset, size = size, resource = stream)
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
 * @param data the underlying [FileChannel]
 * @param offset the offset of the section
 * @param size the length of the section
 *
 * @see java.nio.channels.FileChannel.map
 */
@JvmOverloads
@JvmName("forByteBuffer")
public fun RadByteBufferAccessor(
    data: FileChannel,
    offset: Long = 0L,
    size: Int = (data.size() - offset).toIntChecked(),
): RandomAccessData = ByteBufferRad(data, offset = offset, size = size, resource = data)

/**
 * Creates a new memory-mapping [ByteBuffer]-based [RandomAccessData] instance
 * from the given [File].
 *
 * It uses memory-mapped IO for the given section
 * (maps a region of this channel's file directly into memory).
 *
 * **WARNING: Only files under [2 GB][Int.MAX_VALUE] can be mapped into memory!**
 *
 * @param data the underlying [FileChannel]
 * @param offset the offset of the section
 * @param size the length of the section
 *
 * @see java.nio.channels.FileChannel.map
 */
@JvmOverloads
@JvmName("forByteBuffer")
public fun RadByteBufferAccessor(
    data: File,
    offset: Long = 0L,
    size: Int = (data.length() - offset).toIntChecked(),
): RandomAccessData {
    val stream = FileInputStream(data)
    return ByteBufferRad(stream.channel, offset = offset, size = size, resource = stream)
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
 * @param data the underlying [FileChannel]
 * @param offset the offset of the section
 * @param size the length of the section
 *
 * @see java.nio.channels.FileChannel.map
 */
@JvmOverloads
@JvmName("forByteBuffer")
public fun RadByteBufferAccessor(
    data: FileDescriptor,
    offset: Long = 0L,
): RandomAccessData {
    val stream = FileInputStream(data)
    val channel = stream.channel
    val size = (channel.size() - offset).toIntChecked()
    return ByteBufferRad(channel, offset = offset, size = size, resource = stream)
}

@JvmOverloads
@JvmName("forMemoryMappedFile")
@Deprecated(
    message = "Use RadByteBufferAccessor instead",
    replaceWith = ReplaceWith("RadByteBufferAccessor"),
)
public fun RadMemoryMappedAccessor(
    data: FileChannel,
    offset: Int = 0,
    size: Int = data.size().toIntChecked(),
): RandomAccessData = RadByteBufferAccessor(data, offset = offset, size = size)

@JvmOverloads
@JvmName("forMemoryMappedFile")
@Deprecated(
    message = "Use RadByteBufferAccessor instead",
    replaceWith = ReplaceWith("RadByteBufferAccessor"),
)
public fun RadMemoryMappedAccessor(
    data: File,
    offset: Int = 0,
    size: Int = (data.length() - offset).toIntChecked(),
): RandomAccessData = RadByteBufferAccessor(data, offset = offset, size = size)

// endregion ByteBuffer


// region AsynchronousFileChannel

/**
 * Creates a new [RandomAccessData] instance backed by the given [AsynchronousFileChannel].
 *
 * WARNING: [AsynchronousFileChannel] is super slow for all platforms.
 * Seems to be the slowest possible IO API for JVM/Android.
 * Also, it often has [OutOfMemoryError] (Direct buffer memory) problems.
 *
 * @param data the underlying channel
 * @param offset the offset of the section
 * @param size the length of the section
 */
@JvmOverloads
@RequiresApi(26)
@JvmName("forAsyncFileChannel")
@Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")
@Deprecated("Not recommended for usage, super slow and often has OOM problems.")
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
 * @param data the underlying file
 * @param offset the offset of the section
 * @param size the length of the section
 */
@JvmOverloads
@RequiresApi(26)
@JvmName("forAsyncFileChannel")
@Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")
@Deprecated("Not recommended for usage, super slow and often has OOM problems.")
public fun RadAsyncFileChannelAccessor(
    data: File,
    offset: Long = 0L,
    size: Long = data.length() - offset,
): RandomAccessData = AsyncFileChannelRad(data, offset = offset, size = size)

// endregion AsynchronousFileChannel


// region InputStreamFromRad

/**
 * Returns [InputStream] implementation wrapping the [RandomAccessData].
 *
 * **The caller is responsible for closing the stream when it is finished!**
 */
@JvmName("asInputStream")
public fun InputStreamFromRad(rad: RandomAccessData): InputStream = InputStreamRad(rad)

/**
 * Returns [InputStream] implementation wrapping the [RandomAccessData].
 *
 * **The caller is responsible for closing the stream when it is finished!**
 */
@Deprecated(
    message = "Use InputStreamFromRad instead",
    replaceWith = ReplaceWith("InputStreamFromRad"),
    level = DeprecationLevel.ERROR,
)
@JvmName("asInputStreamOld")
public fun RadInputStream(rad: RandomAccessData): InputStream = InputStreamFromRad(rad)

// endregion InputStreamFromRad
