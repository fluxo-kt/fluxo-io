import fluxo.io.rad.RadByteArrayAccessor
import kotlin.test.Test
import kotlin.test.assertContentEquals

class ConsumerFloorTest {
    // Calls into the library's common API on every target, so each target's published artifact
    // is resolved, linked and executed by the floor compiler.
    @Test
    fun readsThroughThePublishedArtifact() {
        val data = ByteArray(64) { ((it * 31 + 7) and 0xFF).toByte() }
        val rad = RadByteArrayAccessor(data)
        val out = ByteArray(8)
        rad.readFully(out, position = 10)
        assertContentEquals(data.copyOfRange(10, 18), out)
    }
}
