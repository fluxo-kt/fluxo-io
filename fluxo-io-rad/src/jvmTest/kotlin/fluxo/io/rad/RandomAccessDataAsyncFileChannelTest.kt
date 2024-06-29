package fluxo.io.rad

import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException
import java.nio.channels.AsynchronousFileChannel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

/**
 * Tests for [RadAsyncFileChannelAccessor].
 */
@RequiresApi(26)
@Suppress("DEPRECATION", "UseSdkSuppress")
@RunWith(Parameterized::class)
internal class RandomAccessDataAsyncFileChannelTest(
    factory: (File) -> RandomAccessData,
) : AbstractRandomAccessDataTest(factory) {

    companion object {
        @JvmStatic
        @Parameters
        fun data() = arrayOf<(File) -> RandomAccessData>(
            { RadAsyncFileChannelAccessor(it) },
            { RadAsyncFileChannelAccessor(AsynchronousFileChannel.open(it.toPath())) },
        ).asList()
    }


    @Test
    fun fileExists() {
        assertFailsWith<IOException> {
            factory(File("/does/not/exist"))
        }
    }

    @Test
    fun creationBoundaries() {
        val emptyFile = File.createTempFile("tempFile123", "tmp")
        assertEmptyRad(RadAsyncFileChannelAccessor(emptyFile))
        assertEmptyRad(RadAsyncFileChannelAccessor(emptyFile, 0, 0))

        assertEmptyRad(RadAsyncFileChannelAccessor(tempFile, BYTES.size.toLong()))
        assertEmptyRad(RadAsyncFileChannelAccessor(tempFile, BYTES.size.toLong(), 0L))

        assertIOB { RadAsyncFileChannelAccessor(tempFile, -1, 0) }
        assertIOB { RadAsyncFileChannelAccessor(tempFile, -1, 1) }
        assertIOB { RadAsyncFileChannelAccessor(tempFile, 0, -1) }
        assertIOB { RadAsyncFileChannelAccessor(tempFile, 1, -1) }
        assertIOB { RadAsyncFileChannelAccessor(tempFile, BYTES.size + 1L) }
        assertIOB { RadAsyncFileChannelAccessor(tempFile, BYTES.size.toLong(), 1L) }
        assertIOB { RadAsyncFileChannelAccessor(tempFile, BYTES.size + 1L, 1L) }
        assertIOB { RadAsyncFileChannelAccessor(tempFile, 0L, BYTES.size + 1L) }
    }
}
