package fluxo.io.rad

import fluxo.io.IOException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

/**
 * The async Node `fs` source (JS and Wasm-JS) over a real temp file. Lifetime and bounds are
 * [fluxo.io.internal.AsyncAccessorRad]'s and the lease is SharedAsyncDataAccessor's, both
 * tested deterministically in common code; here only the raw read is proven.
 */
internal class NodeAsyncFileRadTest {

    private val bytes = RadContract.bytes()

    @Test
    fun positionalReadsAndSlices() = runTest {
        withTempFile { path ->
            val rad = AsyncRandomAccessData.open(path)
            assertEquals(bytes.size.toLong(), rad.size)
            val buf = ByteArray(16)
            assertEquals(16, rad.readFully(buf, position = 100))
            assertContentEquals(bytes.copyOfRange(100, 116), buf)
            // Into the middle of the array: the JS and Wasm-JS byte copies honour the offset.
            assertEquals(8, rad.readFully(buf, position = 20, offset = 5, maxLength = 8))
            assertContentEquals(bytes.copyOfRange(20, 28), buf.copyOfRange(5, 13))
            assertContentEquals(bytes.copyOfRange(100, 105), buf.copyOfRange(0, 5))
            val slice = rad.slice(200, 56)
            assertEquals(8, slice.readFully(buf, position = 48, maxLength = 8))
            assertContentEquals(bytes.copyOfRange(248, 256), buf.copyOfRange(0, 8))
            assertEquals(-1, slice.read(buf, position = 56))
            rad.close()
            assertFailsWith<IOException> { rad.read(buf) }
        }
    }

    /** Wasm-JS caps one read below this size: readFully must loop the chunks. */
    @Test
    fun readsAcrossTheReadChunkIntoABufferOffset() = runTest {
        val data = ByteArray(200_000) { (it * 31 + it / 251).toByte() }
        withTempFile(data) { path ->
            val rad = AsyncRandomAccessData.open(path)
            val buf = ByteArray(150_000)
            assertEquals(buf.size - 7, rad.readFully(buf, position = 30_000, offset = 7))
            val expected = data.copyOfRange(30_000, 30_000 + buf.size - 7)
            assertContentEquals(expected, buf.copyOfRange(7, buf.size))
            rad.close()
        }
    }

    @Test
    fun missingFileFailsAtOpen() {
        assertFailsWith<IOException> { AsyncRandomAccessData.open(tmpDir() + "/missing-" + Random.nextLong()) }
    }

    private inline fun withTempFile(bytes: ByteArray = this.bytes, block: (String) -> Unit) {
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
