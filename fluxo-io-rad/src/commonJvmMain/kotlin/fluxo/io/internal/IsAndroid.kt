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


/**
 * `android.os.Build.VERSION.SDK_INT` on Android, `0` elsewhere.
 *
 * Read by reflection, once: this source set also compiles for the plain JVM, and putting
 * `android.jar` on that classpath instead would let JVM code compile against Android's newer
 * `java.*` members (e.g. `InputStream.readAllBytes`), which the Java 8 floor does not have.
 */
@JvmField
internal val ANDROID_SDK_INT: Int = if (!IS_ANDROID) 0 else runCatching {
    Class.forName("android.os.Build\$VERSION").getField("SDK_INT").getInt(null)
}.getOrDefault(0)


private fun systemProperty(key: String): String =
    System.getProperty(key, "") ?: ""
