@file:Suppress("KDocUnresolvedReference")

package fluxo.io

import fluxo.io.internal.ThreadSafe
import kotlinx.atomicfu.atomic

private typealias SharedCloseListener = (cause: Throwable?) -> Unit

/**
 * A [SharedCloseable] is a resource that can be shared between multiple consumers.
 * It is closed when the last consumer releases it.
 *
 * @see com.bloomberg.selekt.SharedCloseable
 */
@ThreadSafe
public abstract class SharedCloseable : Closeable {

    private val state = atomic<CloseState>(CloseState.Open(retains = 1))

    public val isOpen: Boolean
        get() = state.value is CloseState.Open

    public fun addOnSharedCloseListener(cb: (cause: Throwable?) -> Unit) {
        while (true) {
            when (val current = state.value) {
                is CloseState.Closed -> return
                is CloseState.Closing -> {
                    val update = current.copy(listeners = addListener(current.listeners, cb))
                    if (state.compareAndSet(expect = current, update = update)) {
                        return
                    }
                }
                is CloseState.Open -> {
                    val update = current.copy(listeners = addListener(current.listeners, cb))
                    if (state.compareAndSet(expect = current, update = update)) {
                        return
                    }
                }
            }
        }
    }

    public fun removeOnSharedCloseListener(cb: (cause: Throwable?) -> Unit) {
        while (true) {
            when (val current = state.value) {
                is CloseState.Closed -> return
                is CloseState.Closing -> {
                    val update = current.copy(listeners = removeListener(current.listeners, cb))
                    if (state.compareAndSet(expect = current, update = update)) {
                        return
                    }
                }
                is CloseState.Open -> {
                    val update = current.copy(listeners = removeListener(current.listeners, cb))
                    if (state.compareAndSet(expect = current, update = update)) {
                        return
                    }
                }
            }
        }
    }


    public final override fun close() {
        if (!releaseRetain()) {
            return
        }

        var closeCause: Throwable? = null
        @Suppress("TooGenericExceptionCaught")
        try {
            onSharedClose()
        } catch (e: Throwable) {
            closeCause = e
        }

        val listenerCause = notifyAndClose(closeCause)
        if (closeCause != null) {
            if (listenerCause != null) {
                closeCause.addSuppressed(listenerCause)
            }
            throw closeCause
        }
        if (listenerCause != null) {
            throw listenerCause
        }
    }

    private fun releaseRetain(): Boolean {
        while (true) {
            when (val current = state.value) {
                is CloseState.Closed,
                is CloseState.Closing -> return false
                is CloseState.Open -> {
                    val update = current.release() ?: CloseState.Closing(current.listeners)
                    if (state.compareAndSet(expect = current, update = update)) {
                        return update is CloseState.Closing
                    }
                }
            }
        }
    }

    private fun notifyAndClose(closeCause: Throwable?): Throwable? {
        var failure: Throwable? = null
        while (true) {
            when (val current = state.value) {
                is CloseState.Closed -> return failure
                is CloseState.Closing -> {
                    if (current.listeners.isNotEmpty() && state.compareAndSet(
                            expect = current,
                            update = current.withoutListeners(),
                        )
                    ) {
                        failure = notifyListenersOnce(current.listeners, closeCause, failure)
                    } else if (state.compareAndSet(
                            expect = current,
                            update = CloseState.Closed,
                        )
                    ) {
                        return failure
                    }
                }
                is CloseState.Open -> error("SharedCloseable reopened during close: $this")
            }
        }
    }

    private fun notifyListenersOnce(
        listeners: Array<SharedCloseListener>,
        closeCause: Throwable?,
        initialFailure: Throwable?,
    ): Throwable? {
        var failure = initialFailure
        for (listener in listeners) {
            try {
                listener(closeCause)
            } catch (e: Throwable) {
                val currentFailure = failure
                if (currentFailure == null) {
                    failure = e
                } else {
                    currentFailure.addSuppressed(e)
                }
            }
        }
        return failure
    }

    private fun addListener(
        listeners: Array<SharedCloseListener>,
        cb: SharedCloseListener,
    ): Array<SharedCloseListener> {
        for (listener in listeners) {
            if (listener == cb) {
                return listeners
            }
        }
        return Array(listeners.size + 1) { index ->
            if (index == listeners.size) cb else listeners[index]
        }
    }

    private fun removeListener(
        listeners: Array<SharedCloseListener>,
        cb: SharedCloseListener,
    ): Array<SharedCloseListener> {
        var removeIndex = -1
        for (index in listeners.indices) {
            if (listeners[index] == cb) {
                removeIndex = index
                break
            }
        }
        if (removeIndex < 0) {
            return listeners
        }
        return Array(listeners.size - 1) { index ->
            listeners[if (index < removeIndex) index else index + 1]
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
            when (val current = state.value) {
                is CloseState.Closed,
                is CloseState.Closing -> {
                    check(false) {
                        "Attempt to retain an already released instance: $this"
                    }
                }
                is CloseState.Open -> {
                    val update = current.copy(retains = current.retains + 1)
                    if (state.compareAndSet(expect = current, update = update)) {
                        return
                    }
                }
            }
        }
    }

    private sealed interface CloseState {
        data class Open(
            val retains: Int,
            val listeners: Array<SharedCloseListener> = emptyArray(),
        ) : CloseState {
            fun release(): Open? =
                if (retains > 1) copy(retains = retains - 1) else null
        }

        data class Closing(
            val listeners: Array<SharedCloseListener>,
        ) : CloseState {
            fun withoutListeners(): Closing =
                copy(listeners = emptyArray())
        }

        data object Closed : CloseState
    }
}
