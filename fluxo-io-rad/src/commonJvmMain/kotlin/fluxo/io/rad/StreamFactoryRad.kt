package fluxo.io.rad

import fluxo.io.Closeable
import fluxo.io.EOFException
import fluxo.io.IOException
import fluxo.io.internal.AccessorAwareRad
import fluxo.io.internal.SharedDataAccessor
import fluxo.io.rad.StreamFactoryRad.StreamFactory
import fluxo.io.rad.StreamFactoryRad.StreamFactoryAccess
import fluxo.io.util.EMPTY_AUTO_CLOSEABLE_ARRAY
import java.io.DataInput
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.util.TreeMap
import javax.annotation.concurrent.ThreadSafe

// FIXME: Use own BufferedInputStream heir to gain better random access performance?

/**
 * [RandomAccessData] implementation backed by a [StreamFactory] ([InputStream], [DataInput], etc.).
 * It provides random data access by creating a dynamic pool of data streams.
 *
 * @param access provides access to the underlying data
 * @param offset the offset of the section
 * @param size the length of the section
 *
 * @see org.springframework.boot.loader.data.RandomAccessDataFile
 */
@ThreadSafe
@Suppress("KDocUnresolvedReference")
internal class StreamFactoryRad
private constructor(access: StreamFactoryAccess, offset: Long, size: Long) :
    AccessorAwareRad<StreamFactoryAccess>(access, offset, size) {

    constructor(
        factory: StreamFactory<*>,
        offset: Long,
        size: Long,
        maxPoolSize: Int,
    ) : this(StreamFactoryAccess(factory, maxPoolSize), offset, size)


    override fun getSubsection0(
        access: StreamFactoryAccess, globalPosition: Long, length: Long,
    ) = StreamFactoryRad(access, globalPosition, length)


    internal class StreamFactoryAccess(
        private val factory: StreamFactory<*>,
        private val maxPoolSize: Int,
    ) : SharedDataAccessor(resources = EMPTY_AUTO_CLOSEABLE_ARRAY) {

        /**
         * Pool of opened streams.
         * Also, used as a lock for synchronization.
         *
         * Key: the current position of the stream in the source data.
         */
        private val pool = TreeMap<Long, PooledStream<*>>()

        override val size: Long get() = factory.size

        override fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int {
            // Try to find a suitable stream in the pool or open new.
            // Use TreeMap thread safely.
            val pool = pool
            var streamOffset = 0L
            val stream = synchronized(pool) b@{
                val entry = pool.floorEntry(position)
                    ?: return@b null
                val key = entry.key
                streamOffset = key!!
                pool.remove(key)
                entry.value
            } ?: factory()

            val read: Int
            var closeStream = true
            try {
                // Skip some part of the InputStream
                stream.skipFully(position - streamOffset)

                // Read part
                read = stream.read(bytes, offset, length)

                // Save remaining back to the pool (if not at the end yet)
                if (read >= 0) {
                    val srcLen = size
                    val newPosition = position + read
                    if (newPosition < srcLen) {
                        synchronized(pool) {
                            val prev = pool.put(newPosition, stream)
                            closeStream = false

                            // Close another stream with exactly same position (if exists).
                            // Can't have two streams with the same position in the pool.
                            prev?.close()

                            // Don't allow pool to overgrow
                            while (pool.size > maxPoolSize) {
                                pool.pollLastEntry()?.value?.close()
                            }
                        }
                    }
                }
            } finally {
                if (closeStream) {
                    stream.close()
                }
            }

            return read
        }

        override fun onSharedClose() {
            val pool = pool
            synchronized(pool) {
                var t: Throwable? = null
                val iterator = pool.values.iterator()
                for (stream in iterator) {
                    iterator.remove()
                    try {
                        stream.close()
                    } catch (e: Throwable) {
                        t?.let { e.addSuppressed(it) }
                        t = e
                    }
                }
                t?.let { throw it }
            }
        }
    }


    // region PooledStream

    internal abstract class PooledStream<T>(
        @JvmField
        protected val api: T,
    ) : Closeable {

        /**
         * Reads up to the [length] bytes of data.
         *
         * @param buffer the buffer into which bytes are to be transferred
         * @param offset the start offset in [buffer] at which the data is written
         * @param length the maximum number of bytes to be read
         *
         * @return number of bytes read
         *  or `-1` if the given position is greater than or equal to the data size.
         *
         * @throws IOException if the data can't be read
         * @throws IndexOutOfBoundsException if the [offset] or [length] are invalid
         *
         * @see java.io.InputStream.read
         * @see java.io.RandomAccessFile.read
         * @see java.nio.channels.FileChannel.read
         */
        @Throws(IOException::class)
        abstract fun read(buffer: ByteArray, offset: Int, length: Int): Int

        /**
         * Skips over and discards [length] bytes of data from this stream.
         * The [skip] method may, for a variety of reasons, end up skipping over some smaller
         * number of bytes, possibly 0.
         *
         * @param length the number of bytes to be skipped
         *
         * @return the actual number of bytes skipped, possibly 0
         *
         * @throws IOException if an I/O error occurs.
         *
         * @see java.io.InputStream.skip
         * @see java.io.RandomAccessFile.skipBytes
         * @see skipFully
         */
        @Throws(IOException::class)
        abstract fun skip(length: Long): Long

        /**
         * Skips over and discards exactly [length] bytes of data from this stream.
         *
         * @param length the number of bytes to be skipped. Should be positive.
         *
         * @throws IOException if an I/O error occurs, or the end of stream reached.
         *
         * @see skip
         * @see java.io.InputStream.skip
         * @see java.io.RandomAccessFile.skipBytes
         */
        @Throws(IOException::class)
        open fun skipFully(length: Long) {
            check(length >= 0L) { "Length must be positive: $length" }
            var idles = 0
            var toSkip: Long = length
            while (toSkip > 0L) {
                val skipped = skip(toSkip)
                if (skipped > 0L) {
                    toSkip -= skipped
                    idles = 0
                } else if (++idles >= @Suppress("MagicNumber") 3) {
                    throw EOFException("Can't skip enough bytes ($length). 3 idle skips")
                }
            }
        }
    }

    private class PooledInputStream(api: InputStream) : PooledStream<InputStream>(api) {

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            api.read(buffer, offset, length)

        override fun skip(length: Long): Long =
            api.skip(length)

        override fun close() {
            api.close()
        }
    }

    private class PooledDataInput(api: DataInput) : PooledStream<DataInput>(api) {

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            api.readFully(buffer, offset, length)
            return length
        }

        override fun skip(length: Long): Long {
            if (length <= 0) {
                return 0L
            }
            val len = if (length >= Int.MAX_VALUE) Int.MAX_VALUE else length.toInt()
            return api.skipBytes(len).toLong()
        }

        override fun close() {
            (api as? AutoCloseable)?.close()
        }
    }

    private class PooledByteChannel(api: ReadableByteChannel) :
        PooledStream<ReadableByteChannel>(api) {

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            api.read(ByteBuffer.wrap(buffer, offset, length))

        override fun skip(length: Long): Long {
            if (length <= 0) {
                return 0L
            }
            val len = if (length >= DEFAULT_BUFFER_SIZE) DEFAULT_BUFFER_SIZE else length.toInt()
            return read(ByteArray(len), 0, len).toLong()
        }

        override fun close() {
            api.close()
        }
    }

    // endregion PooledStream


    // region StreamFactory

    internal interface StreamFactory<T> {

        /**
         * The size of the data stream in bytes.
         * Not necessarily the full size of the underlying data source.
         */
        val size: Long

        /**
         * Opens new [PooledStream]
         */
        @Throws(IOException::class)
        operator fun invoke(): PooledStream<T>
    }

    internal class InputStreamFactory(
        override val size: Long,
        private val factory: () -> InputStream,
    ) : StreamFactory<InputStream> {

        override fun invoke(): PooledStream<InputStream> = PooledInputStream(factory())
    }

    internal class DataInputFactory(
        override val size: Long,
        private val factory: () -> DataInput,
    ) : StreamFactory<DataInput> {

        override fun invoke(): PooledStream<DataInput> = PooledDataInput(factory())
    }

    internal class ByteChannelFactory(
        override val size: Long,
        private val factory: () -> ReadableByteChannel,
    ) : StreamFactory<ReadableByteChannel> {

        override fun invoke(): PooledStream<ReadableByteChannel> = PooledByteChannel(factory())
    }

    // endregion StreamFactory
}
