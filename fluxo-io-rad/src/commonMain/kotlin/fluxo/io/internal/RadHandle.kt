package fluxo.io.internal

import fluxo.io.IOException
import fluxo.io.rad.RandomAccessData
import fluxo.io.util.checkOffsetAndCount
import kotlinx.atomicfu.atomic

/**
 * Lifetime of a [RandomAccessData] handle, shared by every implementation.
 *
 * Two kinds of instance exist, distinguished only by [owner]:
 * - a handle (`owner == this`): the root returned by a factory, or a [share] result.
 *   It holds one ownership of the underlying data and must be closed exactly once.
 * - a view (`owner` = the handle it came from): a [slice]. It owns nothing, needs no
 *   close, and reads only while its owning handle is open.
 *
 * **A closed handle never reads, and neither do its slices**, even while another handle
 * keeps the same data open. Otherwise a read-after-close would succeed or fail depending
 * on unrelated handles, hiding the caller's bug until the close order changes (the same
 * reason two `FileChannel`s on one file close independently). Memory safety does not
 * depend on this check: the resource itself is guarded by [fluxo.io.SharedCloseable]
 * leases. So the check is a single volatile read; a close racing a read may let that
 * read finish, which is harmless.
 */
@ThreadSafe
internal abstract class RadHandle(owner: RadHandle?) : RandomAccessData {

    private val owner: RadHandle = owner ?: this

    /** Meaningful on handles only; a view reads its owner's flag. */
    private val closed = atomic(false)

    /** Throws once the owning handle is closed. Call first in every public data access. */
    @Throws(IOException::class)
    protected fun ensureOpen() {
        if (owner.closed.value) {
            throw IOException(CLOSED_HANDLE_MESSAGE)
        }
    }

    final override fun slice(position: Long, length: Long): RandomAccessData {
        checkOffsetAndCount(size, position, length)
        ensureOpen()
        return view(position, length, owner)
    }

    final override fun share(): RandomAccessData {
        ensureOpen()
        acquireShared()
        return view(0L, size, owner = null)
    }

    @Deprecated(
        "Use slice(position, length).share(): the same owned sub-range, made explicit.",
        ReplaceWith("slice(position, length).share()"),
        DeprecationLevel.ERROR,
    )
    final override fun subsection(position: Long, length: Long): RandomAccessData =
        slice(position, length).share()

    /**
     * Idempotent per handle (the [AutoCloseable] contract): a handle holds exactly one
     * ownership, so a second close must not release it again, which would free data still
     * used by other handles. On a view it does nothing.
     */
    final override fun close() {
        if (owner === this && closed.compareAndSet(expect = false, update = true)) {
            releaseShared()
        }
    }

    /** A new instance over `[position, position + length)` of this one, owned by [owner]. */
    protected abstract fun view(position: Long, length: Long, owner: RadHandle?): RandomAccessData

    /** Adds one ownership of the underlying data; throws [IOException] once it is released. */
    @Throws(IOException::class)
    protected abstract fun acquireShared()

    /** Gives back the ownership this handle holds. Called at most once per handle. */
    protected abstract fun releaseShared()
}

/** Shared by every handle type, so a read on a closed handle reports the same fix. */
internal const val CLOSED_HANDLE_MESSAGE: String =
    "RandomAccessData is closed. Use share() to keep an independent handle open."
