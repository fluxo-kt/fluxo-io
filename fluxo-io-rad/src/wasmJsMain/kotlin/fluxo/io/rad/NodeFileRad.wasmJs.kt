// The js() helpers below use their parameters inside the JS string, where detekt cannot see
// them; the read functions mirror Node's fs.read(fd, buffer, offset, length, position, cb).
@file:Suppress("UnusedParameter", "LongParameterList")
@file:OptIn(UnsafeWasmMemoryApi::class)

package fluxo.io.rad

import kotlin.js.JsAny
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

/**
 * A Wasm `ByteArray` lives in Wasm GC memory that JS cannot see, so Node reads into Wasm linear
 * memory, copied into the ByteArray inside Wasm (no host call per byte). Linear memory never
 * shrinks once grown, so one read is capped (a short read is legal) instead of growing it.
 */
internal actual fun readSync(
    fs: NodeFs,
    fd: Int,
    bytes: ByteArray,
    offset: Int,
    length: Int,
    position: Double,
): Int = withScopedMemoryAllocator { alloc ->
    val chunk = minOf(length, MAX_READ_CHUNK)
    val buf = alloc.allocate(chunk)
    val n = nodeReadSync(fs, fd, buf.address.toInt(), chunk, position)
    copyToArray(buf, bytes, offset, n)
    n
}

private fun nodeReadSync(fs: NodeFs, fd: Int, address: Int, length: Int, position: Double): Int =
    js(
        "fs.readSync(fd, new Uint8Array(wasmExports.memory.buffer, address, length), " +
            "0, length, position)",
    )

private fun copyToArray(buf: Pointer, bytes: ByteArray, offset: Int, n: Int) {
    for (i in 0 until n) {
        bytes[offset + i] = (buf + i).loadByte()
    }
}

/**
 * Node fills a JS `Uint8Array`, not linear memory: a scoped allocation cannot outlive the pending
 * read. The callback, after the read finished, copies it through linear memory into the
 * ByteArray, so the ByteArray is never touched while the read is pending.
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
    val chunk = minOf(length, MAX_READ_CHUNK)
    val buffer = newUint8Array(chunk)
    nodeRead(fs, fd, buffer, chunk, position) { error, n ->
        if (error == null && n > 0) {
            withScopedMemoryAllocator { alloc ->
                val buf = alloc.allocate(n)
                copyToMemory(buffer, buf.address.toInt(), n)
                copyToArray(buf, bytes, offset, n)
            }
        }
        callback.done(error, n)
    }
}

private fun newUint8Array(length: Int): JsAny = js("new Uint8Array(length)")

private fun copyToMemory(buffer: JsAny, address: Int, n: Int): Unit =
    js("new Uint8Array(wasmExports.memory.buffer, address, n).set(buffer.subarray(0, n))")

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

private const val MAX_READ_CHUNK = 64 * 1024
