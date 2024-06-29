@file:Suppress("FunctionName")

package fluxo.io.util

internal actual fun <K, V> ConcurrentHashMap(): MutableMap<K, V> =
    co.touchlab.stately.collections.ConcurrentMutableMap()
