package fluxo.io

import kotlin.test.Test
import org.jetbrains.lincheck.datastructures.ModelCheckingOptions
import org.jetbrains.lincheck.datastructures.Operation

internal class SharedCloseableLincheckTest {
    private val closeable = LincheckCloseable()
    private val listener: (Throwable?) -> Unit = { }

    @Operation
    fun retain(): Boolean =
        try {
            closeable.retain()
            true
        } catch (_: IllegalStateException) {
            false
        }

    @Operation
    fun close(): Boolean {
        closeable.close()
        return true
    }

    @Operation
    fun addOnSharedCloseListener(): Boolean {
        closeable.addOnSharedCloseListener(listener)
        return true
    }

    @Operation
    fun removeOnSharedCloseListener(): Boolean {
        closeable.removeOnSharedCloseListener(listener)
        return true
    }

    @Operation
    fun isOpen(): Boolean = closeable.isOpen

    @Operation
    fun access(): Boolean =
        try {
            closeable.withLease { closeable.touch(); true }
        } catch (_: java.io.IOException) {
            false
        }

    @Test
    fun modelCheckingTest() {
        ModelCheckingOptions()
            .actorsBefore(0)
            .actorsPerThread(2)
            .actorsAfter(0)
            // 2 threads x 2 operations over 7 operations is a small scenario space; the default
            // 10k invocations per iteration re-explored the same interleavings for minutes.
            // At this budget a planted "lease granted after the last close" defect still fails
            // within seconds; the remaining run time is Lincheck's fixed instrumentation cost.
            .iterations(10)
            .invocationsPerIteration(200)
            .check(this::class)
    }

    private class LincheckCloseable : SharedCloseable() {
        private var released = false

        // A second release, or an access that runs after it, is the bug class the lease exists
        // to make impossible; failing here turns it into a Lincheck counterexample.
        override fun onSharedClose() {
            check(!released) { "released twice" }
            released = true
        }

        fun touch() = check(!released) { "access after release" }
    }
}
