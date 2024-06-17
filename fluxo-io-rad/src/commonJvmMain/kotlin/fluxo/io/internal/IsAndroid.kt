@file:JvmName("AndroidConstantsKt")

package fluxo.io.internal


// Reference:
// https://github.com/kodapan/osm-common/blob/master/core/src/main/java/se/kodapan/lang/OperativeSystemDetector.java

/**
 * Whether the current platform is Android.
 *
 * Returns `false` when running on JVM (including Android tests running on JVM).
 */
@JvmField
@Suppress("ReplaceCallWithBinaryOperator")
internal val IS_ANDROID: Boolean = "android" in systemProperty("java.vendor.url")
    || "android" in systemProperty("java.vm.vendor.url")
    || "Dalvik" in systemProperty("java.vm.name")
    || "Android" in systemProperty("java.runtime.name")
    || "Android" in systemProperty("java.specification.vendor")
    || "Android" in systemProperty("java.vm.specification.vendor")
    || "Android" in systemProperty("java.vm.vendor")
    || "true".equals(systemProperty("android.vm.dexfile"))
    || "Dalvik" in systemProperty("java.specification.name")
    || "Android" in systemProperty("java.vendor")
    || "Dalvik" in systemProperty("java.vm.specification.name")


private fun systemProperty(key: String): String =
    System.getProperty(key, "") ?: ""
