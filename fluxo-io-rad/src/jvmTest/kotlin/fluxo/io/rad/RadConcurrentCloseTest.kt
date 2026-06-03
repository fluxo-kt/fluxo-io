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

        assertTrue(readEntered.await(TIMEOUT_S, TimeUnit.SECONDS), "read never entered")
        rad.close() // drains the empty pool (stream is checked out); resource now freed
        proceed.countDown() // in-flight read resumes and attempts to re-pool its stream
        reader.join(TIMEOUT_S * 1000)

        assertFalse(reader.isAlive, "reader did not finish")
        assertNull(readerError.get())
        assertTrue(opened.get() >= 1, "no stream was opened")
        assertEquals(opened.get(), closed.get(), "a re-pooled stream leaked after concurrent close")
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
        assertTrue(writeEntered.await(TIMEOUT_S, TimeUnit.SECONDS), "transfer never entered")

        val closeDone = AtomicBoolean(false)
        val closer = thread {
            rad.close()
            closeDone.set(true)
        }

        // The closer must park on the resource monitor until the in-flight transfer releases it.
        val deadline = System.nanoTime() + TIMEOUT_S * 1_000_000_000L
        while (closer.state != Thread.State.BLOCKED) {
            assertFalse(closeDone.get(), "close finished mid-read — unmap not serialised")
            if (System.nanoTime() > deadline) fail("closer never blocked on the resource monitor")
            Thread.onSpinWait()
        }

        proceed.countDown()
        transfer.join(TIMEOUT_S * 1000)
        closer.join(TIMEOUT_S * 1000)
        assertNull(transferError.get())
        assertTrue(closeDone.get(), "close did not complete after the read released the monitor")
    }
}
