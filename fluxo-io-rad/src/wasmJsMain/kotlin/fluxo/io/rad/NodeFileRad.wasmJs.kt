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
