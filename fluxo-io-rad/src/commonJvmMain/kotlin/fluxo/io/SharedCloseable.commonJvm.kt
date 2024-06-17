package fluxo.io

import fluxo.io.internal.ThreadSafe
import java.io.Closeable
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.io.IOException

@ThreadSafe
public actual abstract class SharedCloseable : Closeable, AutoCloseable {

    private val retainCount = atomic(initial = 1)

    public actual val isOpen: Boolean
        get() = retainCount.value > 0

    private val job: CompletableJob = Job()

    actual override fun close() {
        if (retainCount.decrementAndGet() == 0) {
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

    @Throws(IOException::class)
    protected actual abstract fun onSharedClose()

    public actual fun onSharedClose(cb: CompletionHandler): DisposableHandle =
        job.invokeOnCompletion(cb)

    public actual fun retain() {
        check(retainCount.getAndIncrement() > 0) {
            retainCount.decrementAndGet()
            "Attempt to retain an already released instance: $this"
        }
    }
}

