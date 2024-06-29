@file:Suppress("KDocUnresolvedReference")

package fluxo.io

import fluxo.io.internal.ThreadSafe
import fluxo.io.util.ConcurrentHashMap
import kotlinx.atomicfu.atomic

/**
 * A [SharedCloseable] is a resource that can be shared between multiple consumers.
 * It is closed when the last consumer releases it.
 *
 * @see com.bloomberg.selekt.SharedCloseable
 */
@ThreadSafe
public abstract class SharedCloseable : Closeable {

    private val retainsCount = atomic(initial = 1)

    public val isOpen: Boolean
        get() = retainsCount.value > 0


    private val sharedCloseListeners = ConcurrentHashMap<(cause: Throwable?) -> Unit, Boolean>()

    public fun addOnSharedCloseListener(cb: (cause: Throwable?) -> Unit) {
        sharedCloseListeners[cb] = true
    }

    public fun removeOnSharedCloseListener(cb: (cause: Throwable?) -> Unit) {
        sharedCloseListeners.remove(cb)
    }


    public final override fun close() {
        if (retainsCount.decrementAndGet() != 0) {
            return
        }

        @Suppress("TooGenericExceptionCaught")
        try {
            onSharedClose()
            notifyListenersOnce(e = null)
        } catch (e: Throwable) {
            while (true) {
                try {
                    notifyListenersOnce(e)
                    break
                } catch (e2: Throwable) {
                    e.addSuppressed(e2)
                }
            }
            throw e
        }
    }

    private fun notifyListenersOnce(e: Throwable?) {
        val iterator = sharedCloseListeners.keys.iterator()
        for (listener in iterator) {
            iterator.remove()
            listener(e)
        }
    }

    /**
     * Called at most once when the last consumer releases the resource.
     *
     * Implementations should release any resources held by the instance.
     *
     * Can throw an exception, it will be properly propagated to the caller.
     */
    @Throws(IOException::class)
    protected abstract fun onSharedClose()


    public fun retain() {
        while (true) {
            val count = retainsCount.value
            check(count > 0) {
                "Attempt to retain an already released instance: $this"
            }
            if (retainsCount.compareAndSet(expect = count, update = count + 1)) {
                return
            }
        }
    }
}
