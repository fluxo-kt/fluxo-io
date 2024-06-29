@file:Suppress("FunctionName")

package fluxo.io.util

// WASM and JS are single-threaded, we can use a regular HashMap.
internal actual fun <K, V> ConcurrentHashMap(): MutableMap<K, V> = HashMap()
