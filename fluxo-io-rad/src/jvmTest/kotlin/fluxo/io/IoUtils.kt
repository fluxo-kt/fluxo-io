package fluxo.io

import fluxo.io.nio.markCompat
import fluxo.io.nio.resetCompat
import java.nio.ByteBuffer

/**
 * Tests compile against the build JDK's API but run on JDK 8 too (the `jdk8` CI lane): wrap any
 * JDK 9+ call in `if (JAVA_9_PLUS)`. JDK 8 reports "1.8", later JDKs "9", "17", ….
 */
val JAVA_9_PLUS: Boolean = !System.getProperty("java.specification.version").startsWith("1.")


fun ByteBuffer.toArray(): ByteArray {
    val array = ByteArray(remaining())
    markCompat()
    get(array)
    resetCompat()
    return array
}

