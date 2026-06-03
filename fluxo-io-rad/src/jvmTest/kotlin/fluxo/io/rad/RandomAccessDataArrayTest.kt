package fluxo.io.rad

import fluxo.io.util.EMPTY_BYTE_ARRAY
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [RadByteArrayAccessor].
 */
internal class RandomAccessDataArrayTest : AbstractRandomAccessDataTest(
    { RadByteArrayAccessor(BYTES) }
) {
    /**
     * A [ByteArray] holds no releasable resource, so [ByteArrayRad.close] is a no-op and reads
     * stay valid afterwards — there is no freed state to make a use-after-close unsafe, and
     * guarding the fastest impl's hot path would buy no safety. The reject-after-close contract
     * applies only to resource-backed impls.
     */
    @Test
    override fun readingClosedHolderThrowsNotCrashes() {
        inputStream.close()
        rad.close()
        val sink = Channels.newChannel(ByteArrayOutputStream())
        assertEquals(0, rad.readByteAt(0))
        assertEquals(byteArrayOf(0), rad.readFrom(0, 1))
        assertEquals(1, rad.read(ByteBuffer.allocate(1), 0))
        assertEquals(BYTES.size.toLong(), rad.transferTo(sink))
    }

    @Test
    fun creationBoundaries() {
        assertEmptyRad(RadByteArrayAccessor(EMPTY_BYTE_ARRAY))
        assertEmptyRad(RadByteArrayAccessor(BYTES, 0, 0))
        assertEmptyRad(RadByteArrayAccessor(BYTES, BYTES.size))
        assertEmptyRad(RadByteArrayAccessor(BYTES, BYTES.size, 0))

        assertIOB { RadByteArrayAccessor(BYTES, -1, 0) }
        assertIOB { RadByteArrayAccessor(BYTES, -1, 1) }
        assertIOB { RadByteArrayAccessor(BYTES, 0, -1) }
        assertIOB { RadByteArrayAccessor(BYTES, 1, -1) }
        assertIOB { RadByteArrayAccessor(BYTES, BYTES.size + 1) }
        assertIOB { RadByteArrayAccessor(BYTES, BYTES.size, 1) }
        assertIOB { RadByteArrayAccessor(BYTES, BYTES.size + 1, 1) }
        assertIOB { RadByteArrayAccessor(BYTES, 0, BYTES.size + 1) }
    }
}
