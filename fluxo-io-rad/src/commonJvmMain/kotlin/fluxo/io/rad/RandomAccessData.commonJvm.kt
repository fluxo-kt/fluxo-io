@file:Suppress("KDocUnresolvedReference")

package fluxo.io.rad

import fluxo.io.IOException
import fluxo.io.internal.Blocking
import fluxo.io.internal.InternalFluxoIoApi
import fluxo.io.internal.ThreadSafe
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

@ThreadSafe
@SubclassOptInRequired(InternalFluxoIoApi::class)
public actual interface RandomAccessData : Closeable, AutoCloseable {

    public actual val size: Long


    /**
     * Returns an [InputStream] that can be used to read the underlying data. The
     * caller is responsible for closing the stream when it is finished.
     *
     * @return a new input stream that can be used to read the underlying data.
     * @throws IOException if the stream cannot be opened
     */
    @Throws(IOException::class)
    public fun getInputStream(): InputStream

    public actual fun getSubsection(position: Long, length: Long): RandomAccessData


    @Blocking
    @Throws(IOException::class)
    public actual fun readAllBytes(): ByteArray

    @Blocking
    @Throws(IOException::class)
    public actual fun readFrom(position: Long, maxLength: Int): ByteArray


    @Blocking
    @Throws(IOException::class)
    public actual fun read(
        buffer: ByteArray,
        position: Long,
        offset: Int,
        maxLength: Int,
    ): Int

    @Blocking
    @Throws(IOException::class)
    public actual fun readFully(
        buffer: ByteArray,
        position: Long,
        offset: Int,
        maxLength: Int,
    ): Int

    /**
     * Reads a single byte of data.
     * The byte is returned as an integer in the range 0 to 255 (`0x00..0x0ff`).
     * This method blocks if no input is yet available. Method behaves in exactly the same way as
     * the [InputStream.read] method.
     *
     * Note that this method can be highly inefficient for some implementations!
     * If multiple bytes are to be read, it is generally
     * more efficient to use [read] or [readFully].
     *
     * @param position the position from which data should be read
     *
     * @return the next byte of data as an integer, or `-1` if the given position is
     *  greater than or equal to the data size at the time that the read is attempted.
     *
     * @throws IOException if the data cannot be read
     * @throws IndexOutOfBoundsException if the [position] is invalid
     *
     * @see java.io.InputStream.read
     * @see java.io.RandomAccessFile.read
     */
    @Blocking
    @Throws(IOException::class)
    public fun readByteAt(position: Long): Int


    public actual suspend fun readAsync(
        buffer: ByteArray,
        position: Long,
        offset: Int,
        maxLength: Int,
    ): Int

    public actual suspend fun readFullyAsync(
        buffer: ByteArray,
        position: Long,
        offset: Int,
        maxLength: Int,
    ): Int


    /**
     * Reads a sequence of bytes of data starting at the given [position].
     * If the given [position] is greater than the data size at the time that the read is
     * attempted then no bytes are read.
     *
     * @param buffer the buffer into which bytes are to be transferred
     * @param position the position from which data should be read
     *
     * @return number of bytes read or `-1` if the given position is
     *  greater than or equal to the data size at the time that the read is attempted
     *
     * @throws IOException if the data cannot be read
     * @throws IndexOutOfBoundsException if the [position] is invalid
     *
     * @see java.nio.channels.FileChannel.read
     * @see java.nio.channels.AsynchronousByteChannel.read
     */
    @Blocking
    @Throws(IOException::class)
    public fun read(buffer: ByteBuffer, position: Long): Int

    /**
     * Reads a sequence of bytes of data starting at the given [position].
     * If the given [position] is greater than the data size at the time that the read is attempted
     * then no bytes are read.
     *
     * @param buffer the buffer into which bytes are to be transferred
     * @param position the position from which data should be read
     *
     * @return number of bytes read or `-1` if the given position is
     *  greater than or equal to the data size at the time that the read is attempted.
     *
     * @throws IOException if the data cannot be read
     * @throws IndexOutOfBoundsException if the [position] is invalid
     *
     * @see java.nio.channels.AsynchronousFileChannel.read
     * @see java.nio.channels.AsynchronousByteChannel.read
     */
    public suspend fun readAsync(buffer: ByteBuffer, position: Long): Int


    /**
     * Transfers all bytes to the given [WritableByteChannel].
     *
     * This method is potentially much more efficient than a simple loop that reads from this data
     * and writes to the target [channel]. Many operating systems can transfer bytes directly from
     * the filesystem cache to the target channel without actually copying them.
     *
     * @param channel the target channel
     * @param bufferSize size of the buffer to use, if applicable
     * @param directBuffer whether to use direct buffer, if applicable
     *
     * @returns The number of bytes, possibly zero, that were actually transferred.
     *
     * @throws NonWritableChannelException If the target [channel] was not opened for writing
     * @throws ClosedChannelException If either this data or the target [channel] is closed
     * @throws AsynchronousCloseException If another thread closes either channel while the
     *   transfer is in progress.
     * @throws ClosedByInterruptException If another thread interrupts the current thread while
     *  the transfer is in progress, thereby closing both and setting the current thread's
     *  interrupt status.
     * @throws IOException If some other I/O error occurs
     *
     * @see java.nio.channels.FileChannel.transferTo
     * @see java.io.InputStream.transferTo
     */
    @Blocking
    @Throws(IOException::class)
    public fun transferTo(
        channel: WritableByteChannel,
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
        directBuffer: Boolean = true,
    ): Long

    /**
     * Transfers all bytes to the given [OutputStream].
     *
     * @param stream the target stream
     * @param bufferSize size of the buffer to use, if applicable
     *
     * @returns The number of bytes, possibly zero, that were actually transferred.
     *
     * @throws IOException If some other I/O error occurs
     *
     * @see kotlin.io.copyTo
     * @see java.io.InputStream.transferTo
     * @see java.io.InputStream.readNBytes
     * @see java.nio.channels.FileChannel.transferTo
     * @see azagroup.io.readBytesExact
     * @see azagroup.io.readBytesFully
     */
    @Blocking
    @Throws(IOException::class)
    public fun transferTo(stream: OutputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE): Long


    private companion object {
        private const val DEFAULT_BUFFER_SIZE = 128 * 1024
    }
}
