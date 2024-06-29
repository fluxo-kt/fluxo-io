@file:JvmMultifileClass
@file:JvmName("Rad")
@file:Suppress("FunctionName")

package fluxo.io.rad

import fluxo.io.internal.Blocking
import fluxo.io.util.checkOffsetAndCount
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile


/**
 * Creates a new [RandomAccessData] instance backed by the given [RandomAccessFile].
 *
 * **WARNING: Remember to close the [RandomAccessData] when finished
 * to properly release resources!*
 *
 * **WARNING:
 * This implementation uses [synchronized] blocks to ensure thread safety!*
 *
 * @param data the underlying [RandomAccessFile]
 * @param offset the offset of the section
 * @param size the optional length of the section. -1 means the rest of the file.
 */
@Blocking
@JvmOverloads
@JvmName("forRandomAccessFile")
public fun RandomAccessFileRadAccessor(
    data: RandomAccessFile,
    offset: Long = 0L,
    size: Long = -1L,
): RandomAccessData {
    val dataLength = data.length()
    val size0 = if (size == -1L) dataLength - offset else size
    checkOffsetAndCount(dataLength, offset, size0)
    return RandomAccessFileRad(data, offset = offset, size = size0)
}

/**
 * Creates a new [RandomAccessFile]-based [RandomAccessData] instance
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
 *
 * @throws IllegalArgumentException if the file doesn't exist
 */
@Blocking
@JvmOverloads
@JvmName("forRandomAccessFile")
@Throws(FileNotFoundException::class)
public fun RandomAccessFileRadAccessor(
    data: File,
    offset: Long = 0L,
    size: Long = -1L,
): RandomAccessData =
    RandomAccessFileRadAccessor(RandomAccessFile(data, "r"), offset = offset, size = size)
