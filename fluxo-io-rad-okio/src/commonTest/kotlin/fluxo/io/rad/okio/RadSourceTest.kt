@file:OptIn(InternalFluxoIoApi::class)

package fluxo.io.rad.okio

import fluxo.io.internal.InternalFluxoIoApi
import fluxo.io.internal.radOf
import fluxo.io.rad.RadByteArrayAccessor
import fluxo.io.rad.RadContract
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import okio.Buffer
import okio.IOException
import okio.buffer

private val BYTES = RadContract.bytes()

internal class RadSourceTest {

    @Test
    fun readsEveryRangeToItsEnd() {
        val rad = RadByteArrayAccessor(RadContract.bytes())
        assertContentEquals(BYTES, rad.source().buffer().readByteArray())
        assertContentEquals(BYTES.copyOfRange(100, 256), rad.source(100).buffer().readByteArray())
        val slice = rad.slice(10, 20)
        assertContentEquals(BYTES.copyOfRange(10, 30), slice.source().buffer().readByteArray())
        assertEquals(0, rad.source(256).buffer().readByteArray().size)
        assertEquals(0, rad.slice(5, 0).source().buffer().readByteArray().size)
    }

    @Test
    fun honoursByteCountAndKeepsTheSinkIntact() {
        val sink = Buffer().writeUtf8("head")
        val source = RadByteArrayAccessor(RadContract.bytes()).source(1)
        assertEquals(3L, source.read(sink, 3))
        assertEquals(0L, source.read(sink, 0))
        assertEquals("head", sink.readUtf8(4))
        assertContentEquals(BYTES.copyOfRange(1, 4), sink.readByteArray())
        val exhausted = RadByteArrayAccessor(RadContract.bytes()).source(256)
        assertEquals(-1L, exhausted.read(sink, 8))
        assertEquals(0L, sink.size, "an empty read must not leave a partial segment")
    }

    @Test
    fun failedReadLeavesTheSinkAsItWas() {
        val closed = RadByteArrayAccessor(RadContract.bytes()).also { it.close() }
        val sink = Buffer().writeUtf8("head")
        // okio.IOException: on non-JVM targets the core's IOException is a different class.
        assertFailsWith<IOException> { closed.source().read(sink, 8) }
        assertEquals("head", sink.readUtf8())
    }

    /** A partly filled tail segment leaves less room than byteCount: position advances by n. */
    @Test
    fun consecutiveReadsIntoAPartlyFilledSegmentStayContiguous() {
        val data = ByteArray(20_000) { (it * 31 + 7).toByte() }
        val sink = Buffer().writeByte(0)
        val source = RadByteArrayAccessor(data).source()
        val n1 = source.read(sink, 8192)
        val n2 = source.read(sink, 8192)
        assertTrue(n1 < 8192, "the first read must be capped by the segment")
        assertContentEquals(byteArrayOf(0) + data.copyOf((n1 + n2).toInt()), sink.readByteArray())
    }

    @Test
    fun closedSourceRefusesToRead() {
        val source = RadByteArrayAccessor(RadContract.bytes()).source()
        source.close()
        assertFailsWith<IllegalStateException> { source.read(Buffer(), 1) }
    }

    @Test
    fun zeroByteReadFailsInsteadOfLettingCallersSpin() {
        // The guard turns a regression (callers looping on 0) into a failure instead of a hang.
        var calls = 0
        val stuck = radOf(8, {}) { _, _, _, _ ->
            if (++calls > 1_000) throw AssertionError("caller spins on zero-byte reads")
            0
        }
        assertFailsWith<IOException> { stuck.source().buffer().readByteArray() }
    }
}
