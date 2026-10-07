package fluxo.io.rad

import fluxo.io.IOException
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

/**
 * The behaviour every [RandomAccessData] implementation must share, on every platform: one
 * table of named cases, run against any implementation by [verify].
 *
 * A table instead of an abstract test class: inherited test methods are discovered differently
 * by the JVM, JS and Native test runners, while a plain list runs the same everywhere, and can
 * also be run by a non-test harness. Platform-only API (streams, `ByteBuffer`, `transferTo`,
 * `readByteAt`) is tested in the platform's own test source set.
 *
 * Main code of the unpublished `:conformance-test` module, not a test source set: a module's tests
 * cannot see another module's tests, and every module that ships an implementation (the core
 * and the adapter modules) must run this same table.
 */
public object RadContract {

    /**
     * Fixture data, generated, never stored. `(i * 31 + 7)` makes no byte equal to its index and
     * none at index 0 equal to 0, so a shifted read or an untouched zeroed buffer cannot pass.
     *
     * Private, and [verify] opens a copy: a ByteArray-backed implementation wraps the array it is
     * given, so if it shared this one, a read that wrote into its data instead of out of it would
     * change the expected values too and pass every case.
     */
    private val BYTES: ByteArray = ByteArray(256) { (it * 31 + 7).toByte() }

    /** A fresh copy of the fixture data, for tests outside this table. */
    public fun bytes(): ByteArray = BYTES.copyOf()

    private const val LONG_GIVES_INT_MINUS_2: Long = Int.MAX_VALUE.toLong() + Int.MAX_VALUE
    private const val LONG_GIVES_INT_0: Long = LONG_GIVES_INT_MINUS_2 + 2
    private const val LONG_GIVES_INT_2: Long = LONG_GIVES_INT_MINUS_2 + 4
    private const val LONG_NEG_GIVES_INT_2: Long = -LONG_GIVES_INT_MINUS_2

    private val EMPTY = ByteArray(0)

    /**
     * Runs every case against data opened by [open] and fails once, naming every failed case.
     * Each case opens its own handles, so a case that closes one cannot affect the next.
     */
    public fun verify(open: (ByteArray) -> RandomAccessData) {
        val failures = CASES.mapNotNull { (name, case) ->
            runCatching { case { open(it.copyOf()) } }.exceptionOrNull()?.let { name to it }
        }
        if (failures.isNotEmpty()) {
            fail(
                "${failures.size} of ${CASES.size} contract cases failed:\n" +
                    failures.joinToString("\n") { (name, e) -> "  $name: $e" },
                failures.first().second,
            )
        }
    }

