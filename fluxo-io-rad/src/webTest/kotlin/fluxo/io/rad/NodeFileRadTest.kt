package fluxo.io.rad

import fluxo.io.IOException
import kotlin.js.JsAny
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The Node `fs` file implementation (JS and Wasm-JS) over real temp files. */
internal class NodeFileRadTest {

    @Test
    fun contract() {
        val paths = ArrayList<String>()
        try {
            RadContract.verify { bytes -> RandomAccessData.open(tempFile(bytes).also(paths::add)) }
        } finally {
            paths.forEach(::deleteFile)
        }
    }

    /** Wasm-JS caps one read below this size: [RandomAccessData.readFully] must loop the chunks. */
    @Test
    fun readsAcrossTheReadChunkIntoABufferOffset() {
        val data = ByteArray(200_000) { (it * 31 + it / 251).toByte() }
        val path = tempFile(data)
        try {
            RandomAccessData.open(path).use { rad ->
                val buf = ByteArray(150_000)
                assertEquals(buf.size - 7, rad.readFully(buf, position = 30_000, offset = 7))
                val expected = data.copyOfRange(30_000, 30_000 + buf.size - 7)
                assertContentEquals(expected, buf.copyOfRange(7, buf.size))
                assertContentEquals(data, rad.readAllBytes())
            }
        } finally {
            deleteFile(path)
        }
    }

    @Test
    fun missingFileAndDirectoryFailAtOpen() {
        assertFailsWith<IOException> { RandomAccessData.open(tmpDir() + "/missing-" + Random.nextLong()) }
        assertFailsWith<IOException> { RandomAccessData.open(tmpDir()) }
    }

    private fun tempFile(bytes: ByteArray): String {
        val path = tmpDir() + "/fluxo-rad-" + Random.nextLong().toULong() + ".bin"
        val data = newUint8Array(bytes.size)
        for (i in bytes.indices) setByte(data, i, bytes[i].toInt())
        writeFile(path, data)
        return path
    }
}

internal fun tmpDir(): String = js("process.getBuiltinModule('node:os').tmpdir()")

internal fun newUint8Array(length: Int): JsAny = js("new Uint8Array(length)")

internal fun setByte(array: JsAny, i: Int, value: Int): Unit = js("array[i] = value")

internal fun writeFile(path: String, data: JsAny): Unit =
    js("process.getBuiltinModule('node:fs').writeFileSync(path, data)")

internal fun deleteFile(path: String): Unit = js("process.getBuiltinModule('node:fs').rmSync(path, { force: true })")
