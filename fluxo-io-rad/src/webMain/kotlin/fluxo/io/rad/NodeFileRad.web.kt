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
internal fun openNodeRad(path: String): RandomAccessData =
    openNodeFile(path) { fs, fd, size -> AccessorRad(NodeFdAccess(fs, fd, path, size)) }

/**
 * Opens [path] read-only and passes the descriptor and size to [wrap], which takes it over;
 * the descriptor is closed here only if anything fails first.
 */
// The common IOException takes no cause; the JS error's message is carried over instead.
@Suppress("SwallowedException")
internal inline fun <T> openNodeFile(path: String, wrap: (NodeFs, fd: Int, size: Long) -> T): T {
    val fs = requireNodeFs()
    val fd = try {
        fs.openSync(path, "r")
    } catch (e: Throwable) {
        throw IOException("Cannot open $path: ${e.message}")
    }
    return try {
        wrap(fs, fd, fileSize(fs.fstatSync(fd), path))
    } catch (e: Throwable) {
        runCatching { fs.closeSync(fd) }.exceptionOrNull()?.let(e::addSuppressed)
        throw e
    }
}

/** `openSync` succeeds on a directory; reading it would fail later with EISDIR. */
internal fun fileSize(stats: NodeStats, path: String): Long {
    if (stats.isDirectory()) {
        throw IOException("Cannot open $path: is a directory")
    }
    return stats.size.toLong()
}

/** The fd is used only inside a lease: a closed fd number is reused by later opens. */
private class NodeFdAccess(
    private val fs: NodeFs,
    private val fd: Int,
    private val path: String,
    override val size: Long,
) : SharedDataAccessor(EMPTY_AUTO_CLOSEABLE_ARRAY) {

    // The common IOException takes no cause; the JS error's message is carried over instead.
    @Suppress("SwallowedException")
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
 * Node's `fs`, without read methods on purpose: Deno ignores an `Int8Array` buffer (what a
 * Kotlin/JS `ByteArray` is), so every read goes through the platform `readSync`/`readAsync`,
 * which always pass a `Uint8Array`. Positions are `Number`s (exact up to 2^53).
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
@Suppress("LongParameterList") // mirrors Node's readSync arguments
internal expect fun readSync(
    fs: NodeFs,
    fd: Int,
    bytes: ByteArray,
    offset: Int,
    length: Int,
    position: Double,
): Int

internal fun requireNodeFs(): NodeFs = nodeFs() ?: throw IOException(
    if (hasProcess()) {
        // Node before 20.16 / 22.3 has a file system but no process.getBuiltinModule.
        "This JS runtime has no process.getBuiltinModule (Node 20.16+, 22.3+, Bun, Deno): " +
            "upgrade it to read files"
    } else {
        "No file system in this JS runtime; in a browser, read a Blob " +
            "(Kotlin/JS: AsyncRandomAccessData.open(blob))"
    },
)

private fun hasProcess(): Boolean = js("typeof process !== 'undefined'")

// Kotlin's js() parser predates `?.` and `??`.
private fun nodeFs(): NodeFs? = js(
    "(typeof process !== 'undefined' && process.getBuiltinModule) " +
        "? process.getBuiltinModule('node:fs') : null",
)
