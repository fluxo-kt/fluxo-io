@file:JvmMultifileClass
@file:JvmName("Rad")

package fluxo.io.rad

import fluxo.io.internal.ThreadSafe
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName

/**
 * Adapts this blocking data to [AsyncRandomAccessData] by running each read on
 * [blockingContext], e.g. `rad.asAsync(Dispatchers.IO)`, so the caller's thread never blocks.
 * That holds only for a dispatcher that really moves work to other threads: one that runs
 * inline (`Dispatchers.Unconfined`, `Main.immediate`) blocks the caller anyway, and the check
 * below cannot tell them apart without kotlinx-coroutines.
 *
 * The result takes over this handle: closing it closes this data. To keep using this one
 * as well, adapt a separate handle: `rad.share().asAsync(Dispatchers.IO)`.
 *
 * @param blockingContext where blocking reads run; must contain a dispatcher
 * @throws IllegalArgumentException if [blockingContext] has no dispatcher, since reads would
 *  then block whichever thread resumes the caller
 */
public fun RandomAccessData.asAsync(blockingContext: CoroutineContext): AsyncRandomAccessData {
    requireNotNull(blockingContext[ContinuationInterceptor]) {
        "asAsync needs a dispatcher to run blocking reads on, e.g. asAsync(Dispatchers.IO); " +
            "got $blockingContext"
    }
    return BlockingAsAsync(this, blockingContext)
}

/**
 * Each call is ONE dispatch: the whole blocking call (a `readFully` loop included) runs on
 * [context], then the caller resumes on its own dispatcher. Lifetime is entirely the wrapped
 * [rad]'s: slices wrap its slices and closing closes it, so no state of its own exists here.
 *
 * Uses only stdlib coroutine intrinsics: `startCoroutine` dispatches through the context's
 * interceptor, so kotlinx-coroutines stays an optional dependency of this library.
 */
@ThreadSafe
private class BlockingAsAsync(
    private val rad: RandomAccessData,
    private val context: CoroutineContext,
) : AsyncRandomAccessData {

    override val size: Long get() = rad.size

    override fun slice(position: Long, length: Long): AsyncRandomAccessData =
        BlockingAsAsync(rad.slice(position, length), context)

    override fun share(): AsyncRandomAccessData = BlockingAsAsync(rad.share(), context)

    override suspend fun read(buffer: ByteArray, position: Long, offset: Int, maxLength: Int) =
        runBlockingCall { rad.read(buffer, position, offset, maxLength) }

    override suspend fun readFully(
        buffer: ByteArray, position: Long, offset: Int, maxLength: Int,
    ) = runBlockingCall { rad.readFully(buffer, position, offset, maxLength) }

    override fun close() = rad.close()

    private suspend inline fun <T> runBlockingCall(crossinline call: () -> T): T =
        suspendCoroutine { caller ->
            suspend { call() }.startCoroutine(Continuation(context, caller::resumeWith))
        }
}
