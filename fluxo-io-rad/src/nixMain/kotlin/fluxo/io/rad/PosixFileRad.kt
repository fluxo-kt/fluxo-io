@file:OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)

package fluxo.io.rad

import fluxo.io.IOException
import fluxo.io.internal.AccessorRad
import fluxo.io.internal.SharedDataAccessor
import fluxo.io.util.EMPTY_AUTO_CLOSEABLE_ARRAY
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.EINTR
import platform.posix.O_CLOEXEC
import platform.posix.O_RDONLY
import platform.posix.S_IFDIR
import platform.posix.S_IFMT
import platform.posix.close
import platform.posix.errno
import platform.posix.fstat
import platform.posix.off_tVar
import platform.posix.open
import platform.posix.pread
import platform.posix.stat
import platform.posix.strerror

/**
 * Opens [path] on Apple, Linux and Android Native with POSIX calls: one `pread` per read, which
 * takes its own offset, so concurrent reads on one descriptor need no lock and no seek.
 * `O_CLOEXEC` keeps the descriptor out of child processes.
 */
internal actual fun openPlatformFile(path: String): RandomAccessData {
    val fd = open(path, O_RDONLY or O_CLOEXEC)
    if (fd < 0) {
        throw IOException("Cannot open $path: ${lastError()}")
    }
    return try {
        AccessorRad(FdAccess(fd, path, fileSize(fd, path)))
    } catch (e: Throwable) {
        close(fd)
        throw e
    }
}

private fun fileSize(fd: Int, path: String): Long = memScoped {
    val st = alloc<stat>()
    if (fstat(fd, st.ptr) != 0) {
        throw IOException("Cannot stat $path: ${lastError()}")
    }
    // open(O_RDONLY) succeeds on a directory; reading it would fail later with EISDIR.
    if ((st.st_mode.toInt() and S_IFMT) == S_IFDIR) {
        throw IOException("Cannot open $path: is a directory")
    }
    st.st_size.convert()
}

/**
 * The descriptor is used only inside a lease: once closed, its number can be reused by any
 * later `open` in the process, so a read after close would read someone else's file.
 */
private class FdAccess(
    private val fd: Int,
    private val path: String,
    override val size: Long,
) : SharedDataAccessor(EMPTY_AUTO_CLOSEABLE_ARRAY) {

    override fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int {
        // 32-bit Android Native has a 32-bit off_t; a larger offset would wrap silently.
        if (OFF_T_IS_32_BIT && position > Int.MAX_VALUE) {
            throw IOException("$path: offset $position needs a 64-bit off_t on this target")
        }
        return withLease {
            bytes.usePinned { pinned ->
                var n: Long
                do {
                    n = pread(fd, pinned.addressOf(offset), length.convert(), position.convert())
                        .convert()
                } while (n < 0 && errno == EINTR)
                when {
                    n < 0 -> throw IOException("Cannot read $path at $position: ${lastError()}")
                    // Callers read only inside `size`, so 0 means the file shrank after open:
                    // report end of data instead of a zero-length read they would retry.
                    n == 0L -> -1
                    else -> n.toInt()
                }
            }
        }
    }

    override fun releaseApi() {
        if (close(fd) != 0) {
            throw IOException("Cannot close $path: ${lastError()}")
        }
    }
}

private val OFF_T_IS_32_BIT: Boolean = sizeOf<off_tVar>() == Int.SIZE_BYTES.toLong()

private fun lastError(): String = strerror(errno)?.toKString() ?: "errno ${errno}"
