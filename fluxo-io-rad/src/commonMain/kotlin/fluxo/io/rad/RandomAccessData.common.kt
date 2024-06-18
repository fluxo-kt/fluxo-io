@file:Suppress("KDocUnresolvedReference")

package fluxo.io.rad

import fluxo.io.IOException
import fluxo.io.internal.Blocking
import fluxo.io.internal.InternalFluxoIoApi
import fluxo.io.internal.ThreadSafe
import kotlin.coroutines.cancellation.CancellationException

/**
 * Interface that provides read-only random access to some underlying data.
 * Implementations must allow concurrent reads in a thread-safe manner.
 *
 * See [RandomAccessDataBenchmark] findings if you want to choose implementation
 * rationally for your use case.
 *
 * All implementations should be thread-safe!
 *
 * @see org.springframework.boot.loader.data.RandomAccessData
 */
@ThreadSafe
@SubclassOptInRequired(InternalFluxoIoApi::class)
public expect interface RandomAccessData : AutoCloseable {

    /**
     * Returns the size of the data.
     */
    public val size: Long


    /**
     * Returns a new [RandomAccessData] for a specific subsection of this data.
     *
     * @param position the position of the subsection
     * @param length the length of the subsection
     *
     * @return the subsection data
     *
     * @throws IndexOutOfBoundsException if the [position] or [length] are invalid
     */
    public fun getSubsection(
        position: Long,
        length: Long = size - position,
    ): RandomAccessData


    /**
     * Reads all the data and returns it as a byte array.
     *
     * @return the data
     *
     * @throws IOException if the data cannot be read.
     *
     * @see java.io.InputStream.readAllBytes
     */
    @Blocking
    @Throws(IOException::class)
    public fun readAllBytes(): ByteArray

    /**
     * Reads up to the [maxLength] bytes of data starting at the given [position].
     *
     * @param position the position from which data should be read
     * @param maxLength the maximum number of bytes to be read
     *
     * @return a newly created array with data.
     *
     * @throws IOException if the data cannot be read
     * @throws IndexOutOfBoundsException if the [position] is invalid
     * @throws EOFException if offset plus length is greater than the length of the file
     * or subsection.
     *
     * @see org.springframework.boot.loader.data.RandomAccessData.read
     */
    @Blocking
    @Throws(IOException::class)
    public fun readFrom(position: Long, maxLength: Int = Int.MAX_VALUE): ByteArray


    /**
     * Reads up to the [maxLength] bytes of data starting at the given [position].
     *
     * @param buffer the buffer into which bytes are to be transferred
     * @param position the position from which data should be read
     * @param offset the start offset in [buffer] at which the data is written
     * @param maxLength the maximum number of bytes to be read
     *
     * @return number of bytes read or `-1` if the given position is
     *  greater than or equal to the data size at the time that the read is attempted.
     *
     * @throws IOException if the data cannot be read
     * @throws IndexOutOfBoundsException if the [position], [offset] or [maxLength] are invalid
     *
     * @see java.io.RandomAccessFile.read
     * @see java.nio.channels.FileChannel.read
     */
    @Blocking
    @Throws(IOException::class)
    public fun read(
        buffer: ByteArray,
        position: Long = 0L,
        offset: Int = 0,
        maxLength: Int = Int.MAX_VALUE,
    ): Int

    /**
     * Reads as much as possible up to the [maxLength] bytes from this file into the byte array,
     * starting at the given [position].
     *
     * @param buffer the buffer into which bytes are to be transferred
     * @param position the position from which data should be read
     * @param offset the start offset in [buffer] at which the data is written
     * @param maxLength the maximum number of bytes to be read
     *
     * @return number of bytes read or `-1` if the given position is
     *  greater than or equal to the data size at the time that the read is attempted.
     *
     * @throws IOException if the data cannot be read
     * @throws IndexOutOfBoundsException if the [position], [offset] or [maxLength] are invalid
     *
     * @see java.io.RandomAccessFile.readFully
     * @see java.io.DataInput.readFully
     * @see java.io.InputStream.readNBytes
     */
    @Blocking
    @Throws(IOException::class)
    public fun readFully(
        buffer: ByteArray,
        position: Long = 0L,
        offset: Int = 0,
        maxLength: Int = Int.MAX_VALUE,
    ): Int


    /**
     * Reads up to the [maxLength] bytes of data starting at the given [position]
     * asynchronously suspending until the operation is complete if possible.
     *
     * DOES NOT switch to the IO dispatcher by itself.
     * Caller SHOULD take care of it.
     *
     * Can be blocking if the implementation does not support non-blocking reads!
     *
     * @param buffer the buffer into which bytes are to be transferred
     * @param position the position from which data should be read
     * @param offset the start offset in [buffer] at which the data is written
     * @param maxLength the maximum number of bytes to be read
     *
     * @return number of bytes read or `-1` if the given position is
     *  greater than or equal to the data size at the time that the read is attempted.
     *
     * @throws IOException if the data cannot be read
     * @throws IndexOutOfBoundsException if the [position], [offset] or [maxLength] are invalid
     * @throws CancellationException
     *
     * @see java.nio.channels.AsynchronousFileChannel.read
     * @see java.nio.channels.FileChannel.read
     * @see java.io.RandomAccessFile.read
     */
    public suspend fun readAsync(
        buffer: ByteArray,
        position: Long = 0L,
        offset: Int = 0,
        maxLength: Int = Int.MAX_VALUE,
    ): Int

    /**
     * Reads as much as possible up to the [maxLength] bytes from this file into the byte array,
     * starting at the given [position] asynchronously suspending
     * until the operation is complete if possible.
     *
     * DOES NOT switch to the IO dispatcher by itself.
     * Caller SHOULD take care of it.
     *
     * Can be blocking if the implementation does not support non-blocking reads!
     *
     * @param buffer the buffer into which bytes are to be transferred
     * @param position the position from which data should be read
     * @param offset the start offset in [buffer] at which the data is written
     * @param maxLength the maximum number of bytes to be read
     *
     * @return number of bytes read or `-1` if the given position is
     *  greater than or equal to the data size at the time that the read is attempted.
     *
     * @throws IOException if the data cannot be read
     * @throws IndexOutOfBoundsException if the [position], [offset] or [maxLength] are invalid
     * @throws CancellationException
     *
     * @see java.io.RandomAccessFile.readFully
     * @see java.io.DataInput.readFully
     * @see java.io.InputStream.readNBytes
     */
    public suspend fun readFullyAsync(
        buffer: ByteArray,
        position: Long = 0L,
        offset: Int = 0,
        maxLength: Int = Int.MAX_VALUE,
    ): Int
}
