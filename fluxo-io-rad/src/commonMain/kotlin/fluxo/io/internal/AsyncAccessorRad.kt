package fluxo.io.internal

import fluxo.io.IOException
import fluxo.io.rad.AsyncRandomAccessData
import fluxo.io.util.calcLength
import fluxo.io.util.checkOffsetAndCount
import fluxo.io.util.readFully0
import kotlinx.atomicfu.atomic

/**
 * The single [AsyncRandomAccessData] implementation for natively non-blocking sources: a source
 * supplies only a [SharedAsyncDataAccessor] (its positional read and its release), and every
 * rule of the interface lives here once, so no source can get lifetime or bounds wrong.
 *
 * Lifetime is [RadHandle]'s, which cannot be reused directly because it implements the
 * blocking interface: a handle (`owner == this`, from the constructor or [share]) owns one
 * retain of [access] and must be closed once; a [slice] owns nothing and reads only while its
 * handle is open, even when other handles keep the data alive.
 */
@ThreadSafe
internal class AsyncAccessorRad private constructor(
    private val access: SharedAsyncDataAccessor,
    private val offset: Long,
    override val size: Long,
    owner: AsyncAccessorRad?,
) : AsyncRandomAccessData {

    /** A handle over all of [access]; it takes over the caller's ownership of [access]. */
    constructor(access: SharedAsyncDataAccessor) : this(access, 0L, access.size, owner = null)

    private val owner: AsyncAccessorRad = owner ?: this

    /** Meaningful on handles only; a slice reads its owner's flag. */
    private val closed = atomic(false)

    init {
        checkOffsetAndCount(access.size, offset, size)
    }

    override fun slice(position: Long, length: Long): AsyncRandomAccessData {
        checkOffsetAndCount(size, position, length)
        ensureOpen()
        return AsyncAccessorRad(access, offset + position, length, owner)
    }

    override fun share(): AsyncRandomAccessData {
        ensureOpen()
        if (!access.tryRetain()) {
            throw IOException("RandomAccessData is already closed")
        }
        return AsyncAccessorRad(access, offset, size, owner = null)
    }

    override suspend fun read(buffer: ByteArray, position: Long, offset: Int, maxLength: Int): Int {
        ensureOpen()
        val len = calcLength(size, buffer, position, offset, maxLength)
        if (len <= 0) {
            return len
        }
        return access.read(buffer, this.offset + position, offset, len)
    }

    override suspend fun readFully(
        buffer: ByteArray,
        position: Long,
        offset: Int,
        maxLength: Int,
    ): Int {
        ensureOpen()
        return readFully0(size, buffer, position, offset, maxLength) { pos, offs, len ->
            access.read(buffer, this.offset + pos, offs, len)
        }
    }

    /** Idempotent per handle; a second close must not give back the ownership twice. */
    override fun close() {
        if (owner === this && closed.compareAndSet(expect = false, update = true)) {
            access.close()
        }
    }

    private fun ensureOpen() {
        if (owner.closed.value) {
            throw IOException(CLOSED_HANDLE_MESSAGE)
        }
    }
}