    private val CASES: List<Pair<String, ((ByteArray) -> RandomAccessData) -> Unit>> = listOf(
        "size of data and slices" to { open ->
            open(BYTES).use { rad ->
                assertEquals(BYTES.size.toLong(), rad.size)
                assertEquals(rad.size, rad.slice(0, rad.size).size)
                assertEquals(8, rad.slice(10, 8).size)
                assertEquals(12, rad.slice(123, 12).size)
                assertEquals(rad.size - 7, rad.slice(7).size)
            }
        },
        "readFrom returns copies of the requested range" to { open ->
            open(BYTES).use { rad -> forEachView(rad) { d, view -> readFromCases(d, view) } }
        },
        "readFrom on a slice is relative to the slice" to { open ->
            open(BYTES).use { rad ->
                val part = rad.slice(40, 60)
                val expected = BYTES.copyOfRange(40, 100)
                assertEquals(60L, part.size)
                assertContentEquals(EMPTY, part.readFrom(0, 0))
                assertContentEquals(EMPTY, part.readFrom(40, 0))
                assertContentEquals(expected, part.readFrom(0))
                assertContentEquals(expected, part.readFrom(0, expected.size + 1))
                assertContentEquals(byteArrayOf(BYTES[63]), part.readFrom(23, 1))
                assertContentEquals(EMPTY, part.readFrom(60, 1))
                assertIOB { part.readFrom(61, 0) }
            }
        },
        "read fills the buffer and reports the count" to { open ->
            open(BYTES).use { rad ->
                forEachView(rad, trimmed = true) { d, view ->
                    positionalReadCases(d, view) { b, p -> read(b, p) }
                    readArgumentCases(d, view)
                }
            }
        },
        "readFully reads everything available" to { open ->
            open(BYTES).use { rad ->
                forEachView(rad, trimmed = true) { d, view ->
                    positionalReadCases(d, view) { b, p -> readFully(b, p) }
                }
            }
        },
        "readAllBytes returns the whole range" to { open ->
            open(BYTES).use { rad ->
                assertContentEquals(BYTES, rad.readAllBytes())
                assertContentEquals(BYTES, rad.slice(0).readAllBytes())
                assertContentEquals(BYTES.copyOfRange(34, 179), rad.slice(34, 145).readAllBytes())
            }
        },
        "slice bounds are checked against the parent" to { open ->
            open(BYTES).use { rad ->
                assertIOB { rad.slice(-1, 1) }
                assertIOB { rad.slice(0, -1) }
                assertIOB { rad.slice(0, BYTES.size + 1L) }
                assertIOB { rad.slice(1, BYTES.size.toLong()) }
                assertIOB { rad.slice(rad.size + 1, 0) }
                assertIOB { rad.slice(rad.size, 1) }
                rad.slice(0, 256)
                rad.slice(1, 255)

                val part = rad.slice(5, 8)
                assertIOB { part.slice(0, 9) }
                assertIOB { part.slice(1, 8) }
                assertIOB { part.slice(-1, 5) }
                assertContentEquals(BYTES.copyOfRange(6, 13), part.readFrom(1L))
                assertContentEquals(BYTES.copyOfRange(7, 9), part.slice(2, 2).readAllBytes())

                for (n in longArrayOf(
                    LONG_GIVES_INT_MINUS_2, LONG_GIVES_INT_0, LONG_GIVES_INT_2,
                    LONG_NEG_GIVES_INT_2, Int.MAX_VALUE.toLong(),
                )) {
                    assertIOB { rad.slice(0, n) }
                    assertIOB { rad.slice(n, 1) }
                }
            }
        },
        "empty data and empty slices" to { open ->
            open(EMPTY).use { assertEmpty(it) }
            open(BYTES).use { rad ->
                assertEmpty(rad.slice(0, 0))
                assertEmpty(rad.slice(0, 0).slice(0, 0))
                assertEmpty(rad.slice(9, 0))
                assertEmpty(rad.slice(rad.size, 0))
            }
        },
        "a share outlives the handle it came from" to { open ->
            val rad = open(BYTES)
            val shared = rad.slice(1, 1).share()
            rad.close()
            assertContentEquals(byteArrayOf(BYTES[1]), shared.readFrom(0))
            shared.close()
        },
        "closing a handle twice gives back one ownership" to { open ->
            open(BYTES).use { rad ->
                val shared = rad.share()
                shared.close()
                shared.close()
                assertContentEquals(BYTES, rad.readAllBytes())
            }
        },
        "a closed handle and its slices never read" to { open ->
            open(BYTES).use { rad ->
                val shared = rad.slice(1, 2).share()
                val view = shared.slice(1)
                assertContentEquals(byteArrayOf(BYTES[2]), view.readFrom(0))
                shared.close()

                assertFailsWith<IOException> { shared.readFrom(0) }
                assertFailsWith<IOException> { view.readFrom(0) }
                assertFailsWith<IOException> { shared.slice(0) }
                assertFailsWith<IOException> { shared.share() }

                rad.slice(0, 1).close()
                assertContentEquals(byteArrayOf(BYTES[2]), rad.readFrom(2, 1))
            }
        },
        "every read path of a closed handle and its slices throws" to { open ->
            val rad = open(BYTES)
            val view = rad.slice(1)
            rad.close()
            for (closed in arrayOf(rad, view)) {
                assertFailsWith<IOException> { closed.readFrom(0, 1) }
                assertFailsWith<IOException> { closed.read(ByteArray(1)) }
                assertFailsWith<IOException> { closed.readFully(ByteArray(1)) }
                // Empty ranges too: no read is needed to answer them, so only a check can fail.
                assertFailsWith<IOException> { closed.readFully(ByteArray(1), closed.size) }
                assertFailsWith<IOException> { closed.readFully(ByteArray(0)) }
                assertFailsWith<IOException> { closed.read(ByteArray(0)) }
                assertFailsWith<IOException> { closed.readFrom(0, 0) }
                assertFailsWith<IOException> { closed.readFrom(closed.size) }
                assertFailsWith<IOException> { closed.readAllBytes() }
            }
        },
    )

    private fun forEachView(
        rad: RandomAccessData,
        trimmed: Boolean = false,
        block: (String, RandomAccessData) -> Unit,
    ) {
        block("data", rad)
        block("full slice", rad.slice(0, rad.size))
        if (trimmed) {
            block("trimmed slice", rad.slice(0, rad.size - 11))
        }
    }

