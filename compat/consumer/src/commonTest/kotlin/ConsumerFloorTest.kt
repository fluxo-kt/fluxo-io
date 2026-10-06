import fluxo.io.rad.RadByteArrayAccessor
import kotlin.test.Test
import kotlin.test.assertContentEquals

class ConsumerFloorTest {
    // Calls into the library's common API, so each consumer target's published artifact is
    // resolved and linked by the floor compiler, and run wherever the host can run it.
    @Test
    fun readsThroughThePublishedArtifact() {
        val data = ByteArray(64) { ((it * 31 + 7) and 0xFF).toByte() }
        val rad = RadByteArrayAccessor(data)
        val out = ByteArray(8)
        rad.readFully(out, position = 10)
        assertContentEquals(data.copyOfRange(10, 18), out)
    }
}
