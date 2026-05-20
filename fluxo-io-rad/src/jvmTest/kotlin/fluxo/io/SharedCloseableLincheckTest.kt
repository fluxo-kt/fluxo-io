package fluxo.io

import kotlin.test.Test
import org.jetbrains.lincheck.datastructures.ModelCheckingOptions
import org.jetbrains.lincheck.datastructures.Operation

internal class SharedCloseableLincheckTest {
    private val closeable = LincheckCloseable()

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
        closeable.addOnSharedCloseListener { }
        return true
    }

    @Operation
    fun isOpen(): Boolean = closeable.isOpen

    @Test
    fun modelCheckingTest() {
        ModelCheckingOptions()
            .actorsBefore(0)
            .actorsPerThread(2)
            .actorsAfter(0)
            .iterations(100)
            .check(this::class)
    }

    private class LincheckCloseable : SharedCloseable() {
        override fun onSharedClose() = Unit
    }
}
