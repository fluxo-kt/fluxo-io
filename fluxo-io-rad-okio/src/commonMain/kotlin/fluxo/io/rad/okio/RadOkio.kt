@file:OptIn(InternalFluxoIoApi::class)

package fluxo.io.rad.okio

import fluxo.io.internal.InternalFluxoIoApi
import fluxo.io.internal.radOf
import fluxo.io.rad.RandomAccessData
import kotlin.math.min
import okio.Buffer
import okio.FileHandle
import okio.IOException
import okio.Source
import okio.Timeout

/**
 * Random access to an Okio [FileHandle] (any Okio `FileSystem`: the system one, a zip file
 * system, a fake one in tests) with the core's rules: thread-safe positional reads, slices,
 * shared handles. Takes ownership: [handle] is closed once the last handle closes and the last
 * read has returned. The size is read once, here.
 *
 * @throws IOException if the size cannot be read
 */
public fun RandomAccessData.Companion.open(handle: FileHandle): RandomAccessData =
    radOf(handle.size(), handle::close) { bytes, position, offset, length ->
        handle.read(position, bytes, offset, length)
    }

/**
 * A [Source] reading this data from [position] to its end. The source does not own the data:
 * closing it leaves this data open (close or slice the data yourself). Bytes go straight into
 * the sink's segments, with no intermediate array.
 */
public fun RandomAccessData.source(position: Long = 0L): Source = RadSource(this, position)

private class RadSource(private val rad: RandomAccessData, private var position: Long) : Source {

    override fun read(sink: Buffer, byteCount: Long): Long {
        require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
        if (byteCount == 0L) {
            return 0L
        }
        // The same write-into-segment pattern Okio's own FileHandle.source uses.
        val cursor = sink.readAndWriteUnsafe()
        try {
            val oldSize = sink.size
            cursor.expandBuffer(1)
            val length = min(byteCount, (cursor.end - cursor.start).toLong()).toInt()
            val n = rad.read(cursor.data!!, position, cursor.start, length)
            cursor.resizeBuffer(oldSize + n.coerceAtLeast(0))
            if (n < 0) {
                return -1L
            }
            position += n
            return n.toLong()
        } finally {
            cursor.close()
        }
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() = Unit
}
