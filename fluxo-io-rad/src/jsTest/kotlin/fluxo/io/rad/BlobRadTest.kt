package fluxo.io.rad

import fluxo.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.khronos.webgl.Int8Array
import org.w3c.files.Blob

/** Range reads from a real `Blob` (Node has the same Blob as browsers). */
internal class BlobRadTest {

    @Test
    fun rangeReads() = runTest {
        val bytes = RadContract.bytes()
        val rad = AsyncRandomAccessData.open(Blob(arrayOf(bytes.unsafeCast<Int8Array>())))
        assertEquals(bytes.size.toLong(), rad.size)
        val buf = ByteArray(20)
        assertEquals(20, rad.readFully(buf, position = 30))
        assertContentEquals(bytes.copyOfRange(30, 50), buf)
        assertEquals(6, rad.slice(250, 6).readFully(buf, offset = 4))
        assertContentEquals(bytes.copyOfRange(250, 256), buf.copyOfRange(4, 10))
        assertEquals(-1, rad.read(buf, position = 256))
        rad.close()
        assertFailsWith<IOException> { rad.read(buf) }
    }
}
