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
    fun loggerUpdateIsVisibleAcrossThreads() {
        val loggerMessage = AtomicReference<String>()
        val ready = CountDownLatch(1)
        val done = CountDownLatch(1)

        val worker = Thread {
            ready.countDown()
            while (LOGGER == null) {
                Thread.yield()
            }
            LOGGER?.invoke("visible", null)
            done.countDown()
        }
        worker.start()
        assertTrue(ready.await(1, TimeUnit.SECONDS))

        setFluxoIoLogger { message, _ -> loggerMessage.set(message) }

        assertTrue(done.await(1, TimeUnit.SECONDS))
        worker.join(1_000)
        assertEquals("visible", loggerMessage.get())

        setFluxoIoLogger { _, _ -> }
    }

    private class TestCloseable : SharedCloseable() {
        private val closes = AtomicInteger()

        val closeCount: Int get() = closes.get()

        override fun onSharedClose() {
            closes.incrementAndGet()
        }
    }
}
