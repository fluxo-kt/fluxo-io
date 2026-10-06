@file:OptIn(InternalFluxoIoApi::class)

package fluxo.io.rad.okio

import fluxo.io.internal.InternalFluxoIoApi
import fluxo.io.internal.radOf
import fluxo.io.rad.RadContract
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import okio.buffer

internal class RadFileHandleTest {

    private val bytes = RadContract.bytes()
    private var closes = 0
    private val rad = radOf(bytes.size.toLong(), { closes++ }) { buf, position, offset, length ->
        val n = minOf(length.toLong(), bytes.size - position).toInt()
        if (n <= 0) return@radOf -1
        bytes.copyInto(buf, offset, position.toInt(), position.toInt() + n)
        n
    }

    @Test
    fun readsAtFileOffsetIntoArrayOffset() {
        val handle = rad.asFileHandle()
        assertEquals(bytes.size.toLong(), handle.size())
        val array = ByteArray(16)
        assertEquals(8, handle.read(fileOffset = 100, array, arrayOffset = 5, byteCount = 8))
        assertContentEquals(bytes.copyOfRange(100, 108), array.copyOfRange(5, 13))
        assertEquals(-1, handle.read(fileOffset = bytes.size.toLong(), array, 0, 1))
        val tail = handle.source(fileOffset = 200).buffer()
        assertContentEquals(bytes.copyOfRange(200, bytes.size), tail.readByteArray())
        tail.close()
        handle.close()
    }

    /** Okio releases the handle only after its last source closes; only then is the data closed. */
    @Test
    fun closesTheDataOnceTheHandleAndItsSourcesAreClosed() {
        val handle = rad.asFileHandle()
        val source = handle.source()
        handle.close()
        assertEquals(0, closes, "a source is still open")
        source.close()
        assertEquals(1, closes)
    }
}
