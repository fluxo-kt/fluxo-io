package fluxo.io.rad

import fluxo.io.IOException
import kotlin.js.JsAny
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

/** The Node `fs` file implementation (JS and Wasm-JS) over real temp files. */
internal class NodeFileRadTest {

    @Test
    fun contract() {
        val paths = ArrayList<String>()
        try {
            RadContract.verify { bytes -> openPlatformFile(tempFile(bytes).also(paths::add)) }
        } finally {
            paths.forEach(::deleteFile)
        }
    }

    @Test
    fun missingFileAndDirectoryFailAtOpen() {
        assertFailsWith<IOException> { openPlatformFile(tmpDir() + "/missing-" + Random.nextLong()) }
        assertFailsWith<IOException> { openPlatformFile(tmpDir()) }
    }

    @Test
    fun readsAfterOtherHandleCloses() {
        val path = tempFile(RadContract.BYTES)
        try {
            val rad = openPlatformFile(path)
            val shared = rad.share()
            rad.close()
            assertContentEquals(RadContract.BYTES, shared.readAllBytes())
            shared.close()
            assertFailsWith<IOException> { shared.readAllBytes() }
        } finally {
            deleteFile(path)
        }
    }

    private fun tempFile(bytes: ByteArray): String {
        val path = tmpDir() + "/fluxo-rad-" + Random.nextLong().toULong() + ".bin"
        val data = newUint8Array(bytes.size)
        for (i in bytes.indices) setByte(data, i, bytes[i].toInt())
        writeFile(path, data)
        return path
    }
}

private fun tmpDir(): String = js("process.getBuiltinModule('node:os').tmpdir()")

private fun newUint8Array(length: Int): JsAny = js("new Uint8Array(length)")

private fun setByte(array: JsAny, i: Int, value: Int): Unit = js("array[i] = value")

private fun writeFile(path: String, data: JsAny): Unit =
    js("process.getBuiltinModule('node:fs').writeFileSync(path, data)")

private fun deleteFile(path: String): Unit = js("process.getBuiltinModule('node:fs').rmSync(path, { force: true })")
