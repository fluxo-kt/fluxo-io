package fluxo.io.rad

import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array

/** A Kotlin/JS `ByteArray` is an `Int8Array`: Node reads straight into a `Uint8Array` view of it. */
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
