@file:Suppress("KDocUnresolvedReference")

package fluxo.io

import fluxo.io.internal.ThreadSafe
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.DisposableHandle

/**
 * A [SharedCloseable] is a resource that can be shared between multiple consumers.
 * It is closed when the last consumer releases it.
 *
 * @see com.bloomberg.selekt.SharedCloseable
 */
@ThreadSafe
public expect abstract class SharedCloseable : AutoCloseable {

    public val isOpen: Boolean

    final override fun close()

    protected abstract fun onSharedClose()

    public fun onSharedClose(cb: CompletionHandler): DisposableHandle

    public fun retain()
}
