package fluxo.io.internal

import fluxo.io.IOException
import fluxo.io.rad.RadContract
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [AsyncAccessorRad] holds every rule of the async interface for natively async sources, so it
 * is tested once here over an in-memory accessor; each source then only proves its raw read.
 *
 * Coroutines run without a dispatcher (stdlib `startCoroutine`): a read that suspends stays
 * suspended until the test resumes it, so the close-during-read case is deterministic and runs
 * on every target, JS included.
 */
private val BYTES = RadContract.bytes()

internal class AsyncAccessorRadTest {

    @Test
    fun readsSlicesAndBounds() {
        val rad = AsyncAccessorRad(MemoryAccessor(maxChunk = 3))
        assertEquals(BYTES.size.toLong(), rad.size)

        val buf = ByteArray(8)
        assertEquals(3, run { rad.read(buf, position = 10) }, "one accessor read, may be short")
        assertContentEquals(BYTES.copyOfRange(10, 13), buf.copyOfRange(0, 3))
        assertEquals(8, run { rad.readFully(buf, position = 20) }, "readFully loops")
        assertContentEquals(BYTES.copyOfRange(20, 28), buf)

        val slice = rad.slice(100, 16)
        assertEquals(16, slice.size)
        assertEquals(8, run { slice.readFully(buf, position = 8) })
        assertContentEquals(BYTES.copyOfRange(108, 116), buf, "slice positions are relative")
        assertEquals(-1, run { slice.read(buf, position = 16) }, "-1 at the end of a slice")
        assertEquals(0, run { slice.read(buf, position = 0, maxLength = 0) })

        assertFailsWith<IndexOutOfBoundsException> { rad.slice(250, 7) }
        assertFailsWith<IndexOutOfBoundsException> { run { rad.read(buf, position = -1) } }
        assertFailsWith<IndexOutOfBoundsException> { run { rad.read(buf, 0, offset = 9) } }
    }

    @Test
    fun closedHandleAndItsSlicesNeverRead() {
        val access = MemoryAccessor()
        val rad = AsyncAccessorRad(access)
        val slice = rad.slice(0, 8)
        val shared = rad.share()

        rad.close()
        rad.close() // a second close must not give back the ownership `shared` relies on
        val buf = ByteArray(8)
        assertFailsWith<IOException> { run { rad.read(buf) } }
        assertFailsWith<IOException> { run { slice.readFully(buf) } }
        assertFailsWith<IOException> { rad.share() }
        slice.close() // a no-op on a slice

        assertEquals(8, run { shared.readFully(buf) }, "data stays open for the other handle")
        assertFalse(access.resource.closed)
        shared.close()
        assertTrue(access.resource.closed, "last handle releases the source")
        assertFailsWith<IOException> { run { shared.read(buf) } }
    }

    @Test
    fun zeroProgressInsideDataFailsInsteadOfSpinning() {
        val rad = AsyncAccessorRad(MemoryAccessor(maxChunk = 0))
        assertFailsWith<IOException> { run { rad.readFully(ByteArray(4)) } }
    }

    @Test
    fun closeDuringPendingReadDefersReleaseUntilReadEnds() {
        val access = MemoryAccessor(gated = true)
        val rad = AsyncAccessorRad(access)
        val buf = ByteArray(4)
        var result: Result<Int>? = null
        suspend { rad.read(buf, position = 1) }
            .startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
        val pending = checkNotNull(access.gate) { "read must be suspended inside the source" }

        rad.close()
        assertFalse(access.resource.closed, "release must wait for the in-flight read")
        assertNull(result)

        pending.resume(Unit)
        assertEquals(4, result?.getOrThrow())
        assertContentEquals(BYTES.copyOfRange(1, 5), buf)
        assertTrue(access.resource.closed, "released once the read ended")
    }

    /** Runs a suspend call that must complete without a dispatcher. */
    private fun <T> run(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(Continuation(EmptyCoroutineContext) { result = it })
        return checkNotNull(result) { "call suspended unexpectedly" }.getOrThrow()
    }

    private class TrackedResource : AutoCloseable {
        var closed = false
        override fun close() {
            check(!closed) { "released twice" }
            closed = true
        }
    }

    /**
     * Serves [BYTES], at most [maxChunk] bytes per read. When [gated], the first read
     * suspends inside its lease until the test resumes [gate].
     */
    private class MemoryAccessor(
        private val maxChunk: Int = Int.MAX_VALUE,
        private val gated: Boolean = false,
        val resource: TrackedResource = TrackedResource(),
    ) : SharedAsyncDataAccessor(arrayOf(resource)) {

        var gate: Continuation<Unit>? = null
        private var zeroReads = 0

        override val size: Long get() = BYTES.size.toLong()

        override suspend fun read(bytes: ByteArray, position: Long, offset: Int, length: Int) =
            withLease {
                if (gated && gate == null) {
                    suspendCoroutine { gate = it }
                }
                check(!resource.closed) { "read after release" }
                // A zero read must end readFully, so a second one is a spin: fail instead of hanging.
                check(maxChunk > 0 || ++zeroReads == 1) { "zero-progress read retried" }
                val n = minOf(length, maxChunk)
                BYTES.copyInto(bytes, offset, position.toInt(), position.toInt() + n)
                n
            }
    }
}
