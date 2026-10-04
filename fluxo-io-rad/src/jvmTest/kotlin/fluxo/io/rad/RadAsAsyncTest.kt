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
    fun contextWithoutDispatcherIsRejectedWithTheFix() {
        val e = assertFailsWith<IllegalArgumentException> {
            RadByteArrayAccessor(DATA).asAsync(EmptyCoroutineContext)
        }
        assertContains(e.message.orEmpty(), "Dispatchers.IO")
    }

    /** RED if the blocking read runs on the caller's thread instead of the given dispatcher. */
    @Test
    fun blockingReadRunsOnTheGivenDispatcher() {
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
            assertEquals(4, runBlocking { async.readFully(out, position = 3) })
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
}
