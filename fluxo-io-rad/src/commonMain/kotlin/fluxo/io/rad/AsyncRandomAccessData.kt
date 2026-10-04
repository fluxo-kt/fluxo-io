package fluxo.io.rad

import fluxo.io.IOException
import fluxo.io.internal.InternalFluxoIoApi
import fluxo.io.internal.ThreadSafe
import kotlin.coroutines.cancellation.CancellationException

/**
 * Read-only random access whose reads suspend instead of blocking the calling thread.
 *
 * Same lifetime model as [RandomAccessData]: a factory or [share] returns a handle that
 * must be closed once; a [slice] is a no-copy view that needs no close and reads only
 * while its handle is open. A closed handle (and every slice of it) throws [IOException].
 *
 * Get one from any [RandomAccessData] with [asAsync]. A read already started cannot be
 * aborted (no OS or runtime offers that for positional file reads); cancellation takes
 * effect when the read returns.
 */
@ThreadSafe
@SubclassOptInRequired(InternalFluxoIoApi::class)
public interface AsyncRandomAccessData : AutoCloseable {

    /** The size of the data, in bytes. */
    public val size: Long

    /**
     * Returns a view of `[position, position + length)` of this data. No data is copied.
     * See [RandomAccessData.slice].
     *
     * @throws IndexOutOfBoundsException if the [position] or [length] is invalid
     * @throws IOException if this data is already closed
     */
    public fun slice(position: Long, length: Long = size - position): AsyncRandomAccessData

    /**
     * Returns a new handle that keeps the data open on its own; close it once.
     * See [RandomAccessData.share].
     *
     * @throws IOException if this data is already closed
     */
    public fun share(): AsyncRandomAccessData

    /**
     * Reads up to [maxLength] bytes starting at [position] into [buffer] at [offset].
     *
     * @return the number of bytes read, or `-1` if [position] is at or past the end
     * @throws IOException if the data can't be read
     * @throws IndexOutOfBoundsException if the [position], [offset] or [maxLength] are invalid
     */
    @Throws(IOException::class, CancellationException::class)
    public suspend fun read(
        buffer: ByteArray,
        position: Long = 0L,
        offset: Int = 0,
        maxLength: Int = Int.MAX_VALUE,
    ): Int

    /**
     * Reads as many bytes as available up to [maxLength], unlike [read], which may return
     * fewer.
     *
     * @return the number of bytes read, or `-1` if [position] is at or past the end
     * @throws IOException if the data can't be read
     * @throws IndexOutOfBoundsException if the [position], [offset] or [maxLength] are invalid
     */
    @Throws(IOException::class, CancellationException::class)
    public suspend fun readFully(
        buffer: ByteArray,
        position: Long = 0L,
        offset: Int = 0,
        maxLength: Int = Int.MAX_VALUE,
    ): Int

    /** Closes this handle; idempotent. Does nothing on a [slice]. */
    override fun close()
}
