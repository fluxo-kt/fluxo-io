@file:OptIn(ExperimentalForeignApi::class)

package fluxo.io.rad

import fluxo.io.IOException
import fluxo.io.internal.AccessorRad
import fluxo.io.internal.SharedDataAccessor
import fluxo.io.util.EMPTY_AUTO_CLOSEABLE_ARRAY
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.windows.CloseHandle
import platform.windows.CreateFileW
import platform.windows.DWORDVar
import platform.windows.ERROR_HANDLE_EOF
import platform.windows.FILE_ATTRIBUTE_NORMAL
import platform.windows.FILE_SHARE_DELETE
import platform.windows.FILE_SHARE_READ
import platform.windows.FILE_SHARE_WRITE
import platform.windows.GENERIC_READ
import platform.windows.GetFileSizeEx
import platform.windows.GetLastError
import platform.windows.HANDLE
import platform.windows.INVALID_HANDLE_VALUE
import platform.windows.LARGE_INTEGER
import platform.windows.OPEN_EXISTING
import platform.windows.OVERLAPPED
import platform.windows.ReadFile

/**
 * Opens [path] on Windows. Reads are `ReadFile` with the offset in an `OVERLAPPED` on a
 * synchronous handle: positional, so concurrent reads on one handle need no lock and no seek.
 * Sharing READ | WRITE | DELETE matches POSIX: holding the file open does not stop others
 * from writing, renaming or deleting it.
 */
internal actual fun openPlatformFile(path: String): RandomAccessData {
    val handle = CreateFileW(
        longPath(path),
        GENERIC_READ,
        (FILE_SHARE_READ or FILE_SHARE_WRITE or FILE_SHARE_DELETE).convert(),
        null,
        OPEN_EXISTING.convert(),
        FILE_ATTRIBUTE_NORMAL.convert(),
        null,
    )
    if (handle == null || handle == INVALID_HANDLE_VALUE) {
        throw IOException("Cannot open $path: Windows error ${GetLastError()}")
    }
    return try {
        AccessorRad(HandleAccess(handle, path, fileSize(handle, path)))
    } catch (e: Throwable) {
        CloseHandle(handle)
        throw e
    }
}

/**
 * Absolute paths longer than MAX_PATH (260) need the `\\?\` prefix, which also turns off
 * `/` → `\` translation, so separators are normalised first. Relative paths cannot use it.
 */
private fun longPath(path: String): String {
    val absolute = path.length > 2 && path[1] == ':' || path.startsWith("\\\\")
    if (path.length < MAX_PATH || !absolute || path.startsWith("\\\\?\\")) {
        return path
    }
    val normalized = path.replace('/', '\\')
    return if (normalized.startsWith("\\\\")) "\\\\?\\UNC\\" + normalized.substring(2)
    else "\\\\?\\$normalized"
}

private const val MAX_PATH = 260

private fun fileSize(handle: HANDLE, path: String): Long = memScoped {
    val size = alloc<LARGE_INTEGER>()
    if (GetFileSizeEx(handle, size.ptr) == 0) {
        throw IOException("Cannot get the size of $path: Windows error ${GetLastError()}")
    }
    size.QuadPart
}

/** The handle is used only inside a lease: a closed handle value can be reused by Windows. */
private class HandleAccess(
    private val handle: HANDLE,
    private val path: String,
    override val size: Long,
) : SharedDataAccessor(EMPTY_AUTO_CLOSEABLE_ARRAY) {

    override fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int =
        withLease {
            memScoped {
                val overlapped = alloc<OVERLAPPED>()
                overlapped.Offset = position.toUInt()
                overlapped.OffsetHigh = (position ushr 32).toUInt()
                val read = alloc<DWORDVar>()
                val ok = bytes.usePinned { pinned ->
                    ReadFile(
                        handle,
                        pinned.addressOf(offset),
                        length.convert(),
                        read.ptr,
                        overlapped.ptr,
                    )
                }
                // 0 bytes inside `size` means the file shrank after open: report end of data.
                if (ok != 0) return@withLease read.value.toInt().takeIf { it > 0 } ?: -1
                val error = GetLastError()
                if (error == ERROR_HANDLE_EOF.convert<UInt>()) return@withLease -1
                throw IOException("Cannot read $path at $position: Windows error $error")
            }
        }

    override fun releaseApi() {
        if (CloseHandle(handle) == 0) {
            throw IOException("Cannot close $path: Windows error ${GetLastError()}")
        }
    }
}
