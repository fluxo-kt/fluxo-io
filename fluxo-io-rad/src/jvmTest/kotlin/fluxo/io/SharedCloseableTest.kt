package fluxo.io

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class SharedCloseableTest {

    @Test
    fun retainAfterFinalCloseThrows() {
        val closeable = TestCloseable()

        closeable.close()

        assertFalse(closeable.isOpen)
        assertFailsWith<IllegalStateException> { closeable.retain() }
        assertEquals(1, closeable.closeCount)
    }

    @Test
    fun onSharedCloseRunsOnceAfterBalancedRetainAndClose() {
        val closeable = TestCloseable()

        closeable.retain()
        closeable.retain()

        closeable.close()
        closeable.close()
        assertTrue(closeable.isOpen)
        assertEquals(0, closeable.closeCount)

        closeable.close()
        assertFalse(closeable.isOpen)
        assertEquals(1, closeable.closeCount)
    }

    /**
     * The read-after-close guard ([fluxo.io.internal.SharedDataAccessor]'s `checkOpen`) is only
     * sound if `isOpen` is already `false` when the resource is released: a concurrent reader that
     * serialises on the resource monitor must observe the closed state, not race a half-freed
     * resource. Lock that ordering deterministically — `releaseRetain()` must leave the `Open`
     * state before `onSharedClose()` runs. A refactor that freed resources before flipping state
     * would turn this red, flagging the guard as unsound before it can crash the JVM.
     */
    @Test
    fun resourceReleaseObservesClosedState() {
        lateinit var closeable: TestCloseable
        val openDuringClose = AtomicReference<Boolean>()
        closeable = TestCloseable { openDuringClose.set(closeable.isOpen) }

        closeable.close()

        assertEquals(false, openDuringClose.get()) // null would mean onSharedClose never ran
        assertEquals(1, closeable.closeCount)
    }

    @Test
    fun onSharedCloseFailureAfterRetainIsReportedToListenersAndRethrown() {
        val closeFailure = IOException("close")
        val listenerFailure = IllegalStateException("listener")
        val listenerCause = AtomicReference<Throwable?>()
        val closeable = TestCloseable { throw closeFailure }

        closeable.retain()
        closeable.addOnSharedCloseListener { cause ->
            listenerCause.set(cause)
            throw listenerFailure
        }

        closeable.close()
        val thrown = assertFailsWith<IOException> { closeable.close() }

        assertEquals(closeFailure, thrown)
        assertEquals(closeFailure, listenerCause.get())
        assertEquals(listOf(listenerFailure), thrown.suppressed.toList())
        assertFalse(closeable.isOpen)
        assertEquals(1, closeable.closeCount)
    }

    @Test
    fun throwingListenersDoNotPreventLaterListenersAndPreserveSuppressedFailures() {
        val closeable = TestCloseable()
        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second")
        val completedListeners = AtomicInteger()

        closeable.addOnSharedCloseListener {
            completedListeners.incrementAndGet()
            throw first
        }
        closeable.addOnSharedCloseListener {
            completedListeners.incrementAndGet()
        }
        closeable.addOnSharedCloseListener {
            completedListeners.incrementAndGet()
            throw second
        }

        val thrown = assertFailsWith<Throwable> { closeable.close() }
        val failures = listOf(thrown) + thrown.suppressed.toList()

        assertEquals(3, completedListeners.get())
        assertTrue(first in failures)
        assertTrue(second in failures)
        assertEquals(1, thrown.suppressed.size)
        assertFalse(closeable.isOpen)
        assertEquals(1, closeable.closeCount)
    }

    @Test
    fun listenerAddedDuringFinalCloseIsNotDropped() {
        val enteredClose = CountDownLatch(1)
        val finishClose = CountDownLatch(1)
        val listenerCalls = AtomicInteger()
        val closeFailure = AtomicReference<Throwable>()
        val closeable = TestCloseable {
            enteredClose.countDown()
            assertTrue(finishClose.await(1, TimeUnit.SECONDS))
        }

        val closer = Thread {
            try {
                closeable.close()
            } catch (e: Throwable) {
                closeFailure.set(e)
            }
        }
        closer.start()

        assertTrue(enteredClose.await(1, TimeUnit.SECONDS))
        closeable.addOnSharedCloseListener { listenerCalls.incrementAndGet() }
        assertEquals(0, listenerCalls.get())

        finishClose.countDown()
        closer.join(1_000)

        assertFalse(closer.isAlive)
        assertEquals(null, closeFailure.get())
        assertEquals(1, listenerCalls.get())
        assertFalse(closeable.isOpen)
        assertEquals(1, closeable.closeCount)
    }

    @Test
    fun listenerAddedDuringCloseNotificationIsNotDropped() {
        val enteredListener = CountDownLatch(1)
        val finishListener = CountDownLatch(1)
        val lateListenerCalls = AtomicInteger()
        val closeFailure = AtomicReference<Throwable>()
        val closeable = TestCloseable()
        closeable.addOnSharedCloseListener {
            enteredListener.countDown()
            assertTrue(finishListener.await(1, TimeUnit.SECONDS))
        }

        val closer = Thread {
            try {
                closeable.close()
            } catch (e: Throwable) {
                closeFailure.set(e)
            }
        }
        closer.start()

        assertTrue(enteredListener.await(1, TimeUnit.SECONDS))
        closeable.addOnSharedCloseListener { lateListenerCalls.incrementAndGet() }
        assertEquals(0, lateListenerCalls.get())

        finishListener.countDown()
        closer.join(1_000)

        assertFalse(closer.isAlive)
        assertEquals(null, closeFailure.get())
        assertEquals(1, lateListenerCalls.get())
        assertFalse(closeable.isOpen)
        assertEquals(1, closeable.closeCount)
    }

    @Test
    fun duplicateListenerRegistrationIsDeduplicated() {
        val closeable = TestCloseable()
        val listenerCalls = AtomicInteger()
        val listener: (Throwable?) -> Unit = { listenerCalls.incrementAndGet() }

        closeable.addOnSharedCloseListener(listener)
        closeable.addOnSharedCloseListener(listener)
        closeable.close()

        assertEquals(1, listenerCalls.get())
    }

    @Test
    fun removedListenerIsNotInvoked() {
        val closeable = TestCloseable()
        val listenerCalls = AtomicInteger()
        val listener: (Throwable?) -> Unit = { listenerCalls.incrementAndGet() }

        closeable.addOnSharedCloseListener(listener)
        closeable.removeOnSharedCloseListener(listener)
        closeable.close()

        assertEquals(0, listenerCalls.get())
        assertFalse(closeable.isOpen)
        assertEquals(1, closeable.closeCount)
    }

    @Test
    fun listenerAddedAfterCloseIsNotInvoked() {
        val closeable = TestCloseable()
        val listenerCalls = AtomicInteger()

        closeable.close()
        closeable.addOnSharedCloseListener { listenerCalls.incrementAndGet() }

        assertEquals(0, listenerCalls.get())
        assertFalse(closeable.isOpen)
        assertEquals(1, closeable.closeCount)
    }

    @Test
    fun listenerRemovedDuringResourceCloseIsNotInvoked() {
        lateinit var closeable: TestCloseable
        val listenerCalls = AtomicInteger()
        val listener: (Throwable?) -> Unit = { listenerCalls.incrementAndGet() }
        closeable = TestCloseable { closeable.removeOnSharedCloseListener(listener) }

        closeable.addOnSharedCloseListener(listener)
        closeable.close()

        assertEquals(0, listenerCalls.get())
        assertFalse(closeable.isOpen)
        assertEquals(1, closeable.closeCount)
    }

    @Test
    fun listenerRemovedDuringNotificationIsNotInvokedLater() {
        lateinit var closeable: TestCloseable
        val lateListenerCalls = AtomicInteger()
        val lateListener: (Throwable?) -> Unit = { lateListenerCalls.incrementAndGet() }
        closeable = TestCloseable()

        closeable.addOnSharedCloseListener {
            closeable.addOnSharedCloseListener(lateListener)
            closeable.removeOnSharedCloseListener(lateListener)
        }
        closeable.close()

        assertEquals(0, lateListenerCalls.get())
        assertFalse(closeable.isOpen)
        assertEquals(1, closeable.closeCount)
    }

    private class TestCloseable(
        private val onClose: () -> Unit = {},
    ) : SharedCloseable() {
        private val closes = AtomicInteger()

        val closeCount: Int get() = closes.get()

        override fun onSharedClose() {
            closes.incrementAndGet()
            onClose()
        }
    }
}
