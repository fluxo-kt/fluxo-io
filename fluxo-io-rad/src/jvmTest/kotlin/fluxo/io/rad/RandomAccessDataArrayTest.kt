package fluxo.io.rad

import fluxo.io.util.EMPTY_BYTE_ARRAY
import kotlin.test.Test

/**
 * Tests for [RadByteArrayAccessor].
 */
internal class RandomAccessDataArrayTest : AbstractRandomAccessDataTest(
    { RadByteArrayAccessor(BYTES) }
) {
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
