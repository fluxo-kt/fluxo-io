package fluxo.io.rad

import fluxo.io.IOException
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking

/**
 * The [asAsync] adapter's own promises; its read results are covered on every implementation
 * by [AbstractRandomAccessDataTest].
 */
internal class RadAsAsyncTest {

    private companion object {
        private val DATA = ByteArray(16) { it.toByte() }
        private const val IO_THREAD = "rad-blocking-io"
    }

    /** RED if a context without a dispatcher is accepted: reads would block the caller. */
    @Test
    fun contextWithoutDispatcherIsRejected() {
        val e = assertFailsWith<IllegalArgumentException> {
            RadByteArrayAccessor(DATA).asAsync(EmptyCoroutineContext)
        }
        assertContains(e.message.orEmpty(), "Dispatchers.IO")
    }

    /**
     * RED if the blocking read runs on the caller's thread instead of the given dispatcher, or if
     * the caller resumes on the IO thread (its own code would then run there).
     */
    @Test
    fun blockingReadRunsOnTheGivenDispatcherAndTheCallerResumesOnItsOwn() {
        val readThread = AtomicReference<String>()
        val rad = StreamFactoryRadAccessor(DATA.size.toLong()) {
            object : InputStream() {
                private val src = ByteArrayInputStream(DATA)
                override fun read(): Int = src.read()
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    readThread.set(Thread.currentThread().name)
                    return src.read(b, off, len)
                }
            }
        }
        val executor = Executors.newSingleThreadExecutor { Thread(it, IO_THREAD) }
        try {
            val async = rad.asAsync(executor.asCoroutineDispatcher())
            val out = ByteArray(4)
            runBlocking {
                val caller = Thread.currentThread()
                assertEquals(4, async.readFully(out, position = 3))
                assertEquals(caller, Thread.currentThread())
            }
            assertEquals(DATA.copyOfRange(3, 7).toList(), out.toList())
            assertEquals(IO_THREAD, readThread.get())
            async.close()
        } finally {
            executor.shutdown()
        }
    }

    /**
     * The adapter takes over the handle it wraps (RED if closing it leaks the source), and a
     * `share()` keeps the original usable.
     */
    @Test
    fun adapterOwnsTheHandleItWraps() {
        val owned = RadByteArrayAccessor(DATA)
        owned.asAsync(Dispatchers.IO).close()
        assertFailsWith<IOException> { owned.readByteAt(0) }

        val kept = RadByteArrayAccessor(DATA)
        kept.share().asAsync(Dispatchers.IO).close()
        assertEquals(1, kept.readByteAt(1))
        kept.close()
    }

    /** Slices read relative to their start; a share outlives the adapter it came from. */
    @Test
    fun adapterSlicesAndSharesFollowTheWrappedHandle() = runBlocking {
        val async = RadByteArrayAccessor(DATA).asAsync(Dispatchers.IO)
        val out = ByteArray(3)
        assertEquals(3, async.slice(5, 8).readFully(out, position = 2))
        assertEquals(listOf<Byte>(7, 8, 9), out.toList())
        val shared = async.share()
        async.close()
        assertEquals(2, shared.read(out, position = 14))
        assertFailsWith<IOException> { async.read(out) }
        shared.close()
    }
}
