package fluxo.io.internal

import fluxo.io.IOException
import fluxo.io.SharedCloseable

/**
 * Shared accessor for read-only thread-safe random reads from underlying data.
 */
@ThreadSafe
@SubclassOptInRequired(InternalFluxoIoApi::class)
internal abstract class SharedDataAccessor
protected constructor(
    private val resources: Array<out AutoCloseable>
) : SharedCloseable() {

    abstract val size: Long

    @Blocking
    @Throws(IOException::class)
    abstract fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int

    /**
     * Release the API-specific resource (mmap unmap, drain pool, etc.). Default no-op.
     * A throw here MUST NOT skip closing the [resources] array — the [onSharedClose]
     * template enforces that via try-finally semantics, so impls that previously did
     * `api.release(); super.onSharedClose()` and leaked on release-throw cannot be
     * reintroduced. Caller is single-shot from [SharedCloseable.close]; need not
     * be re-entrant or idempotent.
     */
    @Throws(IOException::class)
    protected open fun releaseApi() {}

    /**
     * Final template: release the API (may throw), then ALWAYS close the [resources]
     * array. If both phases throw, the resource-close exception becomes primary with
     * the release exception attached as suppressed (matching the existing
     * per-resource error-merge convention below).
     *
     * Note: Can be non-synchronized as guarded by [SharedCloseable]
     * and normally by the object itself.
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
