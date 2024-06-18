@file:Suppress("KDocUnresolvedReference")

package fluxo.io

import fluxo.io.internal.ThreadSafe
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job

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

    // Job is used to handle shared close listeners.
    private val job: CompletableJob = Job()

    public final override fun close() {
        if (retainsCount.decrementAndGet() == 0) {
            @Suppress("TooGenericExceptionCaught")
            try {
                onSharedClose()
                job.complete()
            } catch (e: Throwable) {
                job.completeExceptionally(e)
                throw e
            }
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

    public fun onSharedClose(cb: CompletionHandler): DisposableHandle =
        job.invokeOnCompletion(cb)

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
