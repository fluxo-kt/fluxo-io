package fluxo.io.rad

import fluxo.io.SharedCloseable
import fluxo.io.internal.InternalForInheritanceApi
import kotlinx.io.IOException

@SubclassOptInRequired(InternalForInheritanceApi::class)
public abstract class AbstractRadAccess
internal constructor() : SharedCloseable() {

    protected abstract val api: AutoCloseable

    public abstract val size: Long

    @Throws(IOException::class)
    public abstract fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int

    /**
     *
     * Note: Can be non-synchronized as guarded by [SharedCloseable]
     * and normally by the object itself.
     */
    override fun onSharedClose() {
        api.close()
    }
}
