package fluxo.io.rad

import fluxo.io.IOException
import fluxo.io.internal.AccessorRad
import fluxo.io.internal.SharedDataAccessor
import fluxo.io.util.EMPTY_AUTO_CLOSEABLE_ARRAY
import kotlin.js.JsAny

/**
 * Opens [path] with Node's synchronous `fs` API, which Node, Bun and Deno all provide through
 * `process.getBuiltinModule("node:fs")` (no `require`, so it works from ES modules too).
 * `readSync` with a position is a positional read (`pread`), one call per read.
 * A runtime without it (a browser) has no file system: files there come as `Blob`s.
 */
internal fun openPlatformFile(path: String): RandomAccessData {
    val fs = nodeFs()
        ?: throw IOException("No file system in this JS runtime; use AsyncRandomAccessData.open(blob)")
    val fd = try {
        fs.openSync(path, "r")
    } catch (e: Throwable) {
        throw IOException("Cannot open $path: ${e.message}")
    }
    return try {
        val stats = fs.fstatSync(fd)
        if (stats.isDirectory()) {
            throw IOException("Cannot open $path: is a directory")
        }
        AccessorRad(NodeFdAccess(fs, fd, path, stats.size.toLong()))
    } catch (e: Throwable) {
        fs.closeSync(fd)
        throw e
    }
}

/** The fd is used only inside a lease: a closed fd number is reused by later opens. */
private class NodeFdAccess(
    private val fs: NodeFs,
    private val fd: Int,
    private val path: String,
    override val size: Long,
) : SharedDataAccessor(EMPTY_AUTO_CLOSEABLE_ARRAY) {

    override fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int =
        withLease {
            val n = try {
                readSync(fs, fd, bytes, offset, length, position.toDouble())
            } catch (e: Throwable) {
                throw IOException("Cannot read $path at $position: ${e.message}")
            }
            // 0 inside `size` means the file shrank after open: report end of data.
            if (n > 0) n else -1
        }

    override fun releaseApi() = fs.closeSync(fd)
}

/**
 * Node's `fs`, typed so only a `Uint8Array` can be passed as a buffer: Deno's FileHandle
 * ignores an `Int8Array` (what a Kotlin/JS `ByteArray` is), so the platform `readSync` below
 * hands Node a `Uint8Array`. Positions are `Number`s (exact up to 2^53).
 */
internal external interface NodeFs : JsAny {
    fun openSync(path: String, flags: String): Int
    fun fstatSync(fd: Int): NodeStats
    fun closeSync(fd: Int)
}

internal external interface NodeStats : JsAny {
    val size: Double
    fun isDirectory(): Boolean
}

/** `fs.readSync(fd, uint8array, 0, length, position)` into [bytes] at [offset]. */
internal expect fun readSync(
    fs: NodeFs,
    fd: Int,
    bytes: ByteArray,
    offset: Int,
    length: Int,
    position: Double,
): Int

// Kotlin's js() parser predates `?.` and `??`.
private fun nodeFs(): NodeFs? = js(
    "(typeof process !== 'undefined' && process.getBuiltinModule) ? process.getBuiltinModule('node:fs') : null",
)
