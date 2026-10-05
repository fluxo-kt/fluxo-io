package fluxo.io.rad.okio

import fluxo.io.rad.RadByteArrayAccessor
import fluxo.io.rad.RadContract.BYTES
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import okio.Buffer
import okio.buffer

internal class RadSourceTest {

    @Test
    fun readsEveryRangeToItsEnd() {
        val rad = RadByteArrayAccessor(BYTES)
        assertContentEquals(BYTES, rad.source().buffer().readByteArray())
        assertContentEquals(BYTES.copyOfRange(100, 256), rad.source(100).buffer().readByteArray())
        assertContentEquals(BYTES.copyOfRange(10, 30), rad.slice(10, 20).source().buffer().readByteArray())
        assertEquals(0, rad.source(256).buffer().readByteArray().size)
        assertEquals(0, rad.slice(5, 0).source().buffer().readByteArray().size)
    }

    @Test
    fun honoursByteCountAndKeepsTheSinkIntact() {
        val sink = Buffer().writeUtf8("head")
        val source = RadByteArrayAccessor(BYTES).source(1)
        assertEquals(3L, source.read(sink, 3))
        assertEquals(0L, source.read(sink, 0))
        assertEquals("head", sink.readUtf8(4))
        assertContentEquals(BYTES.copyOfRange(1, 4), sink.readByteArray())
        val exhausted = RadByteArrayAccessor(BYTES).source(256)
        assertEquals(-1L, exhausted.read(sink, 8))
        assertEquals(0L, sink.size, "a failed or empty read must not leave a partial segment")
    }
}
