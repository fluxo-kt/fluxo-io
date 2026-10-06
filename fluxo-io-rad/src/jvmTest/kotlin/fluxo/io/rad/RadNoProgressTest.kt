package fluxo.io.rad

import fluxo.io.IOException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.WritableByteChannel
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import org.junit.Test

/**
 * A read or write that makes no progress must fail, never be retried forever. The fake source
 * and target throw [AssertionError] once called far more often than any honest loop would, so
 * a spinning regression fails at once instead of hanging the build.
 */
internal class RadNoProgressTest {

    private companion object {
        private const val SPIN_GUARD = 1_000
        private val DATA = ByteArray(8) { it.toByte() }
    }

    @Test
    fun everyReadLoopFailsOnSourceThatReturnsNothing() {
        var calls = 0
        fun nothing(): Int {
            if (++calls > SPIN_GUARD) throw AssertionError("spinning on no progress")
            return 0
        }
        val rad = StreamFactoryRadAccessor(DATA.size.toLong()) {
            object : InputStream() {
                override fun read(): Int = nothing()
                override fun read(b: ByteArray, off: Int, len: Int): Int = nothing()
            }
        }
        val loops = listOf<() -> Any>(
            { rad.readFully(ByteArray(4)) },
            { rad.readFrom(0, 4) },
            { rad.readByteAt(0) },
            { rad.readAllBytes() },
            { rad.transferTo(ByteArrayOutputStream()) },
            { rad.transferTo(Channels.newChannel(ByteArrayOutputStream())) },
        )
        for (loop in loops) {
            calls = 0
            val e = assertFailsWith<IOException> { loop() }
            assertContains(e.message.orEmpty(), "no progress")
        }
        rad.close()
    }

    @Test
    fun transferToFailsOnTargetThatAcceptsNothing() {
        var calls = 0
        val full = object : WritableByteChannel {
            override fun write(src: ByteBuffer): Int {
                if (++calls > SPIN_GUARD) throw AssertionError("spinning on no progress")
                return 0
            }
            override fun isOpen() = true
            override fun close() = Unit
        }
        val file = File.createTempFile("noProgress", "tmp").apply { writeBytes(DATA) }
        try {
            // The generic copy loop, and FileChannel.transferTo, which returns 0 here.
            for (rad in listOf(RadByteArrayAccessor(DATA), RandomAccessData.open(file))) {
                rad.use {
                    calls = 0
                    val e = assertFailsWith<IOException> { it.transferTo(full) }
                    assertContains(e.message.orEmpty(), "blocking")
                }
            }
        } finally {
            file.delete()
        }
    }
}
