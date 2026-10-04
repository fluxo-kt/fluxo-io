package fluxo.io.rad

import kotlin.test.Test

/** Runs the contract on every target; the only implementation outside the JVM is `ByteArray`. */
internal class ByteArrayRadContractTest {

    @Test
    fun contract() = RadContract.verify { RadByteArrayAccessor(it) }
}
