package fluxo.io.rad

import fluxo.io.nio.positionCompat
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Deterministic reproducers for the *concurrent* close-during-read hazards (the sequential case
 * is covered by [AbstractRandomAccessDataTest.readingClosedHolderThrowsNotCrashes]). Both drive a
 * real read past its open-check, freeze it mid-flight on a latch, run a full [close] from another
 * thread, then resume — exercising the exact window the guards protect. No mocks: a real
 * [InputStream]/[WritableByteChannel] whose blocking point is latch-coordinated.
 */
internal class RadConcurrentCloseTest {

    private companion object {
        private val DATA = ByteArray(64) { it.toByte() }
        // < DATA.size, so the read leaves data and the stream is re-pooled (not closed at EOF)
        private const val PARTIAL = 32
        private const val TIMEOUT_S = 2L
    }

    /**
     * A stream re-pooled by a read that was in flight while the resource closed must NOT leak:
     * `onSharedClose` drains the pool exactly once, so a put after the drain is never closed.
     * The fix rechecks `isOpen` under the pool lock and lets the `finally` close the stream.
     * Without it, `opened > closed` (RED).
     */
    @Test
    fun streamRePooledDuringConcurrentCloseIsNotLeaked() {
        val opened = AtomicInteger()
        val closed = AtomicInteger()
        val readEntered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val readerError = AtomicReference<Throwable?>()

        val rad = StreamFactoryRadAccessor(DATA.size.toLong()) {
            opened.incrementAndGet()
            object : InputStream() {
                private val src = ByteArrayInputStream(DATA)
                override fun read(): Int = src.read()
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    readEntered.countDown()
                    proceed.await()
                    return src.read(b, off, len)
                }
                override fun close() {
                    closed.incrementAndGet()
                    src.close()
                }
            }
        }

        val reader = thread {
            try {
                rad.readFrom(0, PARTIAL)
            } catch (e: Throwable) {
                readerError.set(e)
            }
        }

        try {
            assertTrue(readEntered.await(TIMEOUT_S, TimeUnit.SECONDS), "read never entered")
            rad.close() // drains the empty pool (stream is checked out); resource now freed
        } finally {
            // Always release the reader so a failed pre-close assertion doesn't strand the
            // thread blocked on `proceed.await()` past the test method return.
            proceed.countDown()
        }
        reader.join(TIMEOUT_S * 1000)

        assertFalse(reader.isAlive, "reader did not finish")
        assertNull(readerError.get())
        assertTrue(opened.get() >= 1, "no stream was opened")
        assertEquals(opened.get(), closed.get(), "a re-pooled stream leaked after concurrent close")
    }

    /**
     * A read that opens a fresh stream (pool miss) MUST hold the pool monitor through the
     * factory call, else a concurrent close can drain the (already-empty) pool and return
     * *before* the factory opens its stream — a ghost read against an officially-closed
     * holder. Without the under-lock guard the closer is uncontested (reader pauses inside
     * factory holding no lock), reaches `RUNNABLE→TERMINATED` instead of `BLOCKED`, and
     * `closeDone` flips while the loop still spins — RED. The fix keeps `factory` inside
     * `synchronized(pool)` so the closer's drain parks behind it.
     */
    @Test
    fun closeBlocksWhileReadOpensFreshStream() {
        val factoryEntered = CountDownLatch(1)
        val proceedFactory = CountDownLatch(1)
        val readerError = AtomicReference<Throwable?>()

        val rad = StreamFactoryRadAccessor(DATA.size.toLong()) {
            factoryEntered.countDown()
            proceedFactory.await()
            object : InputStream() {
                private val src = ByteArrayInputStream(DATA)
                override fun read(): Int = src.read()
                override fun read(b: ByteArray, off: Int, len: Int): Int = src.read(b, off, len)
                override fun close() = src.close()
            }
        }

        val reader = thread {
            try {
                rad.readFrom(0, PARTIAL)
            } catch (e: Throwable) {
                readerError.set(e)
            }
        }
        val closeDone = AtomicBoolean(false)
        var closer: Thread? = null
        try {
            assertTrue(factoryEntered.await(TIMEOUT_S, TimeUnit.SECONDS), "factory never entered")
            closer = thread {
                rad.close()
                closeDone.set(true)
            }
            // The closer must park on the pool monitor while the reader is inside `factory`
            // (i.e. holding `synchronized(pool)`). If it reaches TERMINATED before BLOCKED,
            // the under-lock guard is missing — `closeDone` flips first and reds the loop.
            val deadline = System.nanoTime() + TIMEOUT_S * 1_000_000_000L
            while (closer.state != Thread.State.BLOCKED) {
                assertFalse(closeDone.get(), "close completed mid-factory — ghost-read race")
                if (System.nanoTime() > deadline) fail("closer never blocked on the pool monitor")
                Thread.onSpinWait()
            }
        } finally {
            proceedFactory.countDown()
        }
        reader.join(TIMEOUT_S * 1000)
        closer?.join(TIMEOUT_S * 1000)
        assertNull(readerError.get())
        assertTrue(closeDone.get(), "close never completed after factory released the pool")
    }

    /**
     * The mmap/direct unmap in `onSharedClose` must not run while a read holds the resource
     * monitor, else it frees the buffer under an in-flight `get`/`put` (native use-after-free).
     * Proven on a heap buffer (no crash risk): the unmap is `synchronized(api)`, so a concurrent
     * `close` blocks on the monitor the in-flight `transferTo` holds. Without that synchronisation
     * `close` completes mid-read (RED) — caught here before it can ever crash on a real mmap.
     */
    @Test
    fun unmapWaitsForInFlightRead() {
        val rad = RadByteBufferAccessor(DATA)
        val writeEntered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val transferError = AtomicReference<Throwable?>()

        val channel = object : WritableByteChannel {
            override fun write(src: ByteBuffer): Int {
                writeEntered.countDown()
                proceed.await()
                val n = src.remaining()
                src.positionCompat(src.limit())
                return n
            }
            override fun isOpen() = true
            override fun close() = Unit
        }

        val transfer = thread {
            try {
                rad.transferTo(channel)
            } catch (e: Throwable) {
                transferError.set(e)
            }
        }
        val closeDone = AtomicBoolean(false)
        var closer: Thread? = null
        try {
            // The closer is started only after the transfer is in the resource monitor —
            // otherwise close could win the monitor, unmap, and the transfer's checkOpen
            // would throw instead of demonstrating the in-flight-blocks-close invariant.
            assertTrue(writeEntered.await(TIMEOUT_S, TimeUnit.SECONDS), "transfer never entered")
            closer = thread {
                rad.close()
                closeDone.set(true)
            }

            // The closer must park on the resource monitor until the in-flight transfer releases.
            val deadline = System.nanoTime() + TIMEOUT_S * 1_000_000_000L
            while (closer.state != Thread.State.BLOCKED) {
                assertFalse(closeDone.get(), "close finished mid-read — unmap not serialised")
                if (System.nanoTime() > deadline) fail("closer never blocked on resource monitor")
                Thread.onSpinWait()
            }
        } finally {
            // Always release the transfer so a failed assertion doesn't strand it blocked
            // on `proceed.await()`; the closer (if started) is no longer parked behind it either.
            proceed.countDown()
        }
        transfer.join(TIMEOUT_S * 1000)
        closer?.join(TIMEOUT_S * 1000)
        assertNull(transferError.get())
        assertTrue(closeDone.get(), "close did not complete after the read released the monitor")
    }
}