    private fun readFromCases(d: String, rad: RandomAccessData) {
        val size = rad.size
        val sizeInt = size.toInt()
        for (bad in longArrayOf(
            size + 1, -1, Long.MIN_VALUE, Long.MAX_VALUE, Int.MAX_VALUE.toLong(),
            LONG_GIVES_INT_MINUS_2, LONG_GIVES_INT_0, LONG_GIVES_INT_2, LONG_NEG_GIVES_INT_2,
        )) {
            assertIOB(d) { rad.readFrom(bad) }
        }
        assertIOB(d) { rad.readFrom(0, -1) }
        assertIOB(d) { rad.readFrom(100, -1) }
        assertIOB(d) { rad.readFrom(1, Int.MIN_VALUE) }
        assertIOB(d) { rad.readFrom(size + 1, 1) }
        assertIOB(d) { rad.readFrom(-1, 0) }
        assertIOB(d) { rad.readFrom(-100, 1) }

        for ((pos, len) in listOf(0L to 0, 1L to 0, 200L to 0, size - 1 to 0, size to 1)) {
            assertContentEquals(EMPTY, rad.readFrom(pos, len), "$d readFrom($pos, $len)")
        }
        assertContentEquals(EMPTY, rad.readFrom(size), d)

        assertContentEquals(BYTES, rad.readFrom(0), d)
        assertContentEquals(BYTES, rad.readFrom(0, BYTES.size), d)
        assertContentEquals(BYTES, rad.readFrom(0, BYTES.size + 1), d)
        assertContentEquals(BYTES.copyOfRange(2, 5), rad.readFrom(2, 3), d)
        assertContentEquals(BYTES.copyOfRange(1, sizeInt), rad.readFrom(1, sizeInt - 1), d)
        assertContentEquals(BYTES.copyOfRange(2, sizeInt - 3), rad.readFrom(2, sizeInt - 5), d)
        assertContentEquals(BYTES.copyOfRange(56, 156), rad.readFrom(56, 100), d)
        assertContentEquals(byteArrayOf(BYTES[123]), rad.readFrom(123, 1), d)
        assertContentEquals(byteArrayOf(BYTES[255]), rad.readFrom(255, 1), d)

        val part = rad.slice(5, 8)
        assertContentEquals(BYTES.copyOfRange(5, 13), part.readFrom(0L, BYTES.size), d)
        assertContentEquals(BYTES.copyOfRange(7, 13), part.readFrom(2L, Int.MAX_VALUE), d)
        assertContentEquals(EMPTY, part.readFrom(3L, 0), d)
        assertContentEquals(BYTES.copyOfRange(8, 13), part.readFrom(3L), d)
    }

    /**
     * The positional-read rules for a platform-only read path (e.g. JVM `read(ByteBuffer, p)`),
     * checked on [rad] and its slices with the same cases as `read` and `readFully`.
     * [rad] must hold [bytes].
     */
    public fun verifyPositionalRead(
        rad: RandomAccessData,
        read: RandomAccessData.(array: ByteArray, position: Long) -> Int,
    ): Unit = forEachView(rad, trimmed = true) { d, view -> positionalReadCases(d, view, read) }

    /** Shared by `read` and `readFully`: positional results and buffer contents. */
    private fun positionalReadCases(
        d: String,
        rad: RandomAccessData,
        r: RandomAccessData.(array: ByteArray, position: Long) -> Int,
    ) {
        val size = rad.size
        val sizeInt = size.toInt()
        val ba8 = ByteArray(8)
        assertEquals(0, rad.r(EMPTY, 0), d)
        assertEquals(0, rad.r(EMPTY, size - 1), d)
        for (pastEnd in longArrayOf(
            size, size + 1, Int.MAX_VALUE.toLong(), Long.MAX_VALUE,
            LONG_GIVES_INT_MINUS_2, LONG_GIVES_INT_0, LONG_GIVES_INT_2,
        )) {
            assertEquals(-1, rad.r(EMPTY, pastEnd), "$d at $pastEnd")
            assertEquals(-1, rad.r(ba8, pastEnd), "$d at $pastEnd")
        }
        val negatives =
            longArrayOf(-1, Int.MIN_VALUE.toLong(), LONG_NEG_GIVES_INT_2, Long.MIN_VALUE)
        for (negative in negatives) {
            assertIOB(d) { rad.r(EMPTY, negative) }
            assertIOB(d) { rad.r(ba8, negative) }
        }
        assertContentEquals(ByteArray(8), ba8, "$d: rejected reads must not write")

        assertEquals(1, rad.r(ba8, size - 1), d)
        assertContentEquals(ByteArray(8).also { it[0] = BYTES[sizeInt - 1] }, ba8, d)
        assertEquals(8, rad.r(ba8, 0), d)
        assertContentEquals(BYTES.copyOf(8), ba8, d)

        val tail = ByteArray(8)
        assertEquals(4, rad.r(tail, size - 4), d)
        assertContentEquals(BYTES.copyOfRange(sizeInt - 4, sizeInt) + ByteArray(4), tail, d)

        val big = ByteArray(sizeInt * 2)
        assertEquals(sizeInt, rad.r(big, 0), d)
        assertContentEquals(BYTES.copyOf(sizeInt) + ByteArray(sizeInt), big, d)
    }

