@file:JvmName("FluxoIoLogger")

package fluxo.io

import kotlin.jvm.JvmName
import kotlinx.atomicfu.atomic


internal var LOGGER by atomic<((String, Throwable?) -> Unit)?>(initial = null)
    private set

@JvmName("setLogger")
public fun setFluxoIoLogger(logger: (String, Throwable?) -> Unit) {
    LOGGER = logger
}
