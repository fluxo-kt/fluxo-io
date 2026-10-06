package fluxo.io.internal

import fluxo.io.IOException
import fluxo.io.SharedCloseable
import kotlin.coroutines.cancellation.CancellationException

/**
 * Shared accessor for read-only thread-safe random reads from underlying data.
 */
@ThreadSafe
@SubclassOptInRequired(InternalFluxoIoApi::class)
internal abstract class SharedDataAccessor
protected constructor(
    resources: Array<out AutoCloseable>,
) : SharedResource(resources) {

    @Blocking
    @Throws(IOException::class)
    abstract fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int
}

/**
 * [SharedDataAccessor] for natively non-blocking sources (browser `Blob`, Node's async `fs`):
 * the read suspends instead of blocking a thread.
 * [read] takes the lease and holds it across the suspension (`withLease` is inline), so a close
 * while a read is pending defers the release until it completes. Sources implement only
 * [readLeased], so none can skip the lease.
 */
@ThreadSafe
@SubclassOptInRequired(InternalFluxoIoApi::class)
internal abstract class SharedAsyncDataAccessor
protected constructor(
    resources: Array<out AutoCloseable>,
) : SharedResource(resources) {

    @Throws(IOException::class, CancellationException::class)
    suspend fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int =
        withLease { readLeased(bytes, position, offset, length) }

    /** The positional read; called only inside the lease [read] holds. */
    @Throws(IOException::class, CancellationException::class)
    protected abstract suspend fun readLeased(
        bytes: ByteArray, position: Long, offset: Int, length: Int,
    ): Int
}

/**
 * The owned resource behind a data accessor: its size and how it is released. Separate from
 * the read so blocking and suspending accessors share one release template.
 */
@ThreadSafe
@SubclassOptInRequired(InternalFluxoIoApi::class)
internal abstract class SharedResource
protected constructor(
    private val resources: Array<out AutoCloseable>,
) : SharedCloseable() {

    /**
     * Read on every slice and handle construction, outside any lease: take it once when opening.
     * A per-call query (`FileChannel.size()`) is a syscall, fails after close, and on an
     * interrupted thread closes an interruptible channel for every handle.
     */
    abstract val size: Long

    /**
     * Release the API-specific resource (mmap unmap, pool drain, …). May throw;
     * the [onSharedClose] template still closes the [resources] array. Default no-op.
     */
    @Throws(IOException::class)
    protected open fun releaseApi() {}

    /**
     * Template: [releaseApi] then close [resources]; the latter always runs even
     * if release throws. Errors merge per the existing convention (latest primary,
     * earlier suppressed). Non-synchronized — guarded by [SharedCloseable].
     */
    final override fun onSharedClose() {
        var t: Throwable? = null
        try {
            releaseApi()
        } catch (e: Throwable) {
            t = e
        }
        for (resource in resources) {
            try {
                resource.close()
            } catch (e: Throwable) {
                if (t != null) e.addSuppressed(t)
                t = e
            }
        }
        if (t != null) {
            throw t
        }
    }
}
