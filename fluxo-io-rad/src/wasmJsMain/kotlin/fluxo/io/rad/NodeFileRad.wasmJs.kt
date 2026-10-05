// The js() helpers below use their parameters inside the JS string, where detekt cannot see
// them; the read functions mirror Node's fs.read(fd, buffer, offset, length, position, cb).
@file:Suppress("UnusedParameter", "LongParameterList")

package fluxo.io.rad

import kotlin.js.JsAny

/**
 * A Wasm `ByteArray` lives in Wasm GC memory that JS cannot see, so Node reads into a JS
 * `Uint8Array` that is then copied byte by byte (as kotlinx-browser and Ktor do).
 */
internal actual fun readSync(
    fs: NodeFs,
    fd: Int,
    bytes: ByteArray,
    offset: Int,
    length: Int,
    position: Double,
): Int {
    val buffer = newUint8Array(length)
    val n = nodeReadSync(fs, fd, buffer, length, position)
    for (i in 0 until n) {
        bytes[offset + i] = byteAt(buffer, i)
    }
    return n
}

private fun newUint8Array(length: Int): JsAny = js("new Uint8Array(length)")

private fun nodeReadSync(fs: NodeFs, fd: Int, buffer: JsAny, length: Int, position: Double): Int =
    js("fs.readSync(fd, buffer, 0, length, position)")

private fun byteAt(buffer: JsAny, i: Int): Byte = js("buffer[i]")

/**
 * Node fills a JS `Uint8Array`; the copy into the ByteArray happens in the callback, after the
 * read finished, so the ByteArray is never touched while the read is pending.
 */
internal actual fun readAsync(
    fs: NodeFs,
    fd: Int,
    bytes: ByteArray,
    offset: Int,
    length: Int,
    position: Double,
    callback: ReadCallback,
) {
    val buffer = newUint8Array(length)
    nodeRead(fs, fd, buffer, length, position) { error, n ->
        if (error == null) {
            for (i in 0 until n) {
                bytes[offset + i] = byteAt(buffer, i)
            }
        }
        callback.done(error, n)
    }
}

private fun nodeRead(
    fs: NodeFs,
    fd: Int,
    buffer: JsAny,
    length: Int,
    position: Double,
    done: (String?, Int) -> Unit,
): Unit = js(
    "fs.read(fd, buffer, 0, length, position, " +
        "function (e, n) { done(e ? String(e.message) : null, e ? 0 : n); })",
)

/** webMain does not see the nonJvmMain expect, so each web target forwards to it. */
internal actual fun openPlatformFile(path: String): RandomAccessData = openNodeRad(path)
