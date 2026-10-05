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
 * [SharedDataAccessor] for natively non-blocking sources (JVM `AsynchronousFileChannel`,
 * browser `Blob`, Node `fs.promises`): the read suspends instead of blocking a thread.
 * A read holds its lease across the suspension (`withLease` is inline), so a close while the
 * read is pending defers the release until the read completes.
 */
@ThreadSafe
@SubclassOptInRequired(InternalFluxoIoApi::class)
internal abstract class SharedAsyncDataAccessor
protected constructor(
    resources: Array<out AutoCloseable>,
) : SharedResource(resources) {

    @Throws(IOException::class, CancellationException::class)
    abstract suspend fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int
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
