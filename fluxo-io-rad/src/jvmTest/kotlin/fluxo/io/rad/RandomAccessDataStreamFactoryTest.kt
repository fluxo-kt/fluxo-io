package fluxo.io.rad

import java.io.DataInputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.Channels
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
            { file ->
                val bytes = file.readBytes()
                StreamFactoryRadAccessor(bytes.size.toLong()) { bytes.inputStream() }
            },

            { DataInputFactoryRadAccessor(it.length()) { RandomAccessFile(it, "r") } },
            { DataInputFactoryRadAccessor(it.length()) { DataInputStream(it.inputStream()) } },

            { ByteChannelFactoryRadAccessor(it.length()) { it.inputStream().channel } },
            { ByteChannelFactoryRadAccessor(it.length()) { RandomAccessFile(it, "r").channel } },

            // The data behind a prefix: every read goes through a non-zero section offset.
            { file ->
                val bytes = PREFIX + file.readBytes()
                StreamFactoryRadAccessor(bytes.size.toLong(), PREFIX.size.toLong()) {
                    bytes.inputStream()
                }
            },
            { file ->
                val bytes = PREFIX + file.readBytes()
                DataInputFactoryRadAccessor(bytes.size.toLong(), PREFIX.size.toLong()) {
                    DataInputStream(bytes.inputStream())
                }
            },
            { file ->
                val bytes = PREFIX + file.readBytes()
                ByteChannelFactoryRadAccessor(bytes.size.toLong(), PREFIX.size.toLong()) {
                    Channels.newChannel(bytes.inputStream())
                }
            },
        ).asList()

        private val PREFIX = ByteArray(7) { -1 }
    }
}
