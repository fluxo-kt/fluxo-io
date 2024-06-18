@file:Suppress("KDocUnresolvedReference")

package fluxo.io.rad

import fluxo.io.IOException
import fluxo.io.internal.AccessorAwareRad
import fluxo.io.internal.SharedDataAccessor
import fluxo.io.rad.RandomAccessDataRaf.RafAccess
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.Semaphore
import javax.annotation.concurrent.ThreadSafe

/**
 * [RandomAccessData] implementation backed by a [RandomAccessFile].
 *
 * WARNING: [RandomAccessFile] cannot always work reliably under a concurrent load!
 * [RandomAccessDataRaf] tries to control it, but it can lead to performance slowdown
 * or even deadlocks sometimes!
 *
 * @param access provides access to the underlying file
 * @param offset the offset of the section
 * @param size the length of the section
 *
 * @see org.springframework.boot.loader.data.RandomAccessDataFile
 */
@ThreadSafe
internal class RandomAccessDataRaf
private constructor(access: RafAccess, offset: Long, size: Long) :
    AccessorAwareRad<RafAccess, RandomAccessDataRaf>(access, offset, size) {

    /**
     * Create a new [RandomAccessDataRaf] backed by the specified [file].
     * @param file the underlying file
     * @throws IllegalArgumentException if the file is null or does not exist
     */
    constructor(file: File, offset: Long = 0L, size: Long = file.length() - offset)
        : this(RandomAccessFile(file, "r"), offset, size)

    /**
     * Create a new [RandomAccessDataRaf] backed by the specified [RandomAccessFile].
     * @param raf the underlying [RandomAccessFile]
     */
    constructor(raf: RandomAccessFile, offset: Long = 0L, size: Long = raf.length() - offset)
        : this(RafAccess(raf), offset, size)


    override fun getSubsection0(
        access: RafAccess, globalPosition: Long, length: Long,
    ) = RandomAccessDataRaf(access, globalPosition, length)


    override fun readByteAt0(position: Long): Int {
        return access.readByte(offset + position)
    }

    class RafAccess
    internal constructor(
        override val api: RandomAccessFile,
    ) : SharedDataAccessor() {

        // WARNING: RandomAccessFile cannot always work reliably under a concurrent load!

        private val semaphore = Semaphore(1)

        override val size: Long get() = api.length()

        private fun seek(api: RandomAccessFile, position: Long) {
            do {
                api.seek(position)
            } while (api.filePointer != position)
        }

        override fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int {
            val lock = semaphore
            lock.acquire()
            try {
                val api = api
                while (true) {
                    seek(api, position)
                    val read = api.read(bytes, offset, length)
                    if (read > 0 && api.filePointer == position + read) {
                        return read
                    }
                }
            } finally {
                lock.release()
            }
        }

        @Throws(IOException::class)
        internal fun readByte(position: Long): Int {
            val lock = semaphore
            lock.acquire()
            try {
                val api = api
                while (true) {
                    seek(api, position)
                    val read = api.read()
                    if (read >= 0 && api.filePointer == position + 1L) {
                        return read
                    }
                }
            } finally {
                lock.release()
            }
        }
    }
}
