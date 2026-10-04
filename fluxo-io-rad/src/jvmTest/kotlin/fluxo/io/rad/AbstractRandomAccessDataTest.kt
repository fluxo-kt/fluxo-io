@file:Suppress("KotlinConstantConditions", "KDocUnresolvedReference", "LargeClass", "LongMethod")

package fluxo.io.rad

import fluxo.io.IOException
import fluxo.io.nio.flipCompat
import fluxo.io.nio.releaseCompat
import fluxo.io.readBytesExact
import fluxo.io.readBytesFully
import fluxo.io.readableFileSize
import fluxo.io.toArray
import fluxo.io.useLocked
import fluxo.io.util.EMPTY_BYTE_ARRAY
import fluxo.io.util.toIntChecked
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util.concurrent.Executors
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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


        private val RAND: ThreadLocalRandom = ThreadLocalRandom.current()

        val RANDOM_BYTES = ByteArray(98765).also {
            RAND.nextBytes(it)
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


    @Test
    fun readWithOffsetAndLengthShouldRead() = runTest(timeout = DEFAULT_TIMEOUT) {
        val read = rad.readFrom(2, 3)
        assertEquals(byteArrayOf(2, 3, 4), read)
    }

    @Test
    fun readWhenOffsetIsBeyondEOFShouldThrowException() = runTest(timeout = DEFAULT_TIMEOUT) {
        assertIOB { rad.readFrom(257, 0) }
    }

    @Test
    fun readWhenOffsetIsBeyondEndOfSliceShouldThrowException() {
        val slice = rad.slice(0, 10)
        assertIOB { slice.readFrom(11, 0) }
    }

    @Test
    fun readWhenOffsetPlusLengthGreaterThanEOFShouldThrowException() {
        rad.readFrom(BYTES.size - 1L, 1)
        assertEquals(EMPTY_BYTE_ARRAY, rad.readFrom(BYTES.size.toLong(), 1))
        assertIOB { rad.readFrom(BYTES.size + 1L, 1) }
    }

    @Test
    fun readWhenOffsetPlusLengthGreaterThanEndOfSliceShouldThrowException() {
        val slice = rad.slice(0, 10)
        assertEquals(EMPTY_BYTE_ARRAY, slice.readFrom(10, 1))
        assertIOB { slice.readFrom(11, 1) }
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
    fun sliceNegativeOffset() = runTest(timeout = DEFAULT_TIMEOUT) {
        assertIOB { rad.slice(-1, 1) }
    }

    @Test
    fun sliceNegativeLength() = runTest(timeout = DEFAULT_TIMEOUT) {
        assertIOB { rad.slice(0, -1) }
    }

    @Test
    fun sliceZeroLength() = runTest(timeout = DEFAULT_TIMEOUT) {
        val slice = rad.slice(0, 0)
        assertEquals(-1, slice.asInputStream().read())
    }

    @Test
    fun sliceTooBig() = runTest(timeout = DEFAULT_TIMEOUT) {
        rad.slice(0, 256)
        assertIOB { rad.slice(0, 257) }
    }

    @Test
    fun sliceTooBigWithOffset() = runTest(timeout = DEFAULT_TIMEOUT) {
        rad.slice(1, 255)
        assertIOB { rad.slice(1, 256) }
    }

    @Test
    fun slice() = runTest(timeout = DEFAULT_TIMEOUT) {
        val slice = rad.slice(1, 1)
        assertEquals(1, slice.asInputStream().read())
    }

    @Test
    fun shareOutlivesParentClose() = runTest(timeout = DEFAULT_TIMEOUT) {
        val shared = rad.slice(1, 1).share()
        rad.close()

        assertEquals(1, shared.readByteAt(0))
        shared.close()
    }

    /**
     * Closing one handle more than once must be a no-op, per the [java.io.Closeable]
     * contract. A second close must NOT give back the ownership again, else it frees the
     * underlying resource still in use by other handles.
     */
    @Test
    fun doubleClosingShareKeepsDataForParent() = runTest(timeout = DEFAULT_TIMEOUT) {
        val shared = rad.share()
        shared.close()
        shared.close()

        assertEquals(1, rad.readByteAt(1))
        assertEquals(BYTES, rad.readAllBytes())
    }

    /**
     * A closed handle and its slices reject reads even while another handle keeps the same
     * data open: a read-after-close must fail at the bug, never depend on unrelated handles.
     * Closing a slice does nothing.
     */
    @Test
    fun closedHandleRejectsReadsWhileOtherHandleIsOpen() = runTest(timeout = DEFAULT_TIMEOUT) {
        val shared = rad.slice(1, 2).share()
        val view = shared.slice(1)
        assertEquals(2, view.readByteAt(0))
        shared.close()

        assertFailsWith<IOException> { shared.readByteAt(0) }
        assertFailsWith<IOException> { view.readByteAt(0) }
        assertFailsWith<IOException> { shared.slice(0) }
        assertFailsWith<IOException> { shared.share() }

        rad.slice(0, 1).close()
        assertEquals(2, rad.readByteAt(2))
    }

    /**
     * After its handle closes, every public read path of the handle and of its slices fails
     * with [IOException] instead of returning bytes. Each path is listed because an
     * implementation may route it through its own fast-path override.
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
            assertFailsWith<IOException> { closed.readFrom(0, 1) }
            assertFailsWith<IOException> { closed.read(ByteArray(1)) }
            assertFailsWith<IOException> { closed.read(ByteBuffer.allocate(1), 0) }
            assertFailsWith<IOException> { closed.transferTo(sink) }
            assertFailsWith<IOException> { closed.readAllBytes() }
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
    fun testSize() = runTest(timeout = DEFAULT_TIMEOUT) {
        assertEquals(BYTES.size.toLong(), rad.size)
        assertEquals(rad.size, rad.slice(0, rad.size).size)
        assertEquals(8, rad.slice(10, 8).size)
        assertEquals(12, rad.slice(123, 12).size)
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
    fun testSlice() = runTest(timeout = DEFAULT_TIMEOUT) {
        val finished = AtomicBoolean(false)
        val thread = Thread.currentThread()
        val timeoutThread = Thread {
            try {
                Thread.sleep(DEFAULT_TIMEOUT.inWholeMilliseconds)
                if (!finished.get()) {
                    thread.interrupt()
                    this@runTest.cancel()
                }
            } catch (_: InterruptedException) {
            }
        }
        try {
            timeoutThread.start()

            val copy = rad.slice(0, rad.size)
            assertEquals(BYTES.size.toLong(), copy.size)
            assertEquals(BYTES, copy.readAllBytes())

            assertEquals(copy.readByteAt(3), rad.readByteAt(3))
            assertEquals(copy.readByteAt(156), rad.readByteAt(156))
            assertEquals(copy.readByteAt(BYTES.size + 12L), rad.readByteAt(BYTES.size + 12L))

            assertIOB { rad.slice(0, BYTES.size + 1L) }
            assertIOB { rad.slice(1, BYTES.size.toLong()) }
            assertIOB { rad.slice(-1, 5) }
            assertIOB { rad.slice(0, -1) }
            assertIOB { rad.slice(rad.size + 1, 0) }
            assertIOB { rad.slice(rad.size, 1) }


            val part = rad.slice(5, 8)
            assertIOB { part.slice(0, 9) }
            assertIOB { part.slice(1, 8) }
            assertIOB { part.slice(-1, 5) }

            assertEquals(BYTES.copyOfRange(5, 13), part.readAllBytes())
            assertEquals(BYTES.copyOfRange(5, 13), part.readFrom(0L, Int.MAX_VALUE))
            assertEquals(BYTES.copyOfRange(6, 13), part.readFrom(1L, Int.MAX_VALUE))
            assertIOB { part.readFrom(-1L, 1) }

            assertEmptyRad(rad.slice(0, 0))
            assertEmptyRad(rad.slice(0, 0).slice(0, 0))
            assertEmptyRad(rad.slice(9, 0))
            assertEmptyRad(rad.slice(rad.size, 0))

            assertIOB { rad.slice(0, LONG_GIVES_INT_MINUS_2) }
            assertIOB { rad.slice(0, LONG_GIVES_INT_0) }
            assertIOB { rad.slice(0, LONG_GIVES_INT_2) }
            assertIOB { rad.slice(0, LONG_NEG_GIVES_INT_2) }
            assertIOB { rad.slice(0, Int.MAX_VALUE.toLong()) }
            assertIOB { rad.slice(LONG_GIVES_INT_MINUS_2, 1) }
            assertIOB { rad.slice(LONG_GIVES_INT_0, 1) }
            assertIOB { rad.slice(LONG_GIVES_INT_2, 1) }
            assertIOB { rad.slice(LONG_NEG_GIVES_INT_2, 1) }
            assertIOB { rad.slice(Int.MAX_VALUE.toLong(), 1) }
        } finally {
            finished.set(true)
            timeoutThread.interrupt()
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
    fun testReadToNewArray() = runTest(timeout = DEFAULT_TIMEOUT) {
        arrayOf(rad, rad.slice(0, rad.size)).forEachIndexed { ri, rad ->
            val d = "rad #$ri"
            val size = rad.size
            val sizeInt = size.toIntChecked()

            assertIOB(d) { rad.readFrom(0, -1) }
            assertIOB(d) { rad.readFrom(100, -1) }
            assertIOB(d) { rad.readFrom(1, Int.MIN_VALUE) }
            assertIOB(d) { rad.readFrom(size + 1) }
            assertIOB(d) { rad.readFrom(-1) }
            assertIOB(d) { rad.readFrom(Long.MIN_VALUE) }
            assertIOB(d) { rad.readFrom(Long.MAX_VALUE) }
            assertIOB(d) { rad.readFrom(Int.MAX_VALUE.toLong()) }
            assertIOB(d) { rad.readFrom(LONG_GIVES_INT_MINUS_2) }
            assertIOB(d) { rad.readFrom(LONG_GIVES_INT_0) }
            assertIOB(d) { rad.readFrom(LONG_GIVES_INT_2) }
            assertIOB(d) { rad.readFrom(LONG_NEG_GIVES_INT_2) }

            assertEquals(EMPTY_BYTE_ARRAY, rad.readFrom(0, 0), d)
            assertEquals(EMPTY_BYTE_ARRAY, rad.readFrom(1, 0), d)
            assertEquals(EMPTY_BYTE_ARRAY, rad.readFrom(200, 0), d)
            assertEquals(EMPTY_BYTE_ARRAY, rad.readFrom(size), d)
            assertEquals(EMPTY_BYTE_ARRAY, rad.readFrom(size - 1, 0), d)

            assertEquals(BYTES, rad.readFrom(0), d)
            assertEquals(BYTES, rad.readFrom(0, BYTES.size), d)
            assertEquals(BYTES, rad.readFrom(0, BYTES.size + 1), d)
            assertEquals(BYTES, rad.readFrom(0, Int.MAX_VALUE), d)

            assertEquals(BYTES.copyOfRange(1, sizeInt), rad.readFrom(1, sizeInt - 1), d)
            assertEquals(BYTES.copyOfRange(2, sizeInt - 3), rad.readFrom(2, sizeInt - 5), d)
            assertEquals(BYTES.copyOfRange(56, 156), rad.readFrom(56, 100), d)
            assertEquals(BYTES.copyOfRange(70, 150), rad.readFrom(70, 80), d)

            assertEquals(EMPTY_BYTE_ARRAY, rad.readFrom(size), d)
            assertEquals(EMPTY_BYTE_ARRAY, rad.readFrom(size, 0), d)
            assertEquals(EMPTY_BYTE_ARRAY, rad.readFrom(size, maxLength = 1), d)

            assertIOB(d) { rad.readFrom(size + 1, 1) }

            assertIOB(d) { rad.readFrom(-1, 0) }

            assertIOB(d) { rad.readFrom(-100, 1) }

            assertEquals(byteArrayOf(BYTES[123]), rad.readFrom(123, 1), d)
            assertEquals(byteArrayOf(BYTES[200]), rad.readFrom(200, 1), d)

            val part = rad.slice(5, 8)
            assertEquals(BYTES.copyOfRange(5, 13), part.readFrom(0L, BYTES.size), d)
            assertEquals(BYTES.copyOfRange(7, 13), part.readFrom(2L, Int.MAX_VALUE), d)
            assertEquals(EMPTY_BYTE_ARRAY, part.readFrom(3L, 0), d)
            assertEquals(BYTES.copyOfRange(8, 13), part.readFrom(3L), d)
            assertEquals(BYTES.copyOfRange(8, 13), part.readFrom(3L, Int.MAX_VALUE), d)
        }

        val part = rad.slice(40, 60)
        val expected = BYTES.copyOfRange(40, 100)
        assertEquals(60L, part.size)
        assertEquals(EMPTY_BYTE_ARRAY, part.readFrom(0, 0))
        assertEquals(EMPTY_BYTE_ARRAY, part.readFrom(1, 0))
        assertEquals(EMPTY_BYTE_ARRAY, part.readFrom(40, 0))
        assertEquals(EMPTY_BYTE_ARRAY, part.readFrom(part.size - 1, 0))

        assertEquals(expected, part.readFrom(0))
        assertEquals(expected, part.readFrom(0, expected.size))
        assertEquals(expected, part.readFrom(0, expected.size + 1))
        assertEquals(expected, part.readFrom(0, Int.MAX_VALUE))

        assertEquals(byteArrayOf(BYTES[63]), part.readFrom(23, 1))
        assertEquals(byteArrayOf(BYTES[84]), part.readFrom(44, 1))
    }

    @Test
    fun testReadArray() = runTest(timeout = DEFAULT_TIMEOUT) {
        arrayOf(
            rad,
            rad.slice(0, rad.size),
            rad.slice(0, rad.size - 11),
        ).forEachIndexed { ri, rad ->
            val d = "rad #$ri"

            assertRead(d, rad) { array, position ->
                read(array, position)
            }

            val size = rad.size
            val sizeInt = size.toIntChecked()

            val ba0 = EMPTY_BYTE_ARRAY
            val ba8 = ByteArray(8)
            assertEquals(0, rad.read(ba0, size - 1, maxLength = 0), d)
            assertEquals(0, rad.read(ba0, size - 1, maxLength = 999), d)
            assertEquals(-1, rad.read(ba0, size, maxLength = 1), d)
            assertEquals(-1, rad.read(ba8, size, maxLength = 1), d)
            assertEquals(0, rad.read(ba0, 0, maxLength = 0), d)
            assertEquals(0, rad.read(ba8, 0, maxLength = 0), d)
            assertEquals(0, rad.read(ba0, 1, maxLength = 0), d)
            assertEquals(0, rad.read(ba8, 1, maxLength = 0), d)
            assertEquals(0, rad.read(ba0, 200, maxLength = 0), d)
            assertEquals(0, rad.read(ba8, 200, maxLength = 0), d)
            assertEquals(0, rad.read(ba0, size - 1, maxLength = 0), d)
            assertEquals(0, rad.read(ba8, size - 1, maxLength = 0), d)
            assertEquals(-1, rad.read(ba0, size + 2, maxLength = 1), d)
            assertEquals(-1, rad.read(ba8, size + 2, maxLength = 1), d)
            assertEquals(ByteArray(8), ba8, d)

            assertIOB(d) { rad.read(ba0, 0, maxLength = -1) }
            assertIOB(d) { rad.read(ba8, 0, maxLength = -1) }
            assertIOB(d) { rad.read(ba0, 100, maxLength = -1) }
            assertIOB(d) { rad.read(ba8, 100, maxLength = -1) }
            assertIOB(d) { rad.read(ba8, 0, -1) }
            assertIOB(d) { rad.read(ba8, 0, offset = -1) }
            assertIOB(d) { rad.read(ba8, 0, Int.MIN_VALUE, maxLength = 0) }
            assertIOB(d) { rad.read(ba8, 0, offset = Int.MIN_VALUE, 0) }
            assertIOB(d) { rad.read(ba0, -1, maxLength = 0) }
            assertIOB(d) { rad.read(ba8, -1, maxLength = 0) }
            assertIOB(d) { rad.read(ba0, -100, maxLength = 1) }
            assertIOB(d) { rad.read(ba8, -100, maxLength = 1) }
            assertEquals(ByteArray(8), ba8, d)

            var ba = ByteArray(sizeInt)
            assertEquals(sizeInt, rad.read(ba, 0L, maxLength = sizeInt), d)
            assertEquals(BYTES.copyOf(sizeInt), ba, d)

            ba = ByteArray(sizeInt)
            assertEquals(sizeInt, rad.read(ba, 0, maxLength = Int.MAX_VALUE), d)
            assertEquals(BYTES.copyOf(sizeInt), ba, d)

            ba = ByteArray(100)
            assertEquals(100, rad.read(ba, 56, maxLength = 100), d)
            assertEquals(BYTES.copyOfRange(56, 156), ba, d)

            assertEquals(65, rad.read(ba, 75, maxLength = 65), d)
            assertEquals(BYTES.copyOfRange(75, 140) + BYTES.copyOfRange(121, 156), ba, d)

            assertEquals(45, rad.read(ba, 12, 24, 45), d)
            assertEquals(
                BYTES.copyOfRange(75, 99)
                    + BYTES.copyOfRange(12, 57)
                    + BYTES.copyOfRange(125, 156),
                ba, d,
            )

            ba = ByteArray(80)
            assertEquals(80, rad.read(ba, 70, maxLength = 80), d)
            assertEquals(BYTES.copyOfRange(70, 150), ba, d)

            val ba1 = ByteArray(1)
            assertEquals(0, rad.read(ba1, 129, 1, 0), d)
            assertEquals(0, rad.read(ba1, 129, 1, 1), d)
            assertIOB(d) { rad.read(ba1, 129, 2, 0) }
            assertEquals(ByteArray(1), ba1, d)

            assertEquals(0, rad.read(ba1, 129, maxLength = 0), d)
            assertEquals(ByteArray(1), ba1, d)

            assertIOB(d) { rad.read(ba1, 130, 2) }
            assertEquals(0, rad.read(ba1, 130, maxLength = 0), d)
            assertEquals(ByteArray(1), ba1, d)

            assertEquals(0, rad.read(ba1, 131, offset = 1, maxLength = 0), d)
            assertIOB(d) { rad.read(ba1, 131, offset = 2, maxLength = 0) }
            assertEquals(0, rad.read(ba1, 131, maxLength = 0), d)
            assertEquals(ByteArray(1), ba1, d)

            assertIOB(d) { rad.read(ba1, 121, offset = 5, maxLength = 0) }
            assertEquals(0, rad.read(ba1, 121, maxLength = 0), d)
            assertEquals(ByteArray(1), ba1, d)

            assertIOB(d) { rad.read(ba1, 132, 3, 1) }
            assertEquals(ByteArray(1), ba1, d)

            assertIOB(d) { rad.read(ba1, 123, 2, 2) }
            assertEquals(ByteArray(1), ba1, d)

            assertIOB(d) { rad.read(ba0, 102, 1, 1) }

            assertEquals(1, rad.read(ba1, 133, 0, 1), d)
            assertEquals(byteArrayOf(BYTES[133]), ba1, d)

            assertEquals(1, rad.read(ba1, 134, maxLength = 1), d)
            assertEquals(byteArrayOf(BYTES[134]), ba1, d)

            assertEquals(1, rad.read(ba1, 135, offset = 0, maxLength = 1), d)
            assertEquals(byteArrayOf(BYTES[135]), ba1, d)

            assertEquals(1, rad.read(ba1, 200, maxLength = 1), d)
            assertEquals(byteArrayOf(BYTES[200]), ba1, d)

            assertEquals(1, rad.read(ba1, 123, 0, 2), d)
            assertEquals(byteArrayOf(BYTES[123]), ba1, d)

            assertEquals(1, rad.read(ba1, 124, 0, Int.MAX_VALUE), d)
            assertEquals(byteArrayOf(BYTES[124]), ba1, d)

            val part = rad.slice(5, 8)
            ba = ByteArray(sizeInt)
            assertIOB(d) { part.read(ba, 0L, sizeInt + 1) }
            assertIOB(d) { part.read(ba, position = 0L, offset = sizeInt + 1) }
            assertEquals(0, part.read(ba, 0L, sizeInt), d)
            assertEquals(0, part.read(ba, 0L, offset = sizeInt), d)
            assertEquals(8, part.read(ba, 0L, maxLength = sizeInt + 123), d)
            assertEquals(BYTES.copyOfRange(5, 13), ba.copyOf(8), d)
            (0 until 8).forEach { ba[it] = 0 }
            assertEquals(ByteArray(sizeInt), ba, d)
        }
    }

    @Test
    fun testAsyncReadArray() = runTest(timeout = DEFAULT_TIMEOUT) {
        arrayOf(
            rad,
            rad.slice(0, rad.size),
            rad.slice(0, rad.size - 11),
        ).forEachIndexed { ri, rad ->
            val d = "rad #$ri"
            val async = rad.asAsync(Dispatchers.IO)
            assertRead(d, rad) { array, position ->
                runBlocking { async.read(array, position) }
            }
            runBlocking {
                assertEquals(0, async.read(EMPTY_BYTE_ARRAY, 0, maxLength = 0), d)
            }
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

        for (buffer in arrayOf(
            ByteBuffer.wrap(ByteArray(3)),
            ByteBuffer.allocateDirect(3),
        )) {
            try {
                val read = rad.read(buffer, 2)
                assertEquals(3, read)
                assertEquals(3, buffer.capacity())
                assertEquals(3, buffer.position())
                assertEquals(3, buffer.limit())
                assertEquals(EMPTY_BYTE_ARRAY, buffer.toArray())
                buffer.flipCompat()
                assertEquals(byteArrayOf(2, 3, 4), buffer.toArray())
            } finally {
                buffer.releaseCompat()
            }
        }
    }

    @Test
    fun testTransferTo() = runTest(timeout = DEFAULT_TIMEOUT) {
        val bufferSizes = intArrayOf(
            1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144,
        )
        arrayOf(
            rad,
            rad.slice(0, rad.size),
        ).forEachIndexed { ri, rad ->
            val d = "rad #$ri"

            assertEquals(BYTES.size.toLong(), rad.size, d)

            for (bufSize in bufferSizes) {
                assertEquals(
                    BYTES,
                    rad.let {
                        val out = ByteArrayOutputStream(BYTES.size)
                        it.transferTo(out, bufSize)
                        out.toByteArray()
                    },
                    "$d, bufSize=${readableFileSize(bufSize)}",
                )
            }

            val emptyRad = rad.slice(0, 0)

            var tempFile = File.createTempFile("tempTransferToFile1", "tmp")
            try {
                assertEquals(
                    EMPTY_BYTE_ARRAY,
                    emptyRad.let {
                        FileOutputStream(tempFile).useLocked { out ->
                            it.transferTo(out)
                        }
                        tempFile.readBytes()
                    },
                    d,
                )

                for (bufSize in bufferSizes) {
                    assertEquals(
                        BYTES,
                        rad.let {
                            FileOutputStream(tempFile).useLocked { out ->
                                it.transferTo(out, bufSize)
                            }
                            tempFile.readBytes()
                        },
                        "$d, bufSize=${readableFileSize(bufSize)}",
                    )
                }
            } finally {
                check(tempFile.delete()) { "Failed to delete temp file: $tempFile" }
            }

            tempFile = File.createTempFile("tempTransferToFile2", "tmp")
            try {
                assertEquals(
                    EMPTY_BYTE_ARRAY,
                    emptyRad.let {
                        FileOutputStream(tempFile).channel.useLocked { out ->
                            out.truncate(0L)
                            it.transferTo(out)
                        }
                        tempFile.readBytes()
                    },
                    d,
                )

                assertEquals(
                    BYTES,
                    rad.let {
                        FileOutputStream(tempFile).channel.useLocked { out ->
                            out.truncate(0L)
                            it.transferTo(out)
                        }
                        tempFile.readBytes()
                    },
                    d,
                )

                for (directBuffer in booleanArrayOf(true, false)) {
                    for (bufSize in bufferSizes) {
                        assertEquals(
                            BYTES,
                            rad.let {
                                FileOutputStream(tempFile).channel.useLocked { out ->
                                    out.truncate(0L)
                                    it.transferTo(out, bufSize, directBuffer)
                                }
                                tempFile.readBytes()
                            },
                            "$d, bufSize=$bufSize bytes, direct=$directBuffer",
                        )
                    }
                }
            } finally {
                check(tempFile.delete()) { "Failed to delete temp file: $tempFile" }
            }
        }
    }


    private fun RandomAccessData.randRead(array: ByteArray, position: Long): Int {
        return when (RAND.nextInt(0, 6)) {
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
