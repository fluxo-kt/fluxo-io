package fluxo.io.rad

import fluxo.io.internal.AccessorAwareRad
import fluxo.io.internal.SharedDataAccessor
import fluxo.io.nio.limitCompat
import fluxo.io.rad.FileChannelRad.FileChannelAccess
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.WritableByteChannel
import javax.annotation.concurrent.ThreadSafe


/**
 * [RadByteArrayAccessor] implementation backed by a [FileChannel].
 *
 * @param access provides access to the underlying channel
 * @param offset the offset of the section
 * @param size the length of the section
 */
@ThreadSafe
internal class FileChannelRad
private constructor(access: FileChannelAccess, offset: Long, size: Long) :
    AccessorAwareRad<FileChannelAccess, FileChannelRad>(
        access, offset, size,
    ) {

    /**
     * Create a new [FileChannelRad] backed by the specified [channel].
     * @param channel the underlying channel
     */
    constructor(channel: FileChannel, offset: Long = 0L, size: Long = channel.size() - offset)
        : this(FileChannelAccess(channel), offset, size)

    /**
     * Create a new [FileChannelRad] backed by the channel for specified [stream].
     * @param stream file stream to create a channel from
     */
    constructor(stream: FileInputStream, offset: Long = 0L) : this(stream.channel, offset)

    /**
     * Create a new [FileChannelRad] backed by the channel for specified [FileDescriptor].
     * @param fd file descriptor to open channel from
     */
    constructor(fd: FileDescriptor, offset: Long = 0L)
        : this(FileInputStream(fd).channel, offset)

    /**
     * Create a new [FileChannelRad] backed by the channel for specified [file].
     * @param file the file to open channel from
     */
    constructor(file: File, offset: Long = 0L, size: Long = file.length() - offset)
        : this(FileInputStream(file).channel, offset, size)


    override fun getSubsection0(
        access: FileChannelAccess, globalPosition: Long, length: Long,
    ) = FileChannelRad(access, globalPosition, length)


    @Throws(IOException::class)
    override fun read(buffer: ByteBuffer, position: Long): Int {
        val srcLen = size
        if (position < 0L) {
            throw IndexOutOfBoundsException("srcPos=$position, srcLen=$srcLen")
        }
        if (position >= srcLen) {
            return -1
        }
        val bufLimit = buffer.limit()
        val bufPos = buffer.position()
        val destLen = (bufLimit - bufPos).toLong()
        val len = Math.min(srcLen - position, destLen)
        if (len <= 0L) {
            return 0
        }
        if (destLen > len) {
            buffer.limitCompat(bufPos + len.toInt())
        }
        val read = access.api.read(buffer, offset + position)
        if (destLen > len) {
            buffer.limitCompat(bufLimit)
        }
        return read
    }

    @Throws(IOException::class)
    override fun transferTo(
        channel: WritableByteChannel, bufferSize: Int, directBuffer: Boolean,
    ): Long {
        val srcLen = size
        if (srcLen == 0L) {
            return 0L
        }
        val offset = offset
        var position = 0L
        while (true) {
            val written = access.api.transferTo(position + offset, srcLen - position, channel)
            if (written > 0) {
                position += written
                if (position == srcLen) {
                    break
                }
            }
        }
        return position
    }


    internal class FileChannelAccess
    internal constructor(public override val api: FileChannel) : SharedDataAccessor() {

        override val size: Long get() = api.size()

        @Throws(IOException::class)
        override fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int {
            return api.read(ByteBuffer.wrap(bytes, offset, length), position)
        }
    }
}
