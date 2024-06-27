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
     *
     * Note: Can be non-synchronized as guarded by [SharedCloseable]
     * and normally by the object itself.
     */
    @CallSuper
    override fun onSharedClose() {
        var t: Throwable? = null
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