    /** `read`'s offset and maxLength arguments. */
    @Suppress("LongMethod")
    private fun readArgumentCases(d: String, rad: RandomAccessData) {
        val size = rad.size
        val sizeInt = size.toInt()
        val ba8 = ByteArray(8)
        for (pos in longArrayOf(0, 1, 200, size - 1)) {
            assertEquals(0, rad.read(EMPTY, pos, maxLength = 0), "$d at $pos")
            assertEquals(0, rad.read(ba8, pos, maxLength = 0), "$d at $pos")
        }
        assertEquals(0, rad.read(EMPTY, size - 1, maxLength = 999), d)
        assertEquals(-1, rad.read(ba8, size, maxLength = 1), d)
        assertEquals(-1, rad.read(ba8, size + 2, maxLength = 1), d)

        assertIOB(d) { rad.read(ba8, 0, maxLength = -1) }
        assertIOB(d) { rad.read(ba8, 100, maxLength = -1) }
        assertIOB(d) { rad.read(ba8, 0, offset = -1) }
        assertIOB(d) { rad.read(ba8, 0, Int.MIN_VALUE, maxLength = 0) }
        assertIOB(d) { rad.read(ba8, -1, maxLength = 0) }
        assertIOB(d) { rad.read(ba8, -100, maxLength = 1) }
        assertContentEquals(ByteArray(8), ba8, "$d: rejected reads must not write")

        // Exact counts: the spec allows a short `read`, but these ranges are far below any
        // source's per-call cap, so a short count here means a lost byte, not a cap.
        var ba = ByteArray(sizeInt)
        assertEquals(sizeInt, rad.read(ba, 0L, maxLength = sizeInt), d)
        assertContentEquals(BYTES.copyOf(sizeInt), ba, d)

        ba = ByteArray(100)
        assertEquals(100, rad.read(ba, 56, maxLength = 100), d)
        assertContentEquals(BYTES.copyOfRange(56, 156), ba, d)
        assertEquals(65, rad.read(ba, 75, maxLength = 65), d)
        assertContentEquals(BYTES.copyOfRange(75, 140) + BYTES.copyOfRange(121, 156), ba, d)
        assertEquals(45, rad.read(ba, 12, 24, 45), d)
        assertContentEquals(
            BYTES.copyOfRange(75, 99) + BYTES.copyOfRange(12, 57) + BYTES.copyOfRange(125, 156),
            ba, d,
        )

        val ba1 = ByteArray(1)
        assertEquals(0, rad.read(ba1, 129, 1, 0), d)
        assertEquals(0, rad.read(ba1, 129, 1, 1), d)
        assertIOB(d) { rad.read(ba1, 129, 2, 0) }
        assertIOB(d) { rad.read(ba1, 132, 3, 1) }
        assertIOB(d) { rad.read(ba1, 123, 2, 2) }
        assertIOB(d) { rad.read(EMPTY, 102, 1, 1) }
        assertContentEquals(ByteArray(1), ba1, "$d: rejected reads must not write")
        assertEquals(1, rad.read(ba1, 133, 0, 1), d)
        assertContentEquals(byteArrayOf(BYTES[133]), ba1, d)
        assertEquals(1, rad.read(ba1, 123, 0, 2), d)
        assertContentEquals(byteArrayOf(BYTES[123]), ba1, d)
        assertEquals(1, rad.read(ba1, 124, 0, Int.MAX_VALUE), d)
        assertContentEquals(byteArrayOf(BYTES[124]), ba1, d)

        val part = rad.slice(5, 8)
        ba = ByteArray(sizeInt)
        assertIOB(d) { part.read(ba, 0L, sizeInt + 1) }
        assertEquals(0, part.read(ba, 0L, offset = sizeInt), d)
        assertEquals(8, part.read(ba, 0L, maxLength = sizeInt + 123), d)
        assertContentEquals(BYTES.copyOfRange(5, 13), ba.copyOf(8), d)
        assertContentEquals(ByteArray(sizeInt - 8), ba.copyOfRange(8, sizeInt), "$d: no overrun")
    }

    private fun assertEmpty(empty: RandomAccessData) {
        assertEquals(0L, empty.size)
        assertContentEquals(EMPTY, empty.readAllBytes())
        assertContentEquals(EMPTY, empty.readFrom(0))
        assertEquals(-1, empty.read(EMPTY, 0))
        assertEquals(-1, empty.read(ByteArray(1), 0))
        assertEquals(-1, empty.read(EMPTY, 1))
        assertEquals(-1, empty.read(ByteArray(1), 1))
        assertEquals(-1, empty.readFully(ByteArray(1), 0))
        assertIOB { empty.readFrom(1) }
    }

    private inline fun assertIOB(description: String? = null, block: () -> Unit) {
        assertFailsWith<IndexOutOfBoundsException>(description, block)
    }
}
