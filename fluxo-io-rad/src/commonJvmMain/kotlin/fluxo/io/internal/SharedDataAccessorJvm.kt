package fluxo.io.internal

import fluxo.io.IOException

/**
 * Rejects access once the shared resource has been released ([SharedCloseable.isOpen] turns
 * `false` from [SharedDataAccessor.onSharedClose] onward). JVM accessors that touch the resource
 * directly must call this so a read after the last close fails with a clean [IOException] rather
 * than hitting freed state — for a memory-mapped buffer that is a use-after-free that crashes the
 * JVM, and for the stream pool it would silently leak a re-created stream into an already-drained
 * pool. Channel-backed accessors need not call it: the JDK channel already throws
 * `ClosedChannelException` once closed.
 *
 * JVM-only by construction: only the resource-backed JVM impls reach freed state, and only here
 * can a descriptive [IOException] be raised (the common `expect class IOException` has no
 * message constructor).
 */
@Throws(IOException::class)
internal fun SharedDataAccessor.checkOpen() {
    if (!isOpen) {
        throw IOException("RandomAccessData is already closed")
    }
}
