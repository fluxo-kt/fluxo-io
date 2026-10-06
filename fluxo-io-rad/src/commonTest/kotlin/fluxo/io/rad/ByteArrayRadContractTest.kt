package fluxo.io.rad

import kotlin.test.Test

/**
 * Runs the contract on `ByteArray` data on every target; file sources run it in their own tests.
 */
internal class ByteArrayRadContractTest {

    @Test
    fun contract() = RadContract.verify { RadByteArrayAccessor(it) }
}
