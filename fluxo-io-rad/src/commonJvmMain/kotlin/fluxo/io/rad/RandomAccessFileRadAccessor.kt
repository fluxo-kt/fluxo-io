@file:JvmMultifileClass
@file:JvmName("Rad")
@file:Suppress("FunctionName")

package fluxo.io.rad

import fluxo.io.internal.Blocking
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
 * @param size the length of the section
 */
@Blocking
@JvmOverloads
@JvmName("forRandomAccessFile")
public fun RandomAccessFileRadAccessor(
    data: RandomAccessFile,
    offset: Long = 0L,
    size: Long = -1L,
): RandomAccessData {
    val size0 = if (size == -1L) data.length() - offset else size
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
 * @param size the optional length of the section
 *
 * @TODO: Would it be better to use [RandomAccessFile.length] instead of [File.length]?
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
    size: Long = data.length() - offset,
): RandomAccessData =
    RandomAccessFileRadAccessor(RandomAccessFile(data, "r"), offset = offset, size = size)
