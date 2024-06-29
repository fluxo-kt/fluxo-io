package fluxo.io.rad

import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests for [RadFileChannelAccessor].
 */
@RunWith(Parameterized::class)
internal class RandomAccessDataFileChannelTest(
    factory: (File) -> RandomAccessData,
) : AbstractRandomAccessDataTest(factory) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data() = arrayOf<(File) -> RandomAccessData>(
            { RadFileChannelAccessor(it) },
            { RadFileChannelAccessor(it.inputStream()) },
            { RadFileChannelAccessor(it.inputStream().channel) },
            { RadFileChannelAccessor(it.inputStream().fd) },
            { RadFileChannelAccessor(RandomAccessFile(it, "r").channel) },
            { RadFileChannelAccessor(RandomAccessFile(it, "r").fd) },
        ).asList()
    }


    @Test
    fun fileExists() {
        assertFailsWith<FileNotFoundException> {
            factory(File("/does/not/exist"))
        }
    }

    @Test
    fun creationBoundaries() {
        val emptyFile = File.createTempFile("tempFile123", "tmp")
        assertEmptyRad(RadFileChannelAccessor(emptyFile))
        assertEmptyRad(RadFileChannelAccessor(emptyFile, 0, 0))

        assertEmptyRad(RadFileChannelAccessor(tempFile, BYTES.size.toLong()))
        assertEmptyRad(RadFileChannelAccessor(tempFile, BYTES.size.toLong(), 0L))

        assertIOB { RadFileChannelAccessor(tempFile, -1, 0) }
        assertIOB { RadFileChannelAccessor(tempFile, -1, 1) }
        assertIOB { RadFileChannelAccessor(tempFile, 0, -2) }
        assertIOB { RadFileChannelAccessor(tempFile, 1, -2) }
        assertIOB { RadFileChannelAccessor(tempFile, BYTES.size + 1L) }
        assertIOB { RadFileChannelAccessor(tempFile, BYTES.size.toLong(), 1L) }
        assertIOB { RadFileChannelAccessor(tempFile, BYTES.size + 1L, 1L) }
        assertIOB { RadFileChannelAccessor(tempFile, 0L, BYTES.size + 1L) }
    }
}
