package fluxo.io.rad

import fluxo.io.IOException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import org.junit.Test

/**
 * A read or write that makes no progress must fail, never be retried forever. The JUnit
 * timeout only bounds the old failure mode (a spinning loop), so a regression reds instead
 * of hanging the build.
 */
internal class RadNoProgressTest {

    private companion object {
        private const val HANG_BOUND_MS = 5_000L
        private val DATA = ByteArray(8) { it.toByte() }
    }

    @Test(timeout = HANG_BOUND_MS)
    fun everyReadLoopFailsOnSourceThatReturnsNothing() {
        val rad = StreamFactoryRadAccessor(DATA.size.toLong()) {
            object : InputStream() {
                override fun read(): Int = 0
                override fun read(b: ByteArray, off: Int, len: Int): Int = 0
            }
        }
        val loops = listOf<() -> Any>(
            { rad.readFully(ByteArray(4)) },
            { rad.readFrom(0, 4) },
            { rad.readByteAt(0) },
            { rad.readAllBytes() },
            { rad.transferTo(ByteArrayOutputStream()) },
        )
        for (loop in loops) {
            val e = assertFailsWith<IOException> { loop() }
            assertContains(e.message.orEmpty(), "no progress")
        }
        rad.close()
    }

    @Test(timeout = HANG_BOUND_MS)
    fun transferToFailsOnTargetThatAcceptsNothing() {
        val full = object : WritableByteChannel {
            override fun write(src: ByteBuffer): Int = 0
            override fun isOpen() = true
            override fun close() = Unit
        }
        val rad = RadByteArrayAccessor(DATA)
        val e = assertFailsWith<IOException> { rad.transferTo(full) }
        assertContains(e.message.orEmpty(), "blocking channel")
    }
}
