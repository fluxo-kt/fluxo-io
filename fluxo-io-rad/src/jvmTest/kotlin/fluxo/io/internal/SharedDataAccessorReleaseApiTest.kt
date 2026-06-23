package fluxo.io.internal

import fluxo.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Falsifies the structural fix for the `onSharedClose` leak-on-throw bug class:
 * a release-throw MUST NOT skip the [SharedDataAccessor] `resources` close pass.
 *
 * RED-bisect: revert [SharedDataAccessor] to the pre-template form
 * (`api.releaseCompat(); super.onSharedClose()` pattern with no try-finally)
 * and these tests turn red; restore the final-template form to make them green.
 */
internal class SharedDataAccessorReleaseApiTest {

    @Test
    fun releaseApiThrowDoesNotLeakResources() {
        val resource = TrackingCloseable()
        val accessor = ThrowingReleaseAccessor(IOException("release boom"), resource)

        val thrown = assertFailsWith<IOException> { accessor.close() }

        assertEquals("release boom", thrown.message)
        assertTrue(resource.closed.get(), "resources MUST close even when releaseApi throws")
    }

    @Test
    fun releaseApiSuccessClosesResources() {
        val a = TrackingCloseable()
        val b = TrackingCloseable()
        val accessor = OkReleaseAccessor(a, b)

        accessor.close()

        assertTrue(a.closed.get())
        assertTrue(b.closed.get())
        assertTrue(accessor.releaseCalled.get())
    }

    @Test
    fun bothReleaseAndResourceThrowSurfaceWithSuppression() {
        val releaseErr = IOException("release boom")
        val resourceErr = IOException("resource boom")
        val accessor = ThrowingReleaseAccessor(releaseErr, FailingCloseable(resourceErr))

        val thrown = assertFailsWith<IOException> { accessor.close() }

        // Convention (matches resource-merge logic): latest exception primary, earlier suppressed.
        assertSame(resourceErr, thrown)
        assertEquals(1, thrown.suppressed.size)
        assertSame(releaseErr, thrown.suppressed[0])
    }

    private class TrackingCloseable : AutoCloseable {
        val closed = AtomicBoolean(false)
        override fun close() {
            check(closed.compareAndSet(false, true)) { "double-closed" }
        }
    }

    private class FailingCloseable(private val err: Throwable) : AutoCloseable {
        override fun close(): Unit = throw err
    }

    private class OkReleaseAccessor(vararg resources: AutoCloseable) :
        SharedDataAccessor(resources) {
        val releaseCalled = AtomicBoolean(false)
        override val size: Long get() = 0L
        override fun read(bytes: ByteArray, position: Long, offset: Int, length: Int) = -1
        override fun releaseApi() {
            releaseCalled.set(true)
        }
    }

    private class ThrowingReleaseAccessor(
        private val err: Throwable,
        vararg resources: AutoCloseable,
    ) : SharedDataAccessor(resources) {
        override val size: Long get() = 0L
        override fun read(bytes: ByteArray, position: Long, offset: Int, length: Int) = -1
        override fun releaseApi(): Unit = throw err
    }
}
