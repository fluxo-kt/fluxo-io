package fluxo.io.rad

import fluxo.io.IOException
import fluxo.io.internal.AsyncAccessorRad
import fluxo.io.internal.Blocking
import fluxo.io.internal.SharedAsyncDataAccessor
import fluxo.io.util.EMPTY_AUTO_CLOSEABLE_ARRAY
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Opens [path] for reads that never block the JS event loop: each read is Node's callback
 * `fs.read` with a position, run on libuv's thread pool (Node, Bun and Deno). Returns a handle:
 * close it once when finished; a close during a pending read waits for that read to end.
 *
 * Opening is a quick synchronous `openSync` + `fstatSync`, so the factory needs no `suspend`,
 * and the descriptor can be closed synchronously when the last handle closes.
 *
 * @throws IOException if there is no Node-compatible file system (in a browser, read a `Blob`)
 *  or the file cannot be opened
 */
@Blocking
@Throws(IOException::class)
public fun AsyncRandomAccessData.Companion.open(path: String): AsyncRandomAccessData =
    openNodeFile(path) { fs, fd, size -> AsyncAccessorRad(NodeAsyncFdAccess(fs, fd, path, size)) }

private class NodeAsyncFdAccess(
    private val fs: NodeFs,
    private val fd: Int,
    private val path: String,
    override val size: Long,
) : SharedAsyncDataAccessor(EMPTY_AUTO_CLOSEABLE_ARRAY) {

    override suspend fun readLeased(
        bytes: ByteArray, position: Long, offset: Int, length: Int,
    ): Int {
        val n = suspendCoroutine { cont ->
            val callback = ReadCallback(cont, path, position)
            readAsync(fs, fd, bytes, offset, length, position.toDouble(), callback)
        }
        // 0 inside `size` means the file shrank after open: report end of data.
        return if (n > 0) n else -1
    }

    override fun releaseApi() = fs.closeSync(fd)
}

/** Resumes the reader once Node calls back; `error` is the message of a failed read. */
internal class ReadCallback(
    private val cont: Continuation<Int>,
    private val path: String,
    private val position: Long,
) {
    fun done(error: String?, bytesRead: Int) {
        if (error == null) {
            cont.resume(bytesRead)
        } else {
            cont.resumeWithException(IOException("Cannot read $path at $position: $error"))
        }
    }
}

/** `fs.read(fd, uint8array, 0, length, position, callback)` into [bytes] at [offset]. */
@Suppress("LongParameterList") // mirrors Node's fs.read arguments
internal expect fun readAsync(
    fs: NodeFs,
    fd: Int,
    bytes: ByteArray,
    offset: Int,
    length: Int,
    position: Double,
    callback: ReadCallback,
)
