// The read functions mirror Node's fs.read(fd, buffer, offset, length, position, cb).
@file:Suppress("LongParameterList")

package fluxo.io.rad

import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

/** A Kotlin/JS `ByteArray` is an `Int8Array`: Node reads into a `Uint8Array` view of it. */
internal actual fun readSync(
    fs: NodeFs,
    fd: Int,
    bytes: ByteArray,
    offset: Int,
    length: Int,
    position: Double,
): Int {
    val array = bytes.unsafeCast<Int8Array>()
    val view = Uint8Array(array.buffer, array.byteOffset + offset, length)
    return fs.asDynamic().readSync(fd, view, 0, length, position).unsafeCast<Int>()
}

/** Node writes into a `Uint8Array` view of the ByteArray itself, so no copy is needed. */
internal actual fun readAsync(
    fs: NodeFs,
    fd: Int,
    bytes: ByteArray,
    offset: Int,
    length: Int,
    position: Double,
    callback: ReadCallback,
) {
    val array = bytes.unsafeCast<Int8Array>()
    val view = Uint8Array(array.buffer, array.byteOffset + offset, length)
    val onRead = { error: dynamic, n: Int ->
        callback.done(if (error == null) null else error.message.unsafeCast<String>(), n)
    }
    fs.asDynamic().read(fd, view, 0, length, position, onRead)
}

/** webMain does not see the nonJvmMain expect, so each web target forwards to it. */
internal actual fun openPlatformFile(path: String): RandomAccessData = openNodeRad(path)
