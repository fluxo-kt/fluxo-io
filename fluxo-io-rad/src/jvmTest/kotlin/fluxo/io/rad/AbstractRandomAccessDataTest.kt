@file:Suppress("KotlinConstantConditions", "KDocUnresolvedReference", "LargeClass", "LongMethod")

package fluxo.io.rad

import fluxo.io.IOException
import fluxo.io.nio.flipCompat
import fluxo.io.nio.releaseCompat
import fluxo.io.readBytesExact
import fluxo.io.readBytesFully
import fluxo.io.toArray
import fluxo.io.util.EMPTY_BYTE_ARRAY
import fluxo.io.util.toIntChecked
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ClosedChannelException
import java.util.concurrent.Executors
import java.util.concurrent.ThreadLocalRandom
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test

// FIXME: Test behaviour for the underlying file change
// TODO: Fuzzy / random actions test

/**
 * Tests for [RadByteArrayAccessor]
 *
 * @see org.springframework.boot.loader.data.RandomAccessDataFile
 */
internal abstract class AbstractRandomAccessDataTest(
    @JvmField
    protected val factory: (File) -> RandomAccessData,
) {

    companion object {
        private val DEFAULT_TIMEOUT = 9.seconds

        val BYTES = ByteArray(256)

        init {
            for (i in BYTES.indices) {
                BYTES[i] = i.toByte()
            }
        }


        const val LONG_GIVES_INT_MINUS_2: Long = Int.MAX_VALUE.toLong() + Int.MAX_VALUE
        const val LONG_GIVES_INT_0: Long = LONG_GIVES_INT_MINUS_2 + 2
        const val LONG_GIVES_INT_2: Long = LONG_GIVES_INT_MINUS_2 + 4
        const val LONG_NEG_GIVES_INT_2: Long = -LONG_GIVES_INT_MINUS_2
    }

    protected lateinit var tempFile: File
    protected lateinit var rad: RandomAccessData
    protected lateinit var inputStream: InputStream


    @BeforeTest
    fun setup() {
        tempFile = File.createTempFile("tempFile", "tmp")
        tempFile.writeBytes(BYTES)
        rad = factory(tempFile)
        inputStream = rad.asInputStream()
    }

    @AfterTest
    fun cleanup() {
        try {
            inputStream.close()
            rad.close()
        } finally {
            tempFile.delete()
        }
    }


    /** The cross-platform contract, run against this implementation over a real temp file. */
    @Test
    fun contract() {
        val files = ArrayList<File>()
        try {
            RadContract.verify { bytes ->
                val file = File.createTempFile("contract", "tmp").also(files::add)
                file.writeBytes(bytes)
                factory(file)
            }
        } finally {
            files.forEach(File::delete)
        }
    }

    @Test
    fun inputStreamRead() = runTest(timeout = DEFAULT_TIMEOUT) {
        assertEquals(BYTES.size, inputStream.available())
        for (i in BYTES.indices) {
            assertEquals(i, inputStream.read())
        }
        assertEquals(-1, inputStream.read())
    }

    @Test
    fun inputStreamReadNullBytes() = runTest(timeout = DEFAULT_TIMEOUT) {
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        assertFailsWith<NullPointerException> { inputStream.read(null) }
    }

    @Test
    fun inputStreamReadNullBytesWithOffset() = runTest(timeout = DEFAULT_TIMEOUT) {
        @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
        assertFailsWith<NullPointerException> { inputStream.read(null, 0, 1) }
    }

    @Test
    fun inputStreamReadBytes() = runTest(timeout = DEFAULT_TIMEOUT) {
        val b = ByteArray(256)
        val amountRead = inputStream.read(b)
        assertEquals(BYTES, b)
        assertEquals(256, amountRead)
    }

    @Test
    fun inputStreamReadOffsetBytes() = runTest(timeout = DEFAULT_TIMEOUT) {
        val b = ByteArray(7)
        inputStream.skip(1)
        val amountRead = inputStream.read(b, 2, 3)
        assertEquals(byteArrayOf(0, 0, 1, 2, 3, 0, 0), b)
        assertEquals(3, amountRead)
    }

    @Test
    fun inputStreamReadMoreBytesThanAvailable() = runTest(timeout = DEFAULT_TIMEOUT) {
        val b = ByteArray(257)
        val amountRead = inputStream.read(b)
        assertEquals(BYTES, b.copyOf(BYTES.size))
        assertEquals(256, amountRead)
    }

    @Test
    fun inputStreamReadPastEnd() = runTest(timeout = DEFAULT_TIMEOUT) {
        assertEquals(255L, inputStream.skip(255))
        assertEquals(0xFF, inputStream.read())
        assertEquals(-1, inputStream.read())
        assertEquals(-1, inputStream.read())
    }

    @Test
    fun inputStreamReadZeroLength() = runTest(timeout = DEFAULT_TIMEOUT) {
        val b = byteArrayOf(0x0F)
        val amountRead = inputStream.read(b, 0, 0)
        assertEquals(byteArrayOf(0x0F), b)
        assertEquals(0, amountRead)
        assertEquals(0, inputStream.read())
    }

    @Test
    fun inputStreamSkip() = runTest(timeout = DEFAULT_TIMEOUT) {
        assertEquals(4L, inputStream.skip(4))
        assertEquals(4, inputStream.read())
    }

    @Test
    fun inputStreamSkipMoreThanAvailable() = runTest(timeout = DEFAULT_TIMEOUT) {
        val amountSkipped = inputStream.skip(257)
        assertEquals(-1, inputStream.read())
        assertEquals(256L, amountSkipped)
    }

    @Test
    fun inputStreamSkipPastEnd() = runTest(timeout = DEFAULT_TIMEOUT) {
        inputStream.skip(256)
        val amountSkipped = inputStream.skip(1)
        assertEquals(0L, amountSkipped)
    }

    @Test
    fun sliceZeroLength() = runTest(timeout = DEFAULT_TIMEOUT) {
        val slice = rad.slice(0, 0)
        assertEquals(-1, slice.asInputStream().read())
    }


    /**
     * After its handle closes, every JVM-only read path of the handle and of its slices fails
     * with [IOException] (the common paths are in [RadContract]). Each path is listed because
     * an implementation may route it through its own fast-path override.
     *
     * Sequentially this rejects at the handle, before any resource is touched. Memory safety
     * when a close races an in-flight read rests on the resource lease, which
     * [RadConcurrentCloseTest] exercises.
     */
    @Test
    fun readingClosedHolderThrowsNotCrashes() = runTest(timeout = DEFAULT_TIMEOUT) {
        val view = rad.slice(1)
        rad.close()

        val sink = Channels.newChannel(ByteArrayOutputStream())
        for (closed in arrayOf(rad, view)) {
            assertFailsWith<IOException> { closed.readByteAt(0) }
            assertFailsWith<IOException> { closed.read(ByteBuffer.allocate(1), 0) }
            assertFailsWith<IOException> { closed.transferTo(sink) }
            assertFailsWith<IOException> { closed.transferTo(ByteArrayOutputStream()) }
            assertFailsWith<IOException> { closed.asInputStream() }
        }
    }

    @Test
    fun inputStreamReadPastSlice() = runTest(timeout = DEFAULT_TIMEOUT) {
        val slice = rad.slice(1, 2)
        val inputStream = slice.asInputStream()
        assertEquals(2, inputStream.available())
        assertEquals(1, inputStream.read())
        assertEquals(2, inputStream.read())
        assertEquals(-1, inputStream.read())
    }

    @Test
    fun inputStreamReadBytesPastSlice() = runTest(timeout = DEFAULT_TIMEOUT) {
        val slice = rad.slice(1, 2)
        val inputStream = slice.asInputStream()
        assertEquals(2, inputStream.available())
        val b = ByteArray(3)
        assertEquals(2, inputStream.read(b))
        assertEquals(byteArrayOf(1, 2, 0), b)
    }

    @Test
    fun inputStreamSkipPastSlice() = runTest(timeout = DEFAULT_TIMEOUT) {
        val slice = rad.slice(1, 2)
        val inputStream = slice.asInputStream()
        assertEquals(2, inputStream.available())
        assertEquals(2L, inputStream.skip(3))
        assertEquals(-1, inputStream.read())
    }

    @Test
    fun inputStreamSkipNegative() {
        assertEquals(0L, inputStream.skip(-1))
    }

    @Test
    fun testConcurrency() = runTest(timeout = DEFAULT_TIMEOUT) {
        val threadPool = Executors.newFixedThreadPool(30)
        (0 until 180).map { taskIndex ->
            threadPool.submit<Unit> {
                val d = "task #$taskIndex"
                val len = BYTES.size
                val slice = rad.slice(0, len.toLong())

                val stream = slice.asInputStream()
                assertEquals(len, stream.available())
                val b = ByteArray(len)
                assertEquals(len, stream.read(b), d)
                assertEquals(BYTES, b, d)

                arrayOf(rad, slice).forEachIndexed { ri, rad ->
                    assertRead("$d rad #$ri", rad) { array, position ->
                        try {
                            randRead(array, position)
                        } catch (e: Throwable) {
                            when (e) {
                                is IndexOutOfBoundsException,
                                is IOException,
                                    -> {
                                    throw e
                                }

                                else -> {
                                    throw IllegalStateException("$d rad #$ri", e)
                                }
                            }
                        }
                    }
                }
            }
        }.forEach {
            it.get()
        }
    }

    @Test
    fun testInputStream() = runTest(timeout = DEFAULT_TIMEOUT) {
        val streams = arrayOf(
            inputStream,
            rad.asInputStream(),
            rad.asInputStream().buffered(),
            rad.slice(0, rad.size).asInputStream(),
            rad.slice(0, rad.size)
                .slice(0, rad.size).asInputStream(),
        )
        for (stream in streams) {
            assertEquals(true, stream.markSupported())
            assertEquals(BYTES.size, stream.available())
            assertEquals(0, stream.read())
            assertEquals(1, stream.read())

            stream.mark(0)
            assertEquals(2, stream.read())
            assertEquals(0, stream.skip(0))
            assertEquals(3, stream.read())
            assertEquals(1, stream.skip(1))
            assertEquals(5, stream.read())
            assertEquals(2, stream.skip(2))
            assertEquals(8, stream.read())

            stream.reset()
            assertEquals(2, stream.read())
            assertEquals(1, stream.skip(1))
            assertEquals(4, stream.read())
            assertEquals(5, stream.read())
            assertEquals(2, stream.skip(2))
            assertEquals(8, stream.read())

            assertEquals(BYTES.size - 9, stream.available())
            assertEquals(BYTES.size - 9L, stream.skip(BYTES.size - 9L))
            assertEquals(-1, stream.read())
        }
    }

    @Test
    fun testAllBytes() = runTest(timeout = DEFAULT_TIMEOUT) {
        assertEquals(BYTES, inputStream.readBytes())

        for (rad in arrayOf(rad, rad.slice(0, rad.size))) {
            assertEquals(BYTES.size.toLong(), rad.size)
            assertEquals(BYTES.copyOf(BYTES.size), rad.readAllBytes())
            assertEquals(BYTES.copyOfRange(0, BYTES.size), rad.readAllBytes())
            assertEquals(BYTES, rad.readFrom(0L, BYTES.size))
            assertEquals(
                BYTES,
                rad.asInputStream().let {
                    assertEquals(BYTES.size, it.available())
                    it.readBytes()
                },
            )
            assertEquals(BYTES, rad.asInputStream().readBytes())
            assertEquals(BYTES, rad.asInputStream().buffered().readBytes())
            assertEquals(BYTES, rad.asInputStream().readBytesExact(rad.size.toInt()))
            assertEquals(BYTES, rad.asInputStream().readBytesFully(rad.size.toInt()))

            // JDK 9+
            @Suppress("Since15")
            try {
                assertEquals(BYTES, rad.asInputStream().readAllBytes())
                assertEquals(BYTES, rad.asInputStream().readNBytes(rad.size.toInt()))
                assertEquals(
                    BYTES,
                    rad.asInputStream().let {
                        assertEquals(BYTES.size, it.available())
                        val out = ByteArrayOutputStream(BYTES.size)
                        it.transferTo(out)
                        out.toByteArray()
                    },
                )
            } catch (_: NoSuchMethodError) {
                // ignore this error from older JDK versions
            }

            val part = rad.slice(34, 145)
            val expected = BYTES.copyOfRange(34, 179)
            assertEquals(145L, part.size)
            assertEquals(expected, part.readAllBytes())
            assertEquals(expected, part.readFrom(0L, 145))
            assertEquals(
                expected,
                part.asInputStream().let {
                    assertEquals(145, it.available())
                    it.readBytes()
                },
            )
            assertEquals(expected, part.asInputStream().readBytes())
            assertEquals(expected, part.asInputStream().buffered().readBytes())
            assertEquals(expected, part.asInputStream().readBytesExact(145, strict = true))
            assertEquals(expected, part.asInputStream().readBytesFully(145))
        }
    }

    @Test
    fun testReadByte() = runTest(timeout = DEFAULT_TIMEOUT) {
        val copy = rad.slice(0, rad.size)
        arrayOf(rad, copy, rad.slice(0, 8)).forEachIndexed { ri, rad ->
            val d = "rad #$ri"
            val size = rad.size
            for (i in 0 until size.toIntChecked()) {
                assertEquals(BYTES[i].toInt() and 0xFF, rad.readByteAt(i.toLong()), "$d, index #$i")
            }

            assertEquals(-1, rad.readByteAt(size), d)
            assertEquals(-1, rad.readByteAt(size + 1), d)
            assertEquals(-1, rad.readByteAt(size + 100), d)
            assertEquals(-1, rad.readByteAt(Int.MAX_VALUE.toLong()), d)
            assertEquals(-1, rad.readByteAt(Long.MAX_VALUE), d)
            assertEquals(-1, rad.readByteAt(LONG_GIVES_INT_MINUS_2), d)
            assertEquals(-1, rad.readByteAt(LONG_GIVES_INT_0), d)
            assertEquals(-1, rad.readByteAt(LONG_GIVES_INT_2), d)

            assertIOB(d) { rad.readByteAt(-1) }
            assertIOB(d) { rad.readByteAt(Int.MIN_VALUE.toLong()) }
            assertIOB(d) { rad.readByteAt(Long.MIN_VALUE) }
            assertIOB(d) { rad.readByteAt(LONG_NEG_GIVES_INT_2) }
        }
    }

    @Test
    fun testReadBuffer() = runTest(timeout = DEFAULT_TIMEOUT) {
        arrayOf(
            rad,
            rad.slice(0, rad.size),
            rad.slice(0, rad.size - 11),
        ).forEachIndexed { ri, rad ->
            assertRead("rad #$ri", rad) { array, position ->
                read(ByteBuffer.wrap(array), position)
            }
        }

        // The same bytes 2..4 from position 2, and through a slice whose section starts at 1.
        for ((source, position) in listOf(rad to 2L, rad.slice(1, 9) to 1L)) for (buffer in arrayOf(
            ByteBuffer.wrap(ByteArray(3)),
            // A heap slice: its view starts at arrayOffset 5 of the backing array.
            ByteBuffer.wrap(ByteArray(8), 5, 3).slice(),
            ByteBuffer.allocateDirect(3),
        )) {
            try {
                val read = source.read(buffer, position)
                assertEquals(3, read)
                assertEquals(3, buffer.capacity())
                assertEquals(3, buffer.position())
                assertEquals(3, buffer.limit())
                assertEquals(EMPTY_BYTE_ARRAY, buffer.toArray())
                buffer.flipCompat()
                assertEquals(byteArrayOf(2, 3, 4), buffer.toArray(), "$position $buffer")
            } finally {
                buffer.releaseCompat()
            }
        }
    }

    /**
     * Every transfer path, on a slice that starts past 0 (impls add their section offset) and
     * spans several copy buffers (the minimum buffer is 1 KiB), with the returned count.
     */
    @Test
    fun testTransferTo() {
        val data = ByteArray(5000) { (it * 31 + 7).toByte() }
        val file = File.createTempFile("transferTo", "tmp")
        try {
            file.writeBytes(data)
            factory(file).use { whole ->
                val expected = data.copyOfRange(3, data.size - 2)
                val slice = whole.slice(3, expected.size.toLong())
                val transfers = mapOf<String, RandomAccessData.(ByteArrayOutputStream) -> Long>(
                    "stream" to { transferTo(it, 1024) },
                    "channel" to { transferTo(Channels.newChannel(it)) },
                    "channel, heap buffer" to { transferTo(Channels.newChannel(it), 1024, false) },
                    "channel, direct buffer" to { transferTo(Channels.newChannel(it), 1024, true) },
                )
                for ((name, transfer) in transfers) {
                    for ((source, bytes) in listOf(slice to expected, whole.slice(0, 0) to EMPTY_BYTE_ARRAY)) {
                        val out = ByteArrayOutputStream()
                        assertEquals(bytes.size.toLong(), source.transfer(out), name)
                        assertEquals(bytes, out.toByteArray(), name)
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    /** A failing target is the caller's problem: its own exception, not a source-closed one. */
    @Test
    fun transferToClosedTargetThrowsTheTargetsError() {
        val target = Channels.newChannel(ByteArrayOutputStream()).apply { close() }
        assertFailsWith<ClosedChannelException> { rad.transferTo(target) }
        assertEquals(BYTES[1].toInt(), rad.readByteAt(1))
    }

    /** Slicing does no I/O: an interrupted thread may slice without closing interruptible data. */
    @Test
    fun sliceOnInterruptedThreadKeepsDataReadable() {
        Thread.currentThread().interrupt()
        try {
            rad.slice(1, 2).share().close()
        } finally {
            Thread.interrupted()
        }
        assertEquals(BYTES[1].toInt(), rad.readByteAt(1))
    }


    private fun RandomAccessData.randRead(array: ByteArray, position: Long): Int {
        // current() per call: a ThreadLocalRandom must never be shared across threads.
        return when (ThreadLocalRandom.current().nextInt(0, 6)) {
            0 -> read(array, position)
            1 -> runBlocking { asAsync(Dispatchers.IO).read(array, position) }
            2, 3 -> read(ByteBuffer.wrap(array), position)
            4 -> readFully(array, position)
            else -> runBlocking { asAsync(Dispatchers.IO).readFully(array, position) }
        }
    }

    private fun assertRead(
        d: String,
        rad: RandomAccessData,
        r: RandomAccessData.(array: ByteArray, position: Long) -> Int,
    ) {
        val size = rad.size
        val sizeInt = size.toIntChecked()

        val ba0 = EMPTY_BYTE_ARRAY
        var ba8 = ByteArray(8)
        assertEquals(0, rad.r(ba0, 0), d)
        assertEquals(0, rad.r(ba0, size - 1), d)
        assertEquals(-1, rad.r(ba0, size), d)
        assertEquals(-1, rad.r(ba8, size), d)
        assertEquals(-1, rad.r(ba0, size + 1), d)
        assertEquals(-1, rad.r(ba8, size + 1), d)
        assertEquals(-1, rad.r(ba0, Int.MAX_VALUE.toLong()), d)
        assertEquals(-1, rad.r(ba8, Int.MAX_VALUE.toLong()), d)
        assertEquals(-1, rad.r(ba0, Long.MAX_VALUE), d)
        assertEquals(-1, rad.r(ba0, LONG_GIVES_INT_MINUS_2), d)
        assertEquals(-1, rad.r(ba8, LONG_GIVES_INT_MINUS_2), d)
        assertEquals(-1, rad.r(ba0, LONG_GIVES_INT_0), d)
        assertEquals(-1, rad.r(ba8, LONG_GIVES_INT_0), d)
        assertEquals(-1, rad.r(ba0, LONG_GIVES_INT_2), d)
        assertEquals(ByteArray(8), ba8, d)

        assertIOB(d) { rad.r(ba0, -1) }
        assertIOB(d) { rad.r(ba8, -1) }
        assertIOB(d) { rad.r(ba0, Int.MIN_VALUE.toLong()) }
        assertIOB(d) { rad.r(ba8, Int.MIN_VALUE.toLong()) }
        assertIOB(d) { rad.r(ba0, LONG_NEG_GIVES_INT_2) }
        assertIOB(d) { rad.r(ba8, LONG_NEG_GIVES_INT_2) }
        assertIOB(d) { rad.r(ba0, Long.MIN_VALUE) }
        assertIOB(d) { rad.r(ba8, Long.MIN_VALUE) }
        assertEquals(ByteArray(8), ba8, d)

        assertEquals(1, rad.r(ba8, size - 1), d)
        assertEquals(ByteArray(8).also { it[0] = BYTES[sizeInt - 1] }, ba8, d)

        assertEquals(8, rad.r(ba8, 0), d)
        assertEquals(BYTES.copyOf(8), ba8, d)

        ba8 = ByteArray(8)
        assertEquals(4, rad.r(ba8, size - 4), d)
        assertEquals(BYTES.copyOfRange(sizeInt - 4, sizeInt) + ByteArray(4), ba8, d)

        var ba = ByteArray(sizeInt + 3)
        assertEquals(sizeInt, rad.r(ba, 0), d)
        assertEquals(BYTES.copyOf(sizeInt) + ByteArray(3), ba, d)

        ba = ByteArray(sizeInt * 2)
        assertEquals(sizeInt, rad.r(ba, 0), d)
        assertEquals(BYTES.copyOf(sizeInt) + ByteArray(sizeInt), ba, d)
    }

    protected fun assertEmptyRad(empty: RandomAccessData) {
        assertEquals(0L, empty.size)
        assertEquals(EMPTY_BYTE_ARRAY, empty.readAllBytes())
        assertEquals(EMPTY_BYTE_ARRAY, empty.asInputStream().readBytes())
        assertEquals(EMPTY_BYTE_ARRAY, empty.asInputStream().readBytesFully(0))
        assertEquals(EMPTY_BYTE_ARRAY, empty.readFrom(0))

        assertEquals(-1, empty.read(EMPTY_BYTE_ARRAY, 0))
        assertEquals(-1, empty.read(ByteArray(1), 0))
        assertEquals(-1, empty.read(EMPTY_BYTE_ARRAY, 1))
        assertEquals(-1, empty.read(ByteArray(1), 1))

        assertEquals(-1, empty.read(ByteBuffer.wrap(EMPTY_BYTE_ARRAY), 0))
        assertEquals(-1, empty.read(ByteBuffer.wrap(ByteArray(1)), 0))
        assertEquals(-1, empty.read(ByteBuffer.wrap(EMPTY_BYTE_ARRAY), 1))
        assertEquals(-1, empty.read(ByteBuffer.wrap(ByteArray(1)), 1))

        assertEquals(-1, empty.readByteAt(0))
        assertEquals(-1, empty.readByteAt(1L))

        assertIOB { empty.readFrom(1) }
        assertIOB { empty.readByteAt(-1L) }

        runBlocking {
            val async = empty.asAsync(Dispatchers.IO)
            assertEquals(-1, async.read(EMPTY_BYTE_ARRAY, 0))
            assertEquals(-1, async.read(ByteArray(1), 0))
            assertEquals(-1, async.read(EMPTY_BYTE_ARRAY, 1))
            assertEquals(-1, async.read(ByteArray(1), 1))
            assertEquals(-1, async.read(EMPTY_BYTE_ARRAY, 0, maxLength = 0))
            assertEquals(-1, async.readFully(ByteArray(1), 0))
        }
    }

    protected fun assertIOB(description: String? = null, throwingCallable: () -> Unit) {
        assertFailsWith<IndexOutOfBoundsException>(description, throwingCallable)
    }

    protected fun assertEquals(expected: ByteArray, actual: ByteArray, message: String? = null) {
        assertEquals(expected.asList(), actual.asList(), message)
    }
}
