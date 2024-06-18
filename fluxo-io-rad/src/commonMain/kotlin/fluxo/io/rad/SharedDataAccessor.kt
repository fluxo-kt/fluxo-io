package fluxo.io.rad

import fluxo.io.IOException
import fluxo.io.SharedCloseable
import fluxo.io.internal.Blocking
import fluxo.io.internal.InternalForInheritanceApi
import fluxo.io.internal.ThreadSafe

/**
 * Shared accessor for read-only thread-safe random reads from underlying data.
 */
@ThreadSafe
@SubclassOptInRequired(InternalForInheritanceApi::class)
internal abstract class SharedDataAccessor
internal constructor() : SharedCloseable() {

    protected abstract val api: AutoCloseable

    abstract val size: Long

    @Blocking
    @Throws(IOException::class)
    abstract fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int

    /**
     *
     * Note: Can be non-synchronized as guarded by [SharedCloseable]
     * and normally by the object itself.
     */
    override fun onSharedClose() {
        api.close()
    }
}
