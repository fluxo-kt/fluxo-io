@file:JvmName("RadKotlinxIo")
@file:OptIn(UnsafeIoApi::class)

package fluxo.io.rad.kotlinx.io

import fluxo.io.rad.RandomAccessData
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import kotlinx.io.UnsafeIoApi
import kotlinx.io.unsafe.UnsafeBufferOperations
import kotlin.jvm.JvmName
import kotlin.math.min

/**
 * A [RawSource] reading this data from [position] to its end. The source does not own the data:
 * closing it leaves this data open (close or slice the data yourself). Bytes go straight into
 * the sink's segments, with no intermediate array.
 *
 * kotlinx-io has no random-access file API, so there is no reverse adapter.
 */
public fun RandomAccessData.asRawSource(position: Long = 0L): RawSource =
    RadRawSource(this, position)

private class RadRawSource(
    private val rad: RandomAccessData,
    private var position: Long,
) : RawSource {

    override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
        require(byteCount >= 0L) { "byteCount < 0: $byteCount" }
        if (byteCount == 0L) {
            return 0L
        }
        var n = 0
        UnsafeBufferOperations.writeToTail(sink, 1) { bytes, start, end ->
            n = rad.read(bytes, position, start, min(byteCount, (end - start).toLong()).toInt())
            n.coerceAtLeast(0)
        }
        if (n < 0) {
            return -1L
        }
        position += n
        return n.toLong()
    }

    override fun close() = Unit
}
