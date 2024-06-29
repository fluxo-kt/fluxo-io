package fluxo.io.rad

import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests for [RadSeekableByteChannelAccessor].
 */
@RequiresApi(24)
@Suppress("UseSdkSuppress")
@RunWith(Parameterized::class)
internal class RadSeekableByteChannelAccessorTest(
    factory: (File) -> RandomAccessData,
) : AbstractRandomAccessDataTest(factory) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data() = arrayOf<(File) -> RandomAccessData>(
            { RadSeekableByteChannelAccessor(it) },
            { RadSeekableByteChannelAccessor(it.inputStream()) },
            { RadSeekableByteChannelAccessor(it.inputStream().channel) },
            { RadSeekableByteChannelAccessor(it.inputStream().fd) },
            { RadSeekableByteChannelAccessor(RandomAccessFile(it, "r").channel) },
            { RadSeekableByteChannelAccessor(RandomAccessFile(it, "r").fd) },
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
        assertEmptyRad(RadSeekableByteChannelAccessor(emptyFile))
        assertEmptyRad(RadSeekableByteChannelAccessor(emptyFile, 0, 0))

        assertEmptyRad(RadSeekableByteChannelAccessor(tempFile, BYTES.size.toLong()))
        assertEmptyRad(RadSeekableByteChannelAccessor(tempFile, BYTES.size.toLong(), 0L))

        assertIOB { RadSeekableByteChannelAccessor(tempFile, -1, 0) }
        assertIOB { RadSeekableByteChannelAccessor(tempFile, -1, 1) }
        assertIOB { RadSeekableByteChannelAccessor(tempFile, 0, -2) }
        assertIOB { RadSeekableByteChannelAccessor(tempFile, 1, -2) }
        assertIOB { RadSeekableByteChannelAccessor(tempFile, BYTES.size + 1L) }
        assertIOB { RadSeekableByteChannelAccessor(tempFile, BYTES.size.toLong(), 1L) }
        assertIOB { RadSeekableByteChannelAccessor(tempFile, BYTES.size + 1L, 1L) }
        assertIOB { RadSeekableByteChannelAccessor(tempFile, 0L, BYTES.size + 1L) }
    }
}
