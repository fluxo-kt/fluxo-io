@file:JvmMultifileClass
@file:JvmName("Rad")
@file:Suppress("FunctionName")

package fluxo.io.rad

import fluxo.io.internal.Blocking
import fluxo.io.rad.StreamFactoryRad.ByteChannelFactory
import fluxo.io.rad.StreamFactoryRad.DataInputFactory
import fluxo.io.rad.StreamFactoryRad.InputStreamFactory
import fluxo.io.util.checkOffsetAndCount
import java.io.DataInput
import java.io.File
import java.io.InputStream
import java.nio.channels.ReadableByteChannel


private const val DEFAULT_MAX_POOL_SIZE = 15


/**
 * Creates a new [RandomAccessData] instance based on the [InputStream] Factory
 * from the given [File].
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * @param data the underlying [File]
 * @param offset the offset of the section
 * @param size the length of the section
 * @param maxPoolSize the maximum number of streams to keep open
 */
@Blocking
@JvmOverloads
@JvmName("forStreamFactory")
public fun StreamFactoryRadAccessor(
    data: File,
    offset: Long = 0L,
    size: Long = -1L,
    maxPoolSize: Int = DEFAULT_MAX_POOL_SIZE,
): RandomAccessData {
    val fullSize = data.length()
    val size0 = if (size == -1L) fullSize - offset else size
    val factory = InputStreamFactory(fullSize, data::inputStream)
    return StreamFactoryRad(factory, offset = offset, size = size0, maxPoolSize = maxPoolSize)
}

/**
 * Creates a new [RandomAccessData] instance based on the given [InputStream] [factory].
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * @param fullSize the size of the underlying data
 * @param offset the offset of the section
 * @param size the length of the section
 * @param factory the [InputStream] factory
 */
@Blocking
@JvmOverloads
@JvmName("forInputStreamFactory")
public fun StreamFactoryRadAccessor(
    fullSize: Long,
    offset: Long = 0L,
    size: Long = fullSize - offset,
    maxPoolSize: Int = DEFAULT_MAX_POOL_SIZE,
    factory: () -> InputStream,
): RandomAccessData {
    checkOffsetAndCount(fullSize, offset, size)
    return StreamFactoryRad(
        factory = InputStreamFactory(size, factory),
        offset = offset,
        size = size,
        maxPoolSize = maxPoolSize,
    )
}

/**
 * Creates a new [RandomAccessData] instance based on the given [DataInput] [factory].
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * @param fullSize the size of the underlying data
 * @param offset the offset of the section
 * @param size the length of the section
 * @param factory the [DataInput] factory
 */
@Blocking
@JvmOverloads
@JvmName("forDataInputFactory")
public fun DataInputFactoryRadAccessor(
    fullSize: Long,
    offset: Long = 0L,
    size: Long = fullSize - offset,
    maxPoolSize: Int = DEFAULT_MAX_POOL_SIZE,
    factory: () -> DataInput,
): RandomAccessData {
    checkOffsetAndCount(fullSize, offset, size)
    return StreamFactoryRad(
        factory = DataInputFactory(size, factory),
        offset = offset,
        size = size,
        maxPoolSize = maxPoolSize,
    )
}

/**
 * Creates a new [RandomAccessData] instance based on the given NIO [ReadableByteChannel] [factory].
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * @param fullSize the size of the underlying data
 * @param offset the offset of the section
 * @param size the length of the section
 * @param factory the NIO [ReadableByteChannel] factory
 */
@Blocking
@JvmOverloads
@JvmName("forByteChannelFactory")
public fun ByteChannelFactoryRadAccessor(
    fullSize: Long,
    offset: Long = 0L,
    size: Long = fullSize - offset,
    maxPoolSize: Int = DEFAULT_MAX_POOL_SIZE,
    factory: () -> ReadableByteChannel,
): RandomAccessData {
    checkOffsetAndCount(fullSize, offset, size)
    return StreamFactoryRad(
        factory = ByteChannelFactory(size, factory),
        offset = offset,
        size = size,
        maxPoolSize = maxPoolSize,
    )
}
