package fluxo.io.internal

import fluxo.io.EOFException
import fluxo.io.IOException
import fluxo.io.rad.RandomAccessData
import fluxo.io.util.EMPTY_AUTO_CLOSEABLE_ARRAY
import fluxo.io.util.EMPTY_BYTE_ARRAY
import fluxo.io.util.calcLength
import fluxo.io.util.checkOffsetAndCount
import fluxo.io.util.checkPositionAndMaxLength
import kotlin.jvm.JvmField
import kotlin.math.min

@ThreadSafe
internal abstract class AccessorAwareRad<A : SharedDataAccessor>
internal constructor(
    @JvmField
    protected val access: A,

    @JvmField
    protected val offset: Long,

    final override val size: Long,

    owner: RadHandle?,
) : BasicRad(owner) {

    init {
        checkOffsetAndCount(access.size, offset, size)
    }

    final override fun view(position: Long, length: Long, owner: RadHandle?): RandomAccessData =
        view0(access, offset + position, length, owner)

    /** A new instance of the implementation over the same [access]; positions are global. */
    protected abstract fun view0(
        access: A,
        globalPosition: Long,
        length: Long,
        owner: RadHandle?,
    ): RandomAccessData

    final override fun acquireShared() {
        if (!access.tryRetain()) {
            throw IOException("RandomAccessData is already closed")
        }
    }

    final override fun releaseShared() = access.close()


    override fun readFrom0(position: Long, maxLength: Int): ByteArray {
        val srcLen = size
        checkPositionAndMaxLength(size = srcLen, position = position, maxLength = maxLength)
        val len = min(maxLength.toLong(), srcLen - position)
        if (len <= 0L) {
            return EMPTY_BYTE_ARRAY
        }
        val destLen = len.toInt()
        var pos = this.offset + position
        var offset = 0
        val bytes = ByteArray(destLen)
        while (true) {
            val read = access.read(bytes, pos, offset, destLen - offset)
            if (read < 0) {
                throw EOFException(
                    "Unexpected end of data at $pos, expected $srcLen bytes",
                )
            }
            offset += read
            if (offset == destLen) {
                return bytes
            }
            pos += read
        }
    }

    override fun read0(buffer: ByteArray, position: Long, offset: Int, maxLength: Int): Int {
        val len = calcLength(size, buffer, position, offset, maxLength)
        if (len <= 0) {
            return len
        }
        val pos = this.offset + position
        return access.read(buffer, pos, offset, len)
    }
}

/**
 * [AccessorAwareRad] for sources whose accessor is all there is (file descriptors, OS handles,
 * adapters): no per-implementation fast paths, so one class serves them all instead of an
 * empty subclass per platform.
 */
@ThreadSafe
internal class AccessorRad private constructor(
    access: SharedDataAccessor,
    offset: Long,
    size: Long,
    owner: RadHandle?,
) : AccessorAwareRad<SharedDataAccessor>(access, offset, size, owner) {

    /** A handle over all of [access]; it takes over the caller's ownership of [access]. */
    constructor(access: SharedDataAccessor) : this(access, 0L, access.size, owner = null)

    override fun view0(
        access: SharedDataAccessor,
        globalPosition: Long,
        length: Long,
        owner: RadHandle?,
    ): RandomAccessData = AccessorRad(access, globalPosition, length, owner)
}

/**
 * A [RandomAccessData] over any positional reader, with every rule of the core (lifetime,
 * slices, bounds, leases), for fluxo-io's own adapter modules, which cannot extend the internal
 * classes above from another module. [read] runs only inside a lease and is called only for
 * ranges inside [size]; it returns the bytes read (> 0) or -1 at the end. [close] runs once,
 * after the last handle closed and the last read returned.
 */
@InternalFluxoIoApi
public fun radOf(
    size: Long,
    close: () -> Unit,
    read: (bytes: ByteArray, position: Long, offset: Int, length: Int) -> Int,
): RandomAccessData = AccessorRad(LambdaAccess(size, close, read))

/** Not named `close`: inside the class that name is SharedCloseable.close(), not the lambda. */
private class LambdaAccess(
    override val size: Long,
    private val release: () -> Unit,
    private val reader: (ByteArray, Long, Int, Int) -> Int,
) : SharedDataAccessor(EMPTY_AUTO_CLOSEABLE_ARRAY) {

    override fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int =
        withLease { reader(bytes, position, offset, length) }

    override fun releaseApi() = release()
}
