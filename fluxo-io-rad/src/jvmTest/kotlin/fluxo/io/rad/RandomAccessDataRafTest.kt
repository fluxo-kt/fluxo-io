package fluxo.io.rad

import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests for [RandomAccessFileRadAccessor].
 */
@RunWith(Parameterized::class)
internal class RandomAccessDataRafTest(
    factory: (File) -> RandomAccessData,
) : AbstractRandomAccessDataTest(factory) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data() = arrayOf<(File) -> RandomAccessData>(
            { RandomAccessFileRadAccessor(it) },
            { RandomAccessFileRadAccessor(RandomAccessFile(it, "rw")) },
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
        assertEmptyRad(RandomAccessFileRadAccessor(emptyFile))
        assertEmptyRad(RandomAccessFileRadAccessor(emptyFile, 0, 0))

        assertEmptyRad(RandomAccessFileRadAccessor(tempFile, BYTES.size.toLong()))
        assertEmptyRad(RandomAccessFileRadAccessor(tempFile, BYTES.size.toLong(), 0L))

        assertIOB { RandomAccessFileRadAccessor(tempFile, -1, 0) }
        assertIOB { RandomAccessFileRadAccessor(tempFile, -1, 1) }
        assertIOB { RandomAccessFileRadAccessor(tempFile, 0, -1) }
        assertIOB { RandomAccessFileRadAccessor(tempFile, 1, -1) }
        assertIOB { RandomAccessFileRadAccessor(tempFile, BYTES.size + 1L) }
        assertIOB { RandomAccessFileRadAccessor(tempFile, BYTES.size.toLong(), 1L) }
        assertIOB { RandomAccessFileRadAccessor(tempFile, BYTES.size + 1L, 1L) }
        assertIOB { RandomAccessFileRadAccessor(tempFile, 0L, BYTES.size + 1L) }
    }
}
