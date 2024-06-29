package fluxo.io.rad

import java.io.DataInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests for stream-factory RandomAccessData implementations.
 */
@RunWith(Parameterized::class)
internal class RandomAccessDataStreamFactoryTest(
    factory: (File) -> RandomAccessData,
) : AbstractRandomAccessDataTest(factory) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data() = arrayOf<(File) -> RandomAccessData>(
            { StreamFactoryRadAccessor(it) },
            { StreamFactoryRadAccessor(it.length()) { it.inputStream() } },
            { StreamFactoryRadAccessor(BYTES.size.toLong()) { BYTES.inputStream() } },

            { DataInputFactoryRadAccessor(it.length()) { RandomAccessFile(it, "r") } },
            { DataInputFactoryRadAccessor(it.length()) { DataInputStream(it.inputStream()) } },

            { ByteChannelFactoryRadAccessor(it.length()) { it.inputStream().channel } },
            { ByteChannelFactoryRadAccessor(it.length()) { RandomAccessFile(it, "r").channel } },
        ).asList()
    }


    @Test
    fun fileExists() {
        assertFailsWith<FileNotFoundException> {
            factory(File("/does/not/exist"))
        }
    }
}
