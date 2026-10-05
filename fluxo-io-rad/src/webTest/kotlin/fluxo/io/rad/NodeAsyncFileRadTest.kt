package fluxo.io.rad

import fluxo.io.IOException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest

/**
 * The async Node `fs` source (JS and Wasm-JS) over a real temp file. Lifetime and bounds are
 * [fluxo.io.internal.AsyncAccessorRad]'s, tested in common code; here only the raw read and
 * a close while Node still holds a read are proven.
 */
internal class NodeAsyncFileRadTest {

    private val bytes = RadContract.BYTES

    @Test
    fun positionalReadsAndSlices() = runTest {
        withTempFile { path ->
            val rad = AsyncRandomAccessData.open(path)
            assertEquals(bytes.size.toLong(), rad.size)
            val buf = ByteArray(16)
            assertEquals(16, rad.readFully(buf, position = 100))
            assertContentEquals(bytes.copyOfRange(100, 116), buf)
            val slice = rad.slice(200, 56)
            assertEquals(8, slice.readFully(buf, position = 48, maxLength = 8))
            assertContentEquals(bytes.copyOfRange(248, 256), buf.copyOfRange(0, 8))
            assertEquals(-1, slice.read(buf, position = 56))
            rad.close()
            assertFailsWith<IOException> { rad.read(buf) }
        }
    }

    @Test
    fun closeWhileNodeHoldsAReadKeepsTheFdUntilItEnds() = runTest {
        withTempFile { path ->
            val rad = AsyncRandomAccessData.open(path)
            val buf = ByteArray(32)
            // UNDISPATCHED runs the read up to its suspension inside fs.read: it is in flight.
            // Whether libuv's pread runs before or after close is up to its thread pool, so a
            // missing lease reddens this only sometimes; the deferral itself is proven
            // deterministically in AsyncAccessorRadTest.
            val pending = async(start = CoroutineStart.UNDISPATCHED) { rad.readFully(buf, 10) }
            rad.close()
            assertEquals(32, pending.await(), "the read in flight completes on an open fd")
            assertContentEquals(bytes.copyOfRange(10, 42), buf)
        }
    }

    @Test
    fun missingFileFailsAtOpen() {
        assertFailsWith<IOException> { AsyncRandomAccessData.open(tmpDir() + "/missing-" + Random.nextLong()) }
    }

    private inline fun withTempFile(block: (String) -> Unit) {
        val path = tmpDir() + "/fluxo-arad-" + Random.nextLong().toULong() + ".bin"
        val data = newUint8Array(bytes.size)
        for (i in bytes.indices) setByte(data, i, bytes[i].toInt())
        writeFile(path, data)
        try {
            block(path)
        } finally {
            deleteFile(path)
        }
    }
}
