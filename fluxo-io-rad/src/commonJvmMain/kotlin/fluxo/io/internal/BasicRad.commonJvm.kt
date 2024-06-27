package fluxo.io.internal

import fluxo.io.IOException
import fluxo.io.nio.clearCompat
import fluxo.io.nio.flipCompat
import fluxo.io.nio.positionCompat
import fluxo.io.nio.releaseCompat
import fluxo.io.rad.InputStreamFromRad
import fluxo.io.rad.RadByteArrayAccessor
import fluxo.io.rad.RandomAccessData
import fluxo.io.util.readAllBytesImpl
import fluxo.io.util.readFullyAsyncImpl
import fluxo.io.util.readFullyImpl
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel
import kotlin.math.max
import kotlin.math.min

/**
 * Common logic for [RandomAccessData] implementations
 */
@ThreadSafe
@InternalFluxoIoApi
internal actual abstract class BasicRad : RandomAccessData {

    override fun asInputStream(): InputStream =
        InputStreamFromRad(this)


    @Blocking
    actual override fun readAllBytes(): ByteArray = readAllBytesImpl()

    @Blocking
    actual override fun readFully(
        buffer: ByteArray,
        position: Long,
        offset: Int,
        maxLength: Int,
    ): Int = readFullyImpl(buffer, position, offset, maxLength)


    @Blocking
    final override fun readByteAt(position: Long): Int {
        val srcLen = size
        return when {
            position >= srcLen -> -1
            position < 0L -> throw IndexOutOfBoundsException("srcPos=$position, srcLen=$srcLen")
            else -> readByteAt0(position)
        }
    }

    /**
     * Default naive inefficient implementation of [RadByteArrayAccessor.readByteAt].
     * No bounds check here!
     */
    @Suppress("MagicNumber")
    protected open fun readByteAt0(position: Long): Int =
        readFrom(position, maxLength = 1)[0].toInt() and 0xFF


    @Blocking
    actual override suspend fun readAsync(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int {
        @Suppress("BlockingMethodInNonBlockingContext")
        return read(buffer, position, offset, maxLength)
    }

    actual override suspend fun readFullyAsync(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int {
        return readFullyAsyncImpl(buffer, position, offset, maxLength)
    }


    @Blocking
    @Throws(IOException::class)
    override fun read(buffer: ByteBuffer, position: Long): Int {
        val srcLen = size
        if (position < 0L) {
            throw IndexOutOfBoundsException("srcPos=$position, srcLen=$srcLen")
        }
        if (position >= srcLen) {
            return -1
        }

        val pos = buffer.position()
        val destLen = buffer.limit() - pos

        // Use an existing array for HeapByteBuffer
        if (buffer.hasArray()) {
            val read = read(buffer.array(), position, pos, destLen)
            if (read > 0) {
                buffer.positionCompat(pos + read)
            }
            return read
        }

        val bytes = ByteArray(min(destLen.toLong(), srcLen - position).toInt())
        val read = read(bytes, position, 0, bytes.size)
        if (read > 0) {
            buffer.put(bytes, 0, read)
        }
        return read
    }

    @Blocking
    override suspend fun readAsync(buffer: ByteBuffer, position: Long): Int {
        @Suppress("BlockingMethodInNonBlockingContext")
        return read(buffer, position)
    }


    @Blocking
    @Throws(IOException::class)
    @Suppress("NestedBlockDepth")
    override fun transferTo(
        channel: WritableByteChannel,
        bufferSize: Int,
        directBuffer: Boolean,
    ): Long {
        val srcLen = size
        if (srcLen == 0L) {
            return 0L
        }
        val bufSize = min(max(bufferSize, DEFAULT_TRANSFER_BUF_SIZE).toLong(), srcLen).toInt()
        val buffer = when {
            directBuffer -> ByteBuffer.allocateDirect(bufSize)
            else -> ByteBuffer.wrap(ByteArray(bufSize))
        }
        try {
            var position = 0L
            while (true) {
                val read = read(buffer, position)
                if (read > 0) {
                    buffer.flipCompat()
                    var written = 0
                    do {
                        written += channel.write(buffer)
                    } while (written < read)
                } else if (read < 0) {
                    throw EOFException(
                        "Unexpected end of data at $position, expected $srcLen bytes",
                    )
                }
                position += read
                if (position == srcLen) {
                    return position
                }
                buffer.clearCompat()
            }
        } finally {
            buffer?.releaseCompat()
        }
    }

    @Blocking
    @Throws(IOException::class)
    override fun transferTo(stream: OutputStream, bufferSize: Int): Long {
        val srcLen = size
        if (srcLen == 0L) {
            return 0L
        }
        val buffer = ByteArray(
            size = min(max(bufferSize, DEFAULT_TRANSFER_BUF_SIZE).toLong(), srcLen).toInt(),
        )
        var position = 0L
        while (true) {
            val read = read(buffer, position, 0, buffer.size)
            if (read > 0) {
                stream.write(buffer, 0, read)
            } else if (read < 0) {
                throw EOFException(
                    "Unexpected end of data at $position, expected $srcLen bytes",
                )
            }
            position += read
            if (position == srcLen) {
                break
            }
        }
        return position
    }


    private companion object {
        private const val DEFAULT_TRANSFER_BUF_SIZE = 1024
    }
}
