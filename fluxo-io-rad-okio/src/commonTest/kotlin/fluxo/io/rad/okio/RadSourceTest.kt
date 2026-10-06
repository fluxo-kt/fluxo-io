package fluxo.io.rad.okio

import fluxo.io.IOException
import fluxo.io.rad.RadByteArrayAccessor
import fluxo.io.rad.RadContract
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import okio.Buffer
import okio.buffer

private val BYTES = RadContract.bytes()

internal class RadSourceTest {

    @Test
    fun readsEveryRangeToItsEnd() {
        val rad = RadByteArrayAccessor(RadContract.bytes())
        assertContentEquals(BYTES, rad.source().buffer().readByteArray())
        assertContentEquals(BYTES.copyOfRange(100, 256), rad.source(100).buffer().readByteArray())
        assertContentEquals(BYTES.copyOfRange(10, 30), rad.slice(10, 20).source().buffer().readByteArray())
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
        assertFailsWith<IOException> { closed.source().read(sink, 8) }
        assertEquals("head", sink.readUtf8())
    }
}
