@file:OptIn(InternalFluxoIoApi::class)

package fluxo.io.rad.kotlinx.io

import fluxo.io.internal.InternalFluxoIoApi
import fluxo.io.internal.radOf
import fluxo.io.rad.RadByteArrayAccessor
import fluxo.io.rad.RadContract
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.writeString
import kotlinx.io.readString

private val BYTES = RadContract.bytes()

internal class RadRawSourceTest {

    @Test
    fun readsEveryRangeToItsEnd() {
        val rad = RadByteArrayAccessor(RadContract.bytes())
        assertContentEquals(BYTES, rad.asRawSource().buffered().readByteArray())
        assertContentEquals(BYTES.copyOfRange(100, 256), rad.asRawSource(100).buffered().readByteArray())
        assertContentEquals(BYTES.copyOfRange(10, 30), rad.slice(10, 20).asRawSource().buffered().readByteArray())
        assertEquals(0, rad.asRawSource(256).buffered().readByteArray().size)
    }

    @Test
    fun honoursByteCountAndKeepsTheSinkIntact() {
        val sink = Buffer().apply { writeString("head") }
        val source = RadByteArrayAccessor(RadContract.bytes()).asRawSource(1)
        assertEquals(3L, source.readAtMostTo(sink, 3))
        assertEquals(0L, source.readAtMostTo(sink, 0))
        assertEquals("head", sink.readString(4))
        assertContentEquals(BYTES.copyOfRange(1, 4), sink.readByteArray())
        assertEquals(-1L, RadByteArrayAccessor(RadContract.bytes()).asRawSource(256).readAtMostTo(sink, 8))
        assertEquals(0L, sink.size)
    }

    @Test
    fun failedReadThrowsKotlinxIOExceptionAndLeavesTheSinkAsItWas() {
        val closed = RadByteArrayAccessor(RadContract.bytes()).also { it.close() }
        val sink = Buffer().apply { writeString("head") }
        // kotlinx.io.IOException: on non-JVM targets the core's IOException is a different class.
        assertFailsWith<IOException> { closed.asRawSource().readAtMostTo(sink, 8) }
        assertEquals("head", sink.readString())
    }

    /** A partly filled tail segment leaves less room than byteCount: position advances by n. */
    @Test
    fun consecutiveReadsIntoAPartlyFilledSegmentStayContiguous() {
        val data = ByteArray(20_000) { (it * 31 + 7).toByte() }
        val sink = Buffer().apply { writeByte(0) }
        val source = RadByteArrayAccessor(data).asRawSource()
        val n1 = source.readAtMostTo(sink, 8192)
        val n2 = source.readAtMostTo(sink, 8192)
        assertTrue(n1 < 8192, "the first read must be capped by the segment")
        assertContentEquals(byteArrayOf(0) + data.copyOf((n1 + n2).toInt()), sink.readByteArray())
    }

    @Test
    fun closedSourceRefusesToRead() {
        val source = RadByteArrayAccessor(RadContract.bytes()).asRawSource()
        source.close()
        assertFailsWith<IllegalStateException> { source.readAtMostTo(Buffer(), 1) }
    }

    @Test
    fun zeroByteReadFailsInsteadOfLettingCallersSpin() {
        // The guard turns a regression (callers looping on 0) into a failure instead of a hang.
        var calls = 0
        val stuck = radOf(8, {}) { _, _, _, _ ->
            if (++calls > 1_000) throw AssertionError("caller spins on zero-byte reads")
            0
        }
        assertFailsWith<IOException> { stuck.asRawSource().buffered().readByteArray() }
    }
}
