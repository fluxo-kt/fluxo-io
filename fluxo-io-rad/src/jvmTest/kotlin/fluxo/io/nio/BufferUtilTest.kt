package fluxo.io.nio

import java.io.IOException
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class BufferUtilTest {

    /** Every `read(ByteBuffer, position)` of a resource-backed impl caps its read this way. */
    @Test
    fun readAtMostRestoresTheCallersLimitWhenTheReadFails() {
        val buffer = ByteBuffer.allocate(16).apply { positionCompat(2) }
        assertFailsWith<IOException> {
            buffer.readAtMost(5) {
                assertEquals(7, it.limit())
                throw IOException("planted")
            }
        }
        assertEquals(16, buffer.limit())
        assertEquals(2, buffer.position())
    }
}
