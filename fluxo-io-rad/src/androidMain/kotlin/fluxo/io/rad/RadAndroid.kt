@file:JvmName("RadAndroid")

package fluxo.io.rad

import android.content.res.AssetFileDescriptor
import android.os.ParcelFileDescriptor
import fluxo.io.IOException
import fluxo.io.internal.Blocking
import java.io.FileInputStream
import java.io.InputStream

/*
 * Android descriptors from ContentResolver/AssetManager become RandomAccessData. A regular file
 * gets positional FileChannel reads; a pipe or socket (content providers may stream, e.g. cloud
 * documents) cannot be read at a position at all, so its bytes are read into memory once.
 * `statSize` tells the two apart up front (-1 for anything but a regular file), so no read is
 * attempted to find out.
 *
 * No device test: this file is glue over the FileChannel and ByteArray implementations that
 * the JVM and common tests already cover; the only Android-specific logic is that branch.
 */

/**
 * Opens [pfd] for random-access reads and takes ownership of it: closing the last handle closes
 * [pfd]. Returns a handle: close it once when finished.
 *
 * A non-seekable descriptor (pipe, socket) is read into memory here, once, at a cost of its
 * whole size in heap; a regular file is never copied.
 *
 * @throws IOException if reading fails
 */
@Blocking
@Throws(IOException::class)
public fun RandomAccessData.Companion.open(pfd: ParcelFileDescriptor): RandomAccessData {
    val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
    return if (pfd.statSize < 0) readIntoMemory(stream) else open(stream, offset = 0L, size = -1L)
}

/**
 * Opens the range of [afd] (`startOffset`, `declaredLength`; an unknown length means to the
 * end of the file) and takes ownership of it: closing the last handle closes [afd]. Assets must
 * be stored uncompressed in the APK to have a descriptor at all.
 *
 * A non-seekable descriptor (pipe, socket) is read into memory here, once.
 *
 * @throws IOException if reading fails
 */
@Blocking
@Throws(IOException::class)
public fun RandomAccessData.Companion.open(afd: AssetFileDescriptor): RandomAccessData {
    val stream = afd.createInputStream()
    if (afd.parcelFileDescriptor.statSize < 0) {
        return readIntoMemory(stream)
    }
    val size = afd.declaredLength.takeIf { it != AssetFileDescriptor.UNKNOWN_LENGTH } ?: -1L
    return open(stream, offset = afd.startOffset, size = size)
}

private fun open(stream: FileInputStream, offset: Long, size: Long): RandomAccessData = try {
    RadFileChannelAccessor(stream, offset = offset, size = size)
} catch (e: Throwable) {
    stream.close()
    throw e
}

private fun readIntoMemory(stream: InputStream): RandomAccessData =
    RadByteArrayAccessor(stream.use { it.readBytes() })
