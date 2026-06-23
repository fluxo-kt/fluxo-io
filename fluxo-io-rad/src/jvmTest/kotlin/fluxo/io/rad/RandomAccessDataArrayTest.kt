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
        // Assert at non-zero indices: BYTES[i]=i.toByte(), so a regression that
        // cleared/zeroed the backing on close would still pass `==0` assertions by
        // accident. Non-zero indices + content checks distinguish a real read from a
        // zeroed read.
        assertEquals(5, rad.readByteAt(5))
        assertEquals(byteArrayOf(3, 4, 5), rad.readFrom(3, 3))
        val buf = ByteBuffer.allocate(1)
        assertEquals(1, rad.read(buf, 7))
        assertEquals(7, buf.array()[0].toInt())
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
