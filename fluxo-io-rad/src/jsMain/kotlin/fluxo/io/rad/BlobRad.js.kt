package fluxo.io.rad

import fluxo.io.IOException
import fluxo.io.internal.AsyncAccessorRad
import fluxo.io.internal.SharedAsyncDataAccessor
import fluxo.io.util.EMPTY_AUTO_CLOSEABLE_ARRAY
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.w3c.files.Blob

/**
 * Random access to a browser `Blob` or `File` (also available in Node, Bun and Deno): each read
 * fetches only the requested range with `blob.slice(start, end).arrayBuffer()`, so a large
 * file picked by the user is never loaded whole. Returns a handle: close it once when finished.
 *
 * A Blob holds no OS resource, so closing only ends the handle; it is immutable, so reads
 * never see a changed size.
 */
public fun AsyncRandomAccessData.Companion.open(blob: Blob): AsyncRandomAccessData =
    AsyncAccessorRad(BlobAccess(blob))

private class BlobAccess(
    private val blob: Blob,
) : SharedAsyncDataAccessor(EMPTY_AUTO_CLOSEABLE_ARRAY) {

    override val size: Long = blob.size.toLong()

    override suspend fun read(bytes: ByteArray, position: Long, offset: Int, length: Int): Int =
        withLease {
            val start = position.toDouble()
            // Doubles, not the Int-typed stdlib slice(): a File can exceed 2 GiB.
            val chunk = blob.asDynamic().slice(start, start + length).arrayBuffer()
            val buffer = suspendCoroutine { cont ->
                chunk.then(
                    { b: ArrayBuffer -> cont.resume(b) },
                    { e: dynamic ->
                        val error = IOException("Cannot read Blob at $position: ${e?.message}")
                        cont.resumeWithException(error)
                    },
                )
            }
            val n = buffer.byteLength
            bytes.unsafeCast<Int8Array>().set(Int8Array(buffer), offset)
            if (n > 0) n else -1
        }
}
