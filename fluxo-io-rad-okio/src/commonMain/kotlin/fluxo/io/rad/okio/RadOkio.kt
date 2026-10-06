@file:JvmName("RadOkio")
@file:OptIn(InternalFluxoIoApi::class)

package fluxo.io.rad.okio

import fluxo.io.internal.InternalFluxoIoApi
import fluxo.io.internal.radOf
import fluxo.io.rad.RandomAccessData
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.math.min
import okio.Buffer
import okio.FileHandle
import okio.IOException
import okio.Source
import okio.Timeout

/**
 * Random access to an Okio [FileHandle] (from any Okio `FileSystem` that opens one: the system
 * one, a fake one in tests; Okio's zip file system does not) with the core's rules:
 * thread-safe positional reads, slices, shared handles. Takes ownership: [handle] is closed
 * once the last handle closes and the last read has returned. The size is read once, here.
 *
 * @throws IOException if the size cannot be read
 */
public fun RandomAccessData.Companion.open(handle: FileHandle): RandomAccessData {
    val size = try {
        handle.size()
    } catch (e: Throwable) {
        runCatching { handle.close() }.exceptionOrNull()?.let(e::addSuppressed)
        throw e
    }
    return radOf(size, handle::close) { bytes, position, offset, length ->
        try {
            handle.read(position, bytes, offset, length)
        } catch (e: IOException) {
            throw e.asCoreIOException()
        }
    }
}

/**
 * A [Source] reading this data from [position] to its end. The source does not own the data:
 * closing it leaves this data open (close or slice the data yourself). Bytes go straight into
 * the sink's segments, with no intermediate array.
 */
@JvmOverloads
public fun RandomAccessData.source(position: Long = 0L): Source = RadSource(this, position)

private class RadSource(private val rad: RandomAccessData, private var position: Long) : Source {

    private var closed = false

    override fun read(sink: Buffer, byteCount: Long): Long {
        require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
        check(!closed) { "closed" }
        if (byteCount == 0L) {
            return 0L
        }
        // expandBuffer grows sink.size up front, so the size is set back to what was really
        // read even when the read throws: otherwise the sink keeps a segment of stale bytes.
        val cursor = sink.readAndWriteUnsafe()
        val oldSize = sink.size
        var n = -1
        try {
            cursor.expandBuffer(1)
            val length = min(byteCount, (cursor.end - cursor.start).toLong()).toInt()
            n = try {
                rad.read(checkNotNull(cursor.data), position, cursor.start, length)
            } catch (e: fluxo.io.IOException) {
                throw e.asOkioIOException()
            }
        } finally {
            cursor.resizeBuffer(oldSize + n.coerceAtLeast(0))
            cursor.close()
        }
        if (n < 0) {
            return -1L
        }
        // Okio callers loop until -1 (Buffer.writeAll), so passing 0 on would spin them forever.
        if (n == 0) throw IOException("Read made no progress at $position")
        position += n
        return n.toLong()
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() {
        closed = true
    }
}

/**
 * This data as a read-only Okio [FileHandle], for APIs that take one (`handle.source(offset)`,
 * Okio file-system code). Takes ownership, the inverse of `RandomAccessData.open(handle)`:
 * once the handle and every source opened from it are closed, this data is closed; use
 * `share().asFileHandle()` to keep this one usable. Okio serialises the handle's reads.
 */
public fun RandomAccessData.asFileHandle(): FileHandle = RadFileHandle(this)

private class RadFileHandle(private val rad: RandomAccessData) : FileHandle(readWrite = false) {

    override fun protectedRead(
        fileOffset: Long,
        array: ByteArray,
        arrayOffset: Int,
        byteCount: Int,
    ): Int = try {
        rad.read(array, fileOffset, arrayOffset, byteCount)
    } catch (e: fluxo.io.IOException) {
        throw e.asOkioIOException()
    }

    override fun protectedSize(): Long = rad.size

    override fun protectedClose() = rad.close()

    // FileHandle checks readWrite before each of these, so a read-only handle never gets here.
    override fun protectedWrite(
        fileOffset: Long,
        array: ByteArray,
        arrayOffset: Int,
        byteCount: Int,
    ) = throw UnsupportedOperationException("read-only")

    override fun protectedFlush() = throw UnsupportedOperationException("read-only")

    override fun protectedResize(size: Long) = throw UnsupportedOperationException("read-only")
}

// One class on the JVM (java.io.IOException); distinct classes elsewhere, where a caller catching
// one would miss the other.
private fun fluxo.io.IOException.asOkioIOException(): IOException =
    this as? IOException ?: IOException(message, this)

private fun IOException.asCoreIOException(): fluxo.io.IOException =
    this as? fluxo.io.IOException
        ?: fluxo.io.IOException(message ?: "Okio read failed").also { it.addSuppressed(this) }
