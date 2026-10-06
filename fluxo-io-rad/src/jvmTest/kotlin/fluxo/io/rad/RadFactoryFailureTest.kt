package fluxo.io.rad

import com.sun.management.UnixOperatingSystemMXBean
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import org.junit.Assume.assumeTrue

/**
 * A factory that opens the file itself must close it when setup fails: the caller never gets a
 * handle, so nothing else can. Counted as open descriptors of this process (Unix only).
 */
internal class RadFactoryFailureTest {

    @Test
    @Suppress("DEPRECATION")
    fun fileFactoriesCloseTheFileWhenTheRangeIsInvalid() {
        val os = ManagementFactory.getOperatingSystemMXBean()
        assumeTrue(os is UnixOperatingSystemMXBean)
        os as UnixOperatingSystemMXBean
        val file = File.createTempFile("rad-factory", ".bin").apply { deleteOnExit() }
        file.writeBytes(ByteArray(16))
        val badOffset = 17L
        val factories = mapOf<String, () -> Any>(
            "FileChannel" to { RadFileChannelAccessor(file, offset = badOffset) },
            "SeekableByteChannel" to { RadSeekableByteChannelAccessor(file, offset = badOffset) },
            "RandomAccessFile" to { RandomAccessFileRadAccessor(file, offset = badOffset) },
            "ByteBuffer" to { RadByteBufferAccessor(file, offset = badOffset) },
            "AsyncFileChannel" to { RadAsyncFileChannelAccessor(file, offset = badOffset) },
        )
        val leaked = factories.filter { (name, open) ->
            val before = os.openFileDescriptorCount
            assertFails(name) { open() }
            os.openFileDescriptorCount != before
        }.keys
        assertEquals(emptySet(), leaked, "these factories leaked the file they opened")
        file.delete()
    }
}
