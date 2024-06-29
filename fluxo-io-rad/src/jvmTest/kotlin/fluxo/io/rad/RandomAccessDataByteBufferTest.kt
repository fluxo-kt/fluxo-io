package fluxo.io.rad

import fluxo.io.nio.flipCompat
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/**
 * Tests for [RadByteBufferAccessor].
 */
@RunWith(Parameterized::class)
internal class RadByteBufferAccessorTest(
    factory: (File) -> RandomAccessData,
) : AbstractRandomAccessDataTest(factory) {

    companion object {
        @JvmStatic
        @Parameters
        fun data() = arrayOf<(File) -> RandomAccessData>(
            { RadByteBufferAccessor(BYTES) },
            {
                RadByteBufferAccessor(
                    ByteBuffer.allocateDirect(BYTES.size)
                        .also { it.put(BYTES).flipCompat() },
                )
            },
            { RadByteBufferAccessor(it.inputStream().channel) },
            { RadByteBufferAccessor(it.inputStream().fd) },
            { RadByteBufferAccessor(RandomAccessFile(it, "r").channel) },
            { RadByteBufferAccessor(RandomAccessFile(it, "r").fd) },
        ).asList()
    }


    @Test
    fun fileExists() {
        assertFailsWith<FileNotFoundException> {
            RadByteBufferAccessor(File("/does/not/exist"))
        }
    }

    @Test
    fun creationBoundaries() {
        val emptyFile = File.createTempFile("tempFile123", "tmp")
        assertEmptyRad(RadByteBufferAccessor(emptyFile))
        assertEmptyRad(RadByteBufferAccessor(emptyFile, 0, 0))

        assertEmptyRad(RadByteBufferAccessor(tempFile, BYTES.size.toLong()))
        assertEmptyRad(RadByteBufferAccessor(tempFile, BYTES.size.toLong(), 0))

        assertIOB { RadByteBufferAccessor(tempFile, -1, 0) }
        assertIOB { RadByteBufferAccessor(tempFile, -1, 1) }
        assertIOB { RadByteBufferAccessor(tempFile, 0, -2) }
        assertIOB { RadByteBufferAccessor(tempFile, 1, -2) }
        assertIOB { RadByteBufferAccessor(tempFile, BYTES.size + 1L) }
        assertIOB { RadByteBufferAccessor(tempFile, BYTES.size.toLong(), 1) }
        assertIOB { RadByteBufferAccessor(tempFile, BYTES.size + 1L, 1) }
        assertIOB { RadByteBufferAccessor(tempFile, 0L, BYTES.size + 1) }
    }
}
