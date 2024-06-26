package fluxo.io.internal

import fluxo.io.EOFException
import fluxo.io.IOException
import kotlin.math.min

@ThreadSafe
internal abstract class AccessorAwareRad<A : SharedDataAccessor, R : AccessorAwareRad<A, R>>
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

    override fun subsection(position: Long, length: Long): R {
        checkOffsetAndCount(size, position, length)
        access.retain()
        return getSubsection0(access, offset + position, length)
    }

    protected abstract fun getSubsection0(access: A, globalPosition: Long, length: Long): R


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


    override fun close() {
        access.close()
    }
}
