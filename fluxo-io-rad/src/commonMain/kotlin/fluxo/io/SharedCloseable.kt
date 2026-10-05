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

    /**
     * Owners and leases packed into one atomic `Long`, so every transition is a single
     * allocation-free CAS or add, on every target.
     *
     * - Owners (high 32 bits): holders that must [close] once ([retain] adds one).
     * - Leases (low 32 bits): in-flight accesses to the resource (see [tryAcquireLease]).
     *
     * Two counters, not one: a lease is only granted while an owner exists, so once the last
     * owner closes, new accesses fail at once while in-flight ones finish. A single shared
     * count would let a steady stream of reads keep a closed resource alive forever.
     *
     * The resource is released by whichever decrement produces exactly zero. Nothing can
     * increment once owners reach zero, so exactly one caller observes the zero: release runs
     * once, without a separate flag.
     */
    private val state = atomic(ONE_OWNER)

    /**
     * Listeners live apart from [state] because they change rarely and only matter at release;
     * `null` once release has taken them for notification.
     */
    private val listeners = atomic<Array<SharedCloseListener>?>(emptyArray())

    /** `false` from the moment the last owner closes, before [onSharedClose] runs. */
    public val isOpen: Boolean
        get() = state.value >= ONE_OWNER

    public fun addOnSharedCloseListener(cb: (cause: Throwable?) -> Unit) {
        while (true) {
            val current = listeners.value
            if (current == null || current.any { it == cb }) {
                return
            }
            if (listeners.compareAndSet(current, current + cb)) {
                return
            }
        }
    }

    public fun removeOnSharedCloseListener(cb: (cause: Throwable?) -> Unit) {
        while (true) {
            val current = listeners.value ?: return
            val index = current.indexOfFirst { it == cb }
            if (index < 0) {
                return
            }
            val update = Array(current.size - 1) { current[if (it < index) it else it + 1] }
            if (listeners.compareAndSet(current, update)) {
                return
            }
        }
    }

    /**
     * Releases one ownership. A repeated close after the last owner is gone is a no-op.
     *
     * Never waits for in-flight leases: when one is still running, the release happens at
     * the end of that access instead (and its failures go to the fluxo-io logger, as no caller
     * is left to receive them).
     */
    public final override fun close() {
        while (true) {
            val current = state.value
            if (current < ONE_OWNER) {
                return
            }
            if (!state.compareAndSet(current, current - ONE_OWNER)) {
                continue
            }
            if (current == ONE_OWNER) {
                release()?.let { throw it }
            }
            return
        }
    }

    /**
     * Adds an owner. Fails for an already released instance: reviving it would hand out
     * access to a freed resource.
     */
    public fun retain() {
        check(tryRetain()) { "Attempt to retain an already released instance: $this" }
    }

    /** [retain] that reports a released instance with `false` instead of throwing. */
    internal fun tryRetain(): Boolean {
        while (true) {
            val current = state.value
            if (current < ONE_OWNER) {
                return false
            }
            if (state.compareAndSet(current, current + ONE_OWNER)) {
                return true
            }
        }
    }

    /**
     * Starts an access to the resource; `false` once the last owner closed. Every `true` must
     * be paired with exactly one [releaseLease], which keeps the resource alive until then.
     */
    internal fun tryAcquireLease(): Boolean {
        while (true) {
            val current = state.value
            if (current < ONE_OWNER) {
                return false
            }
            if (state.compareAndSet(current, current + 1L)) {
                return true
            }
        }
    }

    /**
     * Runs [block] while the resource is guaranteed alive: it cannot be released before
     * [block] returns, and once the last owner closed this throws instead of touching it.
     * The only sound way to reach a releasable resource (an unmapped buffer or a reused file
     * descriptor would otherwise be read).
     */
    @Throws(IOException::class)
    internal inline fun <T> withLease(block: () -> T): T {
        if (!tryAcquireLease()) {
            throw IOException("RandomAccessData is already closed")
        }
        try {
            return block()
        } finally {
            releaseLease()
        }
    }

    /** Ends an access started by a successful [tryAcquireLease]. */
    internal fun releaseLease() {
        if (state.addAndGet(-1L) == 0L) {
            release()?.let { e ->
                LOGGER?.invoke("Failed to release a shared resource after its last access", e)
            }
        }
    }

    /** Runs once, by the caller whose decrement made [state] zero. Returns the failure. */
    private fun release(): Throwable? {
        var closeCause: Throwable? = null
        @Suppress("TooGenericExceptionCaught")
        try {
            onSharedClose()
        } catch (e: Throwable) {
            closeCause = e
        }

        // Drain in batches: a listener registered while earlier ones run (even from inside one)
        // is still notified.
        var listenerFailure: Throwable? = null
        while (true) {
            val batch = takeListeners() ?: break
            listenerFailure = notifyListeners(batch, closeCause, listenerFailure)
        }

        if (closeCause != null) {
            listenerFailure?.let { closeCause.addSuppressed(it) }
            return closeCause
        }
        return listenerFailure
    }

    /**
     * Takes the registered listeners, leaving an empty registry. Only an empty registry is
     * sealed with `null` (then `null` is returned), after which adds return silently because
     * nothing is left to observe.
     */
    private fun takeListeners(): Array<SharedCloseListener>? {
        while (true) {
            val batch = listeners.value ?: return null
            val next = if (batch.isEmpty()) null else emptyArray<SharedCloseListener>()
            if (listeners.compareAndSet(batch, next)) {
                return if (next == null) null else batch
            }
        }
    }

    /** Notifies every listener even if some throw; returns the first failure, others suppressed. */
    private fun notifyListeners(
        batch: Array<SharedCloseListener>,
        cause: Throwable?,
        failure: Throwable?,
    ): Throwable? {
        var first = failure
        for (listener in batch) {
            @Suppress("TooGenericExceptionCaught")
            try {
                listener(cause)
            } catch (e: Throwable) {
                val f = first
                if (f == null) first = e else f.addSuppressed(e)
            }
        }
        return first
    }

    /**
     * Called exactly once, when the last owner has closed and no access is in flight.
     *
     * Implementations should release any resources held by the instance.
     *
     * May throw: the exception reaches the caller of the `close()` that released it, but
     * when the release waits for the last in-flight access, it runs there and is only
     * logged.
     */
    @Throws(IOException::class)
    protected abstract fun onSharedClose()

    private companion object {
        private const val ONE_OWNER = 1L shl 32
    }
}
