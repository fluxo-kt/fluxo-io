@file:Suppress("KDocUnresolvedReference")

package fluxo.io.nio

import androidx.annotation.RequiresApi
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousCloseException
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.Channel
import java.nio.channels.CompletionHandler
import java.nio.channels.FileLock
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine


/**
 * [java.nio.channels.AsynchronousChannel] coroutine helpers.
 */


/**
 * Performs [AsynchronousFileChannel.lock] without blocking a thread and resumes when
 * asynchronous operation completes. This suspending function is cancellable.
 * If the [Job] of the current coroutine is cancelled or completed while this suspending function
 * is waiting, this function *closes the underlying channel* and immediately resumes
 * with [CancellationException].
 */
@RequiresApi(26)
public suspend fun AsynchronousFileChannel.aLock(): FileLock =
    suspendCancellableCoroutine { cont ->
        lock(cont, asyncIOHandler())
        closeOnCancel(cont)
    }

/**
 * Performs [AsynchronousFileChannel.lock] without blocking a thread and resumes when
 * asynchronous operation completes. This suspending function is cancellable.
 * If the [Job] of the current coroutine is cancelled or completed while this suspending function
 * is waiting, this function *closes the underlying channel* and immediately resumes
 * with [CancellationException].
 */
@RequiresApi(26)
public suspend fun AsynchronousFileChannel.aLock(
    position: Long,
    size: Long,
    shared: Boolean,
): FileLock = suspendCancellableCoroutine { cont ->
    lock(position, size, shared, cont, asyncIOHandler())
    closeOnCancel(cont)
}

/**
 * Performs [AsynchronousFileChannel.read] without blocking a thread and resumes when
 * asynchronous operation completes. This suspending function is cancellable.
 * If the [Job] of the current coroutine is cancelled or completed while this suspending function
 * is waiting, this function *closes the underlying channel* and immediately resumes
 * with [CancellationException].
 */
@RequiresApi(26)
public suspend fun AsynchronousFileChannel.aRead(buf: ByteBuffer, position: Long): Int =
    suspendCancellableCoroutine { cont ->
        read(buf, position, cont, asyncIOHandler())
        closeOnCancel(cont)
    }

/**
 * Performs [AsynchronousFileChannel.write] without blocking a thread and resumes when
 * asynchronous operation completes. This suspending function is cancellable.
 * If the [Job] of the current coroutine is cancelled or completed while this suspending function
 * is waiting, this function *closes the underlying channel* and immediately resumes
 * with [CancellationException].
 */
@RequiresApi(26)
public suspend fun AsynchronousFileChannel.aWrite(buf: ByteBuffer, position: Long): Int =
    suspendCancellableCoroutine { cont ->
        write(buf, position, cont, asyncIOHandler())
        closeOnCancel(cont)
    }

/**
 * Performs [AsynchronousServerSocketChannel.accept] without blocking a thread and resumes when
 * asynchronous operation completes. This suspending function is cancellable.
 * If the [Job] of the current coroutine is cancelled or completed while this suspending function
 * is waiting, this function *closes the underlying channel* and immediately resumes
 * with [CancellationException].
 */
@RequiresApi(26)
public suspend fun AsynchronousServerSocketChannel.aAccept(): AsynchronousSocketChannel =
    suspendCancellableCoroutine { cont ->
        accept(cont, asyncIOHandler())
        closeOnCancel(cont)
    }

/**
 * Performs [AsynchronousSocketChannel.connect] without blocking a thread and resumes when
 * asynchronous operation completes. This suspending function is cancellable.
 * If the [Job] of the current coroutine is cancelled or completed while this suspending function
 * is waiting, this function *closes the underlying channel* and immediately resumes
 * with [CancellationException].
 */
@RequiresApi(26)
public suspend fun AsynchronousSocketChannel.aConnect(socketAddress: SocketAddress): Unit =
    suspendCancellableCoroutine { cont ->
        connect(socketAddress, cont, AsyncVoidIOHandler)
        closeOnCancel(cont)
    }

/**
 * Performs [AsynchronousSocketChannel.read] without blocking a thread and resumes when
 * asynchronous operation completes. This suspending function is cancellable.
 * If the [Job] of the current coroutine is cancelled or completed while this suspending function
 * is waiting, this function *closes the underlying channel* and immediately resumes
 * with [CancellationException].
 */
@RequiresApi(26)
public suspend fun AsynchronousSocketChannel.aRead(
    buf: ByteBuffer, timeout: Long = 0L, timeUnit: TimeUnit = TimeUnit.MILLISECONDS,
): Int = suspendCancellableCoroutine { cont ->
    read(buf, timeout, timeUnit, cont, asyncIOHandler())
    closeOnCancel(cont)
}

/**
 * Performs [AsynchronousSocketChannel.write] without blocking a thread and resumes when
 * asynchronous operation completes. This suspending function is cancellable.
 * If the [Job] of the current coroutine is cancelled or completed while this suspending function
 * is waiting, this function *closes the underlying channel* and immediately resumes
 * with [CancellationException].
 */
@RequiresApi(26)
public suspend fun AsynchronousSocketChannel.aWrite(
    buf: ByteBuffer, timeout: Long = 0L, timeUnit: TimeUnit = TimeUnit.MILLISECONDS,
): Int = suspendCancellableCoroutine { cont ->
    write(buf, timeout, timeUnit, cont, asyncIOHandler())
    closeOnCancel(cont)
}


// ---------------- private details ----------------

private fun Channel.closeOnCancel(cont: CancellableContinuation<*>) {
    cont.invokeOnCancellation { cause: Throwable? ->
        try {
            close()
        } catch (t: Throwable) {
            // The specification says that it is Ok to call it any time,
            //  but the reality is different,
            // so we have to ignore the exception.
            cause?.addSuppressed(t)
        }
    }
}

@RequiresApi(26)
@Suppress("UNCHECKED_CAST")
private fun <T> asyncIOHandler(): CompletionHandler<T, CancellableContinuation<T>> =
    AsyncIOHandlerAny as CompletionHandler<T, CancellableContinuation<T>>

@RequiresApi(26)
private object AsyncIOHandlerAny : CompletionHandler<Any, CancellableContinuation<Any>> {
    override fun completed(result: Any, cont: CancellableContinuation<Any>) {
        cont.resume(result)
    }

    override fun failed(ex: Throwable, cont: CancellableContinuation<Any>) {
        // Return if already canceled and got an expected exception for that case.
        if (ex is AsynchronousCloseException && cont.isCancelled) {
            return
        }
        cont.resumeWithException(ex)
    }
}

@RequiresApi(26)
private object AsyncVoidIOHandler : CompletionHandler<Void?, CancellableContinuation<Unit>> {
    override fun completed(result: Void?, cont: CancellableContinuation<Unit>) {
        cont.resume(Unit)
    }

    override fun failed(ex: Throwable, cont: CancellableContinuation<Unit>) {
        // Return if already canceled and got an expected exception for that case.
        if (ex is AsynchronousCloseException && cont.isCancelled) {
            return
        }
        cont.resumeWithException(ex)
    }
}
