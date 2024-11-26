@file:Suppress("KDocUnresolvedReference")

package fluxo.io.rad

import fluxo.io.IOException
import fluxo.io.internal.AccessorAwareRad
import fluxo.io.internal.SharedDataAccessor
import fluxo.io.rad.RandomAccessFileRad.RafAccess
import java.io.RandomAccessFile
import javax.annotation.concurrent.ThreadSafe

// TODO: Try to avoiding the excessive `filePointer` usages.

/**
 * [RandomAccessData] implementation backed by a [RandomAccessFile].
 *
 * **WARNING: [RandomAccessFile]
 * can't always work reliably under a concurrent load!
 * [Implementation][RandomAccessFileRad] tries to control it,
 * but it can lead to performance slowdown or even rare deadlocks sometimes!**
 *
 * **WARNING:
 * This implementation uses [synchronized] blocks to ensure thread safety!*
 *
 * @param access provides access to the underlying file
 * @param offset the offset of the section
 * @param size the length of the section
 *
 * @see org.springframework.boot.loader.data.RandomAccessDataFile
 */
@ThreadSafe
internal class RandomAccessFileRad
private constructor(access: RafAccess, offset: Long, size: Long) :
    AccessorAwareRad<RafAccess>(access, offset, size) {

    constructor(raf: RandomAccessFile, offset: Long, size: Long)
        : this(RafAccess(raf), offset, size)


    override fun getSubsection0(access: RafAccess, globalPosition: Long, length: Long) =
        RandomAccessFileRad(access, globalPosition, length)


    override fun readByteAt0(position: Long): Int =
        access.readByte(offset + position)


    internal class RafAccess(
        private val api: RandomAccessFile,
    ) : SharedDataAccessor(resources = arrayOf(api)) {

        private companion object {
            private const val MAX_TRIES = 10
            private const val DEBUG = false
        }


        private val monitor = Any()

        override val size: Long get() = api.length()

        private fun seek(api: RandomAccessFile, position: Long) {
            // Retry until the seek is successful
            var tries = 0
            do {
                if (tries++ >= MAX_TRIES) {
                    throw IOException("Failed to seek to position $position after $MAX_TRIES tries")
                }
                api.seek(position)
            } while (api.filePointer != position)
        }

        override fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int {
            val api = api
            // RandomAccessFile isn't reliably under a concurrent load!
            synchronized(monitor) {
                // Retry until the read is successful
                var tries = 0
                while (true) {
                    seek(api, position)
                    val read = api.read(bytes, offset, length)
                    if (read > 0) {
                        if (DEBUG && api.filePointer != position + read) {
                            throw AssertionError(
                                "Read $read bytes at position $position," +
                                    " but the pointer is at ${api.filePointer}",
                            )
                        }
                        return read
                    }
                    if (tries++ >= MAX_TRIES) {
                        throw IOException(
                            "Failed to read $length bytes at position $position" +
                                " after $MAX_TRIES tries",
                        )
                    }
                }
            }
        }

        @Throws(IOException::class)
        internal fun readByte(position: Long): Int {
            val api = api
            // RandomAccessFile isn't reliably under a concurrent load!
            synchronized(monitor) {
                // Retry until the read is successful
                var tries = 0
                while (true) {
                    seek(api, position)
                    val byteUint = api.read()
                    if (byteUint >= 0) {
                        // Sometimes `read` returns a byte,
                        // but the pointer is still at the same position.
                        if (DEBUG && api.filePointer != position + 1L) {
                            throw AssertionError(
                                "Read a byte at position $position," +
                                    " but the pointer is at ${api.filePointer}",
                            )
                        }
                        return byteUint
                    }
                    if (tries++ >= MAX_TRIES) {
                        throw IOException(
                            "Failed to read a byte at position $position after $MAX_TRIES tries",
                        )
                    }
                }
            }
        }
    }
}
