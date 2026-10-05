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
 * A factory returns a *handle*: close it when finished to release the underlying resource.
 * [slice] gives a no-copy view of a range that needs no close and reads only while its
 * handle is open; [share] gives another handle that keeps the data open on its own.
 * A closed handle (and every slice of it) throws [IOException] on read.
 *
 * All implementations are thread-safe!
 * For JVM and Android, it also implements [java.io.Closeable] interface.
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
     * Returns a view of `[position, position + length)` of this data. No data is copied.
     *
     * The view needs no [close][AutoCloseable.close] (closing it does nothing) and reads
     * only while the handle it came from is open: afterwards every read throws
     * [IOException]. To keep a range open independently, use `slice(…).share()`.
     *
     * @param position the start of the range, relative to this data
     * @param length the length of the range
     *
     * @throws IndexOutOfBoundsException if the [position] or [length] is invalid
     * @throws IOException if this data is already closed
     */
    public fun slice(
        position: Long,
        length: Long = size - position,
    ): RandomAccessData

    /**
     * Returns a new handle over the same range that keeps the underlying data open on its
     * own: it stays readable after this one is closed, and must itself be closed once.
     * The data is released when the last handle is closed.
     *
     * @throws IOException if this data is already closed
     */
    public fun share(): RandomAccessData

    /**
     * Returns a new handle for a specific range of this data, which must be closed.
     *
     * @throws IndexOutOfBoundsException if the [position] or [length] is invalid
     */
    @Deprecated(
        "Use slice(position, length).share(): the same owned sub-range, made explicit.",
        ReplaceWith("slice(position, length).share()"),
        DeprecationLevel.ERROR,
    )
    public fun subsection(
        position: Long,
        length: Long = size - position,
    ): RandomAccessData


    /**
     * Reads all bytes from the underlying data and returns it as a byte array.
     *
     * @return the data
     *
     * @throws IOException if the data can't be read.
     * @throws ArithmeticException if length of the data exceeds [Int.MAX_VALUE] and data
     *  can't be read into a single byte array.
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
     * @throws IOException if the data can't be read
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
     * @return number of bytes read
     *  or `-1` if the given position is greater than or equal to the data size.
     *
     * @throws IOException if the data can't be read
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
     * @return number of bytes read
     *  or `-1` if the given position is greater than or equal to the data size.
     *
     * @throws IOException if the data can't be read
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
     * DOESN'T switch to the IO dispatcher by itself.
     * Caller SHOULD take care of it.
     *
     * Can be blocking if the implementation doesn't support non-blocking reads!
     *
     * @param buffer the buffer into which bytes are to be transferred
     * @param position the position from which data should be read
     * @param offset the start offset in [buffer] at which the data is written
     * @param maxLength the maximum number of bytes to be read
     *
     * @return number of bytes read
     *  or `-1` if the given position is greater than or equal to the data size.
     *
     * @throws IOException if the data can't be read
     * @throws IndexOutOfBoundsException if the [position], [offset] or [maxLength] are invalid
     * @throws CancellationException
     *
     * @see java.nio.channels.AsynchronousFileChannel.read
     * @see java.nio.channels.FileChannel.read
     * @see java.io.RandomAccessFile.read
     */
    @Deprecated(
        "Suspends but blocks the calling thread for the whole read. Use asAsync(dispatcher), " +
            "e.g. rad.asAsync(Dispatchers.IO).read(…).",
        level = DeprecationLevel.ERROR,
    )
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
     * DOESN'T switch to the IO dispatcher by itself.
     * Caller SHOULD take care of it.
     *
     * Can be blocking if the implementation doesn't support non-blocking reads!
     *
     * @param buffer the buffer into which bytes are to be transferred
     * @param position the position from which data should be read
     * @param offset the start offset in [buffer] at which the data is written
     * @param maxLength the maximum number of bytes to be read
     *
     * @return number of bytes read
     *  or `-1` if the given position is greater than or equal to the data size.
     *
     * @throws IOException if the data can't be read
     * @throws IndexOutOfBoundsException if the [position], [offset] or [maxLength] are invalid
     * @throws CancellationException
     *
     * @see java.io.RandomAccessFile.readFully
     * @see java.io.DataInput.readFully
     * @see java.io.InputStream.readNBytes
     */
    @Deprecated(
        "Suspends but blocks the calling thread for the whole read. Use asAsync(dispatcher), " +
            "e.g. rad.asAsync(Dispatchers.IO).read(…).",
        level = DeprecationLevel.ERROR,
    )
    public suspend fun readFullyAsync(
        buffer: ByteArray,
        position: Long = 0L,
        offset: Int = 0,
        maxLength: Int = Int.MAX_VALUE,
    ): Int

    /**
     * Home of the `RandomAccessData.open(…)` factories, so Kotlin and Java (via `@JvmStatic`)
     * spell the obvious entry point the same way. Platform-only overloads are added as
     * members of a platform's `actual` companion or as extensions on it.
     */
    public companion object {
        /**
         * Opens the file at [path] for random-access reads, on every target. Returns a handle:
         * close it once when finished; reads are thread-safe.
         *
         * Each platform uses its positional read: JVM/Android `FileChannel`, POSIX `pread`,
         * Windows `ReadFile` at an offset, Node/Bun/Deno `fs.readSync`, WASI `fd_pread` (the path
         * must lie under a directory the host preopened). In a browser there is no file
         * system: read a `Blob` with `AsyncRandomAccessData.open(blob)`.
         *
         * @throws IOException if the file cannot be opened, is a directory, or this runtime
         *  has no file system
         */
        @Blocking
        @Throws(IOException::class)
        public fun open(path: String): RandomAccessData
    }
}
