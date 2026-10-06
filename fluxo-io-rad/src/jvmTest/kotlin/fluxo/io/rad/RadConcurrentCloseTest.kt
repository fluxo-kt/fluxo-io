package fluxo.io.rad

import fluxo.io.IOException
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Deterministic reproducers for the *concurrent* close-during-read hazards (the sequential case
 * is covered by [AbstractRandomAccessDataTest.readingClosedHolderThrowsNotCrashes]). Each drives a
 * real read past its lease, freeze it mid-flight on a latch, run a full [close] from another
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
     * the pool is drained exactly once, so a put after the drain would never be closed. The read's
     * lease defers the drain until the read ends, so the re-pooled stream is drained too.
     * Without that, `opened > closed` (RED).
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
     * Concurrent `close` while a read is mid-`factory()` must (a) NOT wait for it: a slow or
     * hostile user factory cannot DoS close; (b) let that read, which started before the close,
     * complete normally; (c) still close the stream it opened once it ends, never leaking it
     * past the pool drain. RED if close waits for the factory (`closeDone` never flips), or if
     * the opened stream outlives the release (`opened != closed`).
     */
    @Test
    fun concurrentCloseDoesNotBlockOnFactoryAndOrphanStreamIsClosed() {
        val opened = AtomicInteger()
        val closed = AtomicInteger()
        val factoryEntered = CountDownLatch(1)
        val proceedFactory = CountDownLatch(1)
        val readerError = AtomicReference<Throwable?>()

        val rad = StreamFactoryRadAccessor(DATA.size.toLong()) {
            factoryEntered.countDown()
            proceedFactory.await()
            opened.incrementAndGet()
            object : InputStream() {
                private val src = ByteArrayInputStream(DATA)
                override fun read(): Int = src.read()
                override fun read(b: ByteArray, off: Int, len: Int): Int = src.read(b, off, len)
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
        // Closer started only after the reader is parked INSIDE factory: a closer that runs
        // first makes the read fail before the factory is entered, which would hide the
        // invariant under a misleading "factory never entered" failure.
        assertTrue(factoryEntered.await(TIMEOUT_S, TimeUnit.SECONDS), "factory never entered")
        val closeDone = AtomicBoolean(false)
        val closer = thread {
            rad.close()
            closeDone.set(true)
        }
        try {
            // Close only drops the last owner; the drain runs when the in-flight read ends, so
            // the closer finishes while the factory is still parked. A close that waited for the
            // factory never finishes before `proceedFactory`: the bound only stops that hang, it
            // is not a speed budget, so a slow machine cannot fail a correct close.
            closer.join(TIMEOUT_S * 1000)
            assertTrue(closeDone.get(), "close parked on factory (DoS-on-close band-aid)")
        } finally {
            proceedFactory.countDown()
        }
        reader.join(TIMEOUT_S * 1000)
        closer.join(TIMEOUT_S * 1000)

        assertFalse(reader.isAlive, "reader did not finish")
        assertNull(readerError.get(), "a read that started before close must complete")
        assertEquals(1, opened.get(), "factory ran exactly once")
        assertEquals(1, closed.get(), "orphan stream was leaked after concurrent close")
        assertFailsWith<IOException> { rad.readFrom(0, PARTIAL) }
    }

    /**
     * The mmap/direct unmap must not run while a read is in flight, else it frees the buffer
     * under an in-flight `get` (native use-after-free). Proven on a heap buffer (no crash risk)
     * whose release is observed through a resource closed with it: `close` returns at once,
     * but the release waits for the in-flight `transferTo` to end. Without the read's lease
     * the resource closes mid-read (RED), caught before it can ever crash on a real mmap.
     */
    @Test
    fun unmapWaitsForInFlightRead() {
        val writeEntered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val transferError = AtomicReference<Throwable?>()
        val released = AtomicInteger()
        val resource = AutoCloseable { released.incrementAndGet() }
        val rad = RadByteBufferAccessor(ByteBuffer.wrap(DATA), resources = arrayOf(resource))

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
        // Close only after the transfer is mid-write: a close that runs first makes the
        // transfer fail on entry instead of demonstrating the in-flight case.
        try {
            assertTrue(writeEntered.await(TIMEOUT_S, TimeUnit.SECONDS), "transfer never entered")
            rad.close() // returns at once: close never waits for readers
            assertEquals(0, released.get(), "resource released mid-read: release not deferred")
        } finally {
            // Always release the transfer so a failed assertion doesn't strand its thread.
            proceed.countDown()
        }
        transfer.join(TIMEOUT_S * 1000)

        assertFalse(transfer.isAlive, "transfer did not finish")
        assertNull(transferError.get())
        assertEquals(1, released.get(), "release must run exactly once, when the read ends")
        assertFailsWith<IOException> { rad.readByteAt(0) }
    }
}
