@file:OptIn(UnsafeWasmMemoryApi::class)

package fluxo.io.rad

import fluxo.io.IOException
import fluxo.io.internal.AccessorRad
import fluxo.io.internal.SharedDataAccessor
import fluxo.io.util.EMPTY_AUTO_CLOSEABLE_ARRAY
import kotlin.wasm.WasmImport
import kotlin.wasm.unsafe.MemoryAllocator
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

/**
 * Opens [path] through WASI preview1. A WASI module can only reach files under directories the
 * host preopened, and hosts (Node's `wasi` among them) accept only paths RELATIVE to such a
 * directory: an absolute path is resolved against the preopen it lies under, a relative path
 * against the first preopen (the convention kotlinx-io follows too). Reads are `fd_pread`,
 * one positional call per read.
 */
internal actual fun openPlatformFile(path: String): RandomAccessData {
    val (dirFd, relative) = resolvePreopen(path)
    val fd = withScopedMemoryAllocator { alloc ->
        val bytes = relative.encodeToByteArray()
        val pathPtr = alloc.write(bytes)
        val fdOut = alloc.allocate(4)
        check(
            path_open(dirFd, LOOKUP_SYMLINK_FOLLOW, pathPtr.address.toInt(), bytes.size, 0, READ_RIGHTS, 0L, 0, fdOut.address.toInt()),
            "Cannot open $path",
        )
        fdOut.loadInt()
    }
    return try {
        AccessorRad(WasiFdAccess(fd, path, fileSize(fd, path)))
    } catch (e: Throwable) {
        fd_close(fd)
        throw e
    }
}

private fun fileSize(fd: Int, path: String): Long = withScopedMemoryAllocator { alloc ->
    val stat = alloc.allocate(FILESTAT_SIZE)
    check(fd_filestat_get(fd, stat.address.toInt()), "Cannot stat $path")
    if ((stat + FILESTAT_TYPE).loadByte().toInt() == FILETYPE_DIRECTORY) {
        throw IOException("Cannot open $path: is a directory")
    }
    (stat + FILESTAT_SIZE_OFFSET).loadLong()
}

/** WASI fd numbers are reused after `fd_close`, so the fd is used only inside a lease. */
private class WasiFdAccess(
    private val fd: Int,
    private val path: String,
    override val size: Long,
) : SharedDataAccessor(EMPTY_AUTO_CLOSEABLE_ARRAY) {

    override fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int =
        withLease {
            withScopedMemoryAllocator { alloc ->
                val buf = alloc.allocate(length)
                val iovec = alloc.allocate(8)
                iovec.storeInt(buf.address.toInt())
                (iovec + 4).storeInt(length)
                val nOut = alloc.allocate(4)
                check(fd_pread(fd, iovec.address.toInt(), 1, position, nOut.address.toInt()), "Cannot read $path at $position")
                val n = nOut.loadInt()
                // Linear memory → GC array; no host call per byte.
                for (i in 0 until n) {
                    bytes[offset + i] = (buf + i).loadByte()
                }
                // 0 inside `size` means the file shrank after open: report end of data.
                if (n > 0) n else -1
            }
        }

    override fun releaseApi() = check(fd_close(fd), "Cannot close $path")
}

/** The preopened directory fd that [path] lies under, and [path] relative to it. */
private fun resolvePreopen(path: String): Pair<Int, String> {
    val preopens = PREOPENS
    if (!path.startsWith('/')) {
        val first = preopens.firstOrNull() ?: throw noPreopen(path)
        return first.second to path
    }
    // Longest matching directory wins, compared by whole path components.
    for ((dir, fd) in preopens.sortedByDescending { it.first.length }) {
        val root = dir.trimEnd('/')
        if (path == root) return fd to "."
        if (path.startsWith("$root/")) return fd to path.substring(root.length + 1)
    }
    throw noPreopen(path)
}

private fun noPreopen(path: String) = IOException(
    "Cannot open $path: it is not under a directory the WASI host preopened " +
        "(e.g. Node: new WASI({ preopens: { '/data': '/real/dir' } }))",
)

/** Preopens start at fd 3 (after stdio) and end at the first fd that is not one (EBADF). */
private val PREOPENS: List<Pair<String, Int>> by lazy {
    val result = ArrayList<Pair<String, Int>>()
    var fd = 3
    while (true) {
        val name = withScopedMemoryAllocator { alloc ->
            val prestat = alloc.allocate(8)
            val err = fd_prestat_get(fd, prestat.address.toInt())
            if (err == ERRNO_BADF) return@withScopedMemoryAllocator null
            check(err, "Cannot inspect preopened fd $fd")
            val len = (prestat + 4).loadInt()
            val buf = alloc.allocate(len)
            check(fd_prestat_dir_name(fd, buf.address.toInt(), len), "Cannot name preopened fd $fd")
            ByteArray(len) { (buf + it).loadByte() }.decodeToString().trimEnd('\u0000')
        } ?: break
        result += name to fd
        fd++
    }
    result
}

private fun MemoryAllocator.write(bytes: ByteArray): Pointer {
    val ptr = allocate(bytes.size.coerceAtLeast(1))
    for (i in bytes.indices) (ptr + i).storeByte(bytes[i])
    return ptr
}

private fun check(errno: Int, what: String) {
    if (errno != 0) throw IOException("$what: WASI errno $errno")
}

private const val ERRNO_BADF = 8
private const val LOOKUP_SYMLINK_FOLLOW = 1
private const val FILETYPE_DIRECTORY = 3
private const val FILESTAT_SIZE = 64
private const val FILESTAT_TYPE = 16
private const val FILESTAT_SIZE_OFFSET = 32
/** `fd_read` | `fd_seek` | `fd_filestat_get`: hosts require `fd_seek` for `fd_pread` too. */
private const val READ_RIGHTS: Long = (1L shl 1) or (1L shl 2) or (1L shl 21)

@WasmImport("wasi_snapshot_preview1", "fd_prestat_get")
private external fun fd_prestat_get(fd: Int, resultPtr: Int): Int

@WasmImport("wasi_snapshot_preview1", "fd_prestat_dir_name")
private external fun fd_prestat_dir_name(fd: Int, pathPtr: Int, pathLen: Int): Int

@Suppress("LongParameterList")
@WasmImport("wasi_snapshot_preview1", "path_open")
private external fun path_open(
    fd: Int,
    dirflags: Int,
    pathPtr: Int,
    pathLen: Int,
    oflags: Int,
    rightsBase: Long,
    rightsInheriting: Long,
    fdFlags: Int,
    resultPtr: Int,
): Int

@WasmImport("wasi_snapshot_preview1", "fd_filestat_get")
private external fun fd_filestat_get(fd: Int, resultPtr: Int): Int

@WasmImport("wasi_snapshot_preview1", "fd_pread")
private external fun fd_pread(fd: Int, iovsPtr: Int, iovsLen: Int, offset: Long, resultPtr: Int): Int

@WasmImport("wasi_snapshot_preview1", "fd_close")
private external fun fd_close(fd: Int): Int
