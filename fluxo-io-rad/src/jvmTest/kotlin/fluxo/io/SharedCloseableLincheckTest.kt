package fluxo.io

import kotlin.test.Test
import org.jetbrains.lincheck.datastructures.ModelCheckingOptions
import org.jetbrains.lincheck.datastructures.Operation
import org.jetbrains.lincheck.datastructures.Validate

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
    fun access(): Boolean =
        try {
            closeable.withLease { closeable.touch(); true }
        } catch (_: java.io.IOException) {
            false
        }

    @Validate
    fun noViolation() = check(closeable.violation == null) { closeable.violation!! }

    @Test
    fun modelCheckingTest() {
        ModelCheckingOptions()
            // Most of the run is Lincheck's fixed instrumentation cost; each random scenario adds
            // more, so the races the lease exists for are named instead of left to random
            // generation: a read racing the last close, the last two owners closing at once, a
            // retain racing the last close, and listeners changing during it.
            .addCustomScenario {
                parallel {
                    thread { actor(::close) }
                    thread { actor(::access) }
                }
            }
            .addCustomScenario {
                initial { actor(::retain) }
                parallel {
                    thread { actor(::close); actor(::access) }
                    thread { actor(::close); actor(::access) }
                }
            }
            .addCustomScenario {
                parallel {
                    thread { actor(::retain); actor(::access) }
                    thread { actor(::close) }
                }
            }
            .addCustomScenario {
                parallel {
                    thread {
                        actor(::addOnSharedCloseListener)
                        actor(::removeOnSharedCloseListener)
                    }
                    thread { actor(::close) }
                }
            }
            .iterations(0)
            .invocationsPerIteration(200)
            .check(this::class)
    }

    private class LincheckCloseable : SharedCloseable() {
        private var released = false
        var violation: String? = null

        // A second release, or an access that runs after it, is the bug class the lease exists
        // to make impossible. It is recorded, not thrown: Lincheck compares results with a
        // sequential run of this same code, where a thrown violation is an ordinary result.
        override fun onSharedClose() {
            if (released) violation = "released twice"
            released = true
        }

        fun touch() {
            if (released) violation = "access after release"
        }
    }
}
