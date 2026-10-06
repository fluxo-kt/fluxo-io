package fluxo.io.rad.kotlinx.io

import fluxo.io.rad.RadByteArrayAccessor
import fluxo.io.rad.RadContract
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlinx.io.Buffer
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
}
