package fluxo.io.internal

import fluxo.io.EOFException
import fluxo.io.IOException
import fluxo.io.rad.RandomAccessData
import fluxo.io.util.EMPTY_BYTE_ARRAY
import fluxo.io.util.calcLength
import fluxo.io.util.checkOffsetAndCount
import fluxo.io.util.checkPositionAndMaxLength
import kotlin.math.min
import kotlinx.atomicfu.atomic

@ThreadSafe
internal abstract class AccessorAwareRad<A : SharedDataAccessor>
internal constructor(
    @JvmField
    protected val access: A,

    @JvmField
    protected val offset: Long,

    final override val size: Long,
) : BasicRad() {

    init {
        checkOffsetAndCount(access.size, offset, size)
    }

    override fun subsection(position: Long, length: Long): RandomAccessData {
        checkOffsetAndCount(size, position, length)
        access.retain()
        return getSubsection0(access, offset + position, length)
    }

    protected abstract fun getSubsection0(
        access: A,
        globalPosition: Long,
        length: Long,
    ): RandomAccessData


    @Throws(IOException::class)
    override fun readFrom(position: Long, maxLength: Int): ByteArray {
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

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, position: Long, offset: Int, maxLength: Int): Int {
        val len = calcLength(size, buffer, position, offset, maxLength)
        if (len <= 0) {
            return len
        }
        val pos = this.offset + position
        return access.read(buffer, pos, offset, len)
    }


    /**
     * Guards [close] for one-shot release. Each holder — the root or any [subsection] —
     * owns exactly one retain on the shared [access], so it must release at most once.
     */
    private val closed = atomic(false)

    /**
     * Idempotent, per the [java.io.Closeable] contract ("if already closed, invoking this
     * method has no effect"). A repeated close must NOT decrement the shared refcount again:
     * doing so would prematurely free the resource still in use by the parent or sibling
     * subsections — for a memory-mapped buffer that is a use-after-free that crashes the JVM,
     * not merely an [IOException]. Final so no subclass can reintroduce the unguarded path.
     */
    final override fun close() {
        if (closed.compareAndSet(expect = false, update = true)) {
            access.close()
        }
    }
}
