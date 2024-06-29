@file:Suppress("FunctionName")

package fluxo.io.util

internal expect fun <K, V> ConcurrentHashMap(): MutableMap<K, V>
