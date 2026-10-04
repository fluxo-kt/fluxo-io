package fluxo.io.internal

import fluxo.io.IOException
import fluxo.io.nio.clearCompat
import fluxo.io.nio.flipCompat
import fluxo.io.nio.positionCompat
import fluxo.io.nio.releaseCompat
import fluxo.io.rad.InputStreamFromRad
import fluxo.io.rad.RandomAccessData
import fluxo.io.util.MAX_BYTE
import fluxo.io.util.readAllBytesImpl
import fluxo.io.util.readFullyImpl
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel
import kotlin.math.max
import kotlin.math.min

/**
 * Common logic for [RandomAccessData] implementations.
 *
 * Every public read method is `final` and delegates to a protected `…0` hook that
 * implementations override. The public layer is the single place where per-call rules
 * ([ensureOpen]) apply to every path, so an implementation's fast-path override can never
 * bypass them. Methods that only call other public reads (`readFully`) are covered by those.
 */
@ThreadSafe
@InternalFluxoIoApi
internal actual abstract class BasicRad
internal actual constructor(owner: RadHandle?) : RadHandle(owner) {

    final override fun asInputStream(): InputStream {
        ensureOpen()
        return asInputStream0()
    }

    protected open fun asInputStream0(): InputStream = InputStreamFromRad(this)


    @Blocking
    actual final override fun readAllBytes(): ByteArray {
        ensureOpen()
        return readAllBytes0()
    }

    protected actual open fun readAllBytes0(): ByteArray = readAllBytesImpl()

    @Blocking
    actual final override fun readFrom(position: Long, maxLength: Int): ByteArray {
        ensureOpen()
        return readFrom0(position, maxLength)
    }

    protected actual abstract fun readFrom0(position: Long, maxLength: Int): ByteArray

    @Blocking
    actual final override fun read(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int {
        ensureOpen()
        return read0(buffer, position, offset, maxLength)
    }

    protected actual abstract fun read0(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int

    @Blocking
    actual final override fun readFully(
        buffer: ByteArray,
        position: Long,
        offset: Int,
        maxLength: Int,
    ): Int {
        if (Thread.interrupted()) {
            throw IOException("Thread interrupted")
        }
        return readFullyImpl(buffer, position, offset, maxLength)
    }


    @Blocking
    final override fun readByteAt(position: Long): Int {
        ensureOpen()
        val srcLen = size
        return when {
            position >= srcLen -> -1
            position < 0L -> throw IndexOutOfBoundsException("srcPos=$position, srcLen=$srcLen")
            Thread.interrupted() -> throw IOException("Thread interrupted")
            else -> readByteAt0(position)
        }
    }

    /**
     * Default naive inefficient implementation for the [readByteAt].
     *
     * _No bounds check here!
     * Already done in the [readByteAt] method._
     */
    @Suppress("MagicNumber")
    protected open fun readByteAt0(position: Long): Int =
        readFrom(position, maxLength = 1)[0].toInt() and MAX_BYTE


    @Blocking
    @Deprecated("Suspends but blocks the calling thread for the whole read. Use asAsync(dispatcher), e.g. rad.asAsync(Dispatchers.IO).read(…).", level = DeprecationLevel.ERROR)
    actual final override suspend fun readAsync(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int {
        ensureOpen()
        return readAsync0(buffer, position, offset, maxLength)
    }

    protected open suspend fun readAsync0(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int {
        @Suppress("BlockingMethodInNonBlockingContext")
        return read(buffer, position, offset, maxLength)
    }

    @Deprecated("Suspends but blocks the calling thread for the whole read. Use asAsync(dispatcher), e.g. rad.asAsync(Dispatchers.IO).read(…).", level = DeprecationLevel.ERROR)
    actual final override suspend fun readFullyAsync(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ): Int = readFully(buffer, position, offset, maxLength)


    @Blocking
    @Throws(IOException::class)
    final override fun read(buffer: ByteBuffer, position: Long): Int {
        ensureOpen()
        return read0(buffer, position)
    }

    @Throws(IOException::class)
    protected open fun read0(buffer: ByteBuffer, position: Long): Int {
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
    @Deprecated("Suspends but blocks the calling thread for the whole read. Use asAsync(dispatcher), e.g. rad.asAsync(Dispatchers.IO).read(…).", level = DeprecationLevel.ERROR)
    final override suspend fun readAsync(buffer: ByteBuffer, position: Long): Int {
        ensureOpen()
        return readAsync0(buffer, position)
    }

    protected open suspend fun readAsync0(buffer: ByteBuffer, position: Long): Int {
        @Suppress("BlockingMethodInNonBlockingContext")
        return read(buffer, position)
    }


    @Blocking
    @Throws(IOException::class)
    final override fun transferTo(
        channel: WritableByteChannel,
        bufferSize: Int,
        directBuffer: Boolean,
    ): Long {
        ensureOpen()
        return transferTo0(channel, bufferSize, directBuffer)
    }

    @Throws(IOException::class)
    @Suppress("NestedBlockDepth")
    protected open fun transferTo0(
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
    final override fun transferTo(stream: OutputStream, bufferSize: Int): Long {
        ensureOpen()
        return transferTo0(stream, bufferSize)
    }

    @Throws(IOException::class)
    protected open fun transferTo0(stream: OutputStream, bufferSize: Int): Long {
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
