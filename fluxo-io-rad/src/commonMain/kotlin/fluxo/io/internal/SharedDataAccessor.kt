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
