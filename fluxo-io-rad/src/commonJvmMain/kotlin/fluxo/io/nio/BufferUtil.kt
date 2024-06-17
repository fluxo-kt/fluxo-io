package fluxo.io.nio

import android.os.Build
import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi
import fluxo.io.internal.EMPTY_BYTE_ARRAY
import fluxo.io.internal.IS_ANDROID
import fluxo.io.internal.classForNameOrNull
import fluxo.io.internal.getStaticField
import fluxo.io.internal.invokeStaticMethod
import fluxo.io.internal.reflectionMethodOrNull
import java.lang.reflect.Method
import java.nio.Buffer
import java.nio.ByteBuffer
import java.security.PrivilegedActionException
import java.security.PrivilegedExceptionAction
import kotlinx.atomicfu.atomic

public object BufferUtil {

    @JvmField
    public val EMPTY_BYTE_BUFFER: ByteBuffer = ByteBuffer.wrap(EMPTY_BYTE_ARRAY)


    private val logger = atomic<((String, Throwable?) -> Unit)?>(initial = null)

    public fun setLogger(logger: (String, Throwable?) -> Unit) {
        this.logger.value = logger
    }


    // Proper cleanup for DirectBuffer
    //
    // A mapped byte buffer, and the file mapping that it represents remain valid
    //  until the buffer itself is garbage-collected.
    // Especially important on Windows as memory mapped can't be deleted or renamed.
    //
    // See:
    // https://stackoverflow.com/a/5036003/1816338
    // https://stackoverflow.com/a/19447758/1816338
    // https://bugs.java.com/bugdatabase/view_bug.do?bug_id=4715154
    // https://bitbucket.org/vladimir.dolzhenko/gflogger/src/366fd4ee/core/src/main/java/org/gflogger/util/DirectBufferUtils.java
    // https://github.com/cddesire/hoss/blob/4a97dc0/src/hdfs/org/apache/hadoop/hdfs/hoss/meta/ByteBufferCleaner.java
    // https://github.com/graphhopper/graphhopper/blob/d310f63/core/src/main/java/com/graphhopper/storage/MMapDataAccess.java#L83
    // https://github.com/graphhopper/graphhopper/issues/933
    private val DIRECT_BYTE_BUFFER_CLASS: Class<*>?
    private val UNMAP_METHOD: Method?
    private val FREE_DIRECT_BUFFER_METHOD: Method?
    private val CLEANER_METHOD: Method?
    private val CLEAN_METHOD: Method?
    private var ATTACHMENT_METHOD: Method?
    private val UNSAFE_CLEAN_METHOD: Method?
    private val MAPPED_BYTE_BUFFER_ADAPTER_CLASS: Class<*>?
    private val UNSAFE: Any?

    init {
        // Oracle JRE 6-8 / OpenJDK 6-8
        val sunDirectBuffer = classForNameOrNull("sun.nio.ch.DirectBuffer")
        DIRECT_BYTE_BUFFER_CLASS = classForNameOrNull("java.nio.DirectByteBuffer")

        CLEANER_METHOD = sunDirectBuffer?.reflectionMethodOrNull("cleaner")
            ?: DIRECT_BYTE_BUFFER_CLASS?.reflectionMethodOrNull("cleaner")
        CLEAN_METHOD = CLEANER_METHOD?.returnType?.reflectionMethodOrNull("clean")
            ?: classForNameOrNull("sun.misc.Cleaner")?.reflectionMethodOrNull("clean")

        // Strictly forbidden in Android since API level 28
        ATTACHMENT_METHOD = if (IS_ANDROID && Build.VERSION.SDK_INT >= VERSION_CODES.P) null else {
            sunDirectBuffer?.reflectionMethodOrNull("attachment")
            // They changed the name in Java 7 (???)
                ?: sunDirectBuffer?.reflectionMethodOrNull("viewedBuffer")
        }


        // Android API level 27, public API!
        // android.os.SharedMemory.unmap
        UNMAP_METHOD = if (!IS_ANDROID || Build.VERSION.SDK_INT < VERSION_CODES.O_MR1) null else {
            classForNameOrNull("android.os.SharedMemory")
                ?.reflectionMethodOrNull("unmap", ByteBuffer::class.java)
        }

        // Android API, grey list
        // java.nio.NioUtils.freeDirectBuffer
        FREE_DIRECT_BUFFER_METHOD = classForNameOrNull("java.nio.NioUtils")
            ?.reflectionMethodOrNull("freeDirectBuffer", ByteBuffer::class.java)


        // Oracle JRE 9+ / OpenJDK 9+
        val unsafe = classForNameOrNull("sun.misc.Unsafe") ?:
        // jdk.internal.misc.Unsafe doesn't yet have an invokeCleaner() method,
        // but that method should be added if sun.misc.Unsafe is removed.
        classForNameOrNull("jdk.internal.misc.Unsafe")

        UNSAFE = try {
            unsafe?.getStaticField<Any?>("theUnsafe")
        } catch (_: Throwable) {
            null
        }
            ?: try {
                unsafe?.invokeStaticMethod("getUnsafe")
            } catch (_: Throwable) {
                null
            }

        UNSAFE_CLEAN_METHOD =
            unsafe?.reflectionMethodOrNull("invokeCleaner", ByteBuffer::class.java)


        // Android 4.1
        MAPPED_BYTE_BUFFER_ADAPTER_CLASS = classForNameOrNull("java.nio.MappedByteBufferAdapter")
    }


    /**
     * Proper cleanup for [sun.nio.ch.DirectBuffer]
     *
     * @param buffer Any [Buffer]
     *
     * @return the [EMPTY_BYTE_BUFFER]
     */
    @JvmStatic
    @RequiresApi(9)
    public fun releaseBuffer(buffer: Buffer?): ByteBuffer {
        if (buffer != null && buffer.isDirect) {
            val exceptions = ArrayList<Throwable>()
            if (!releaseBuffer(buffer, exceptions)) {
                logger.value?.invoke(
                    "Can't release direct buffer: $buffer",
                    exceptions.reduceOrNull { e, e2 ->
                        e.addSuppressed(e2)
                        e
                    },
                )

                try {
                    // Last chance: force garbage collection.
                    @Suppress("ExplicitGarbageCollectionCall")
                    System.gc()
                } catch (_: Throwable) {
                }
            }
        }
        return EMPTY_BYTE_BUFFER
    }

    private fun releaseBuffer(buffer: Buffer, exc: ArrayList<Throwable>): Boolean {
        return try {
            @Suppress("DEPRECATION")
            java.security.AccessController.doPrivileged(
                PrivilegedExceptionAction {
                    releaseBuffer0(buffer, exc)
                },
            )
        } catch (e: PrivilegedActionException) {
            exc.add(e.cause ?: e)
            false
        } catch (e: Throwable) {
            exc.add(e)
            releaseBuffer0(buffer, exc)
        }
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth", "LongMethod")
    private fun releaseBuffer0(buffer: Buffer, exc: ArrayList<Throwable>): Boolean {
        try {
            // Android API level 27+, public API!
            // android.os.SharedMemory { public static void unmap (ByteBuffer buffer) }
            // https://android.googlesource.com/platform/frameworks/base/+/0f9429b/core/java/android/os/SharedMemory.java#235
            if (UNMAP_METHOD != null) {
                try {
                    UNMAP_METHOD.invoke(buffer)
                    return true
                } catch (e: Throwable) {
                    exc.add(e)
                }
            }

            // Android API, grey list
            // java.nio.NioUtils { public static void freeDirectBuffer(ByteBuffer buffer) }
            // https://android.googlesource.com/platform/libcore/+/bacbadb/luni/src/main/java/java/nio/NioUtils.java#44
            if (FREE_DIRECT_BUFFER_METHOD != null) {
                try {
                    FREE_DIRECT_BUFFER_METHOD.invoke(buffer)
                    return true
                } catch (e: Throwable) {
                    exc.add(e)
                }
            }

            // Oracle JRE 9+ / OpenJDK 9+
            if (UNSAFE_CLEAN_METHOD != null && UNSAFE != null) {
                try {
                    // >=JDK9 class sun.misc.Unsafe { void invokeCleaner(ByteBuffer buf) }
                    UNSAFE_CLEAN_METHOD.invoke(UNSAFE, buffer)
                    return true
                } catch (e: Throwable) {
                    exc.add(e)
                }
            }

            // Android 4.1
            if (buffer.javaClass.simpleName == "MappedByteBufferAdapter") {
                try {
                    // ((java.nio.MappedByteBufferAdapter)buffer).free()
                    if (callBufferFree(buffer, MAPPED_BYTE_BUFFER_ADAPTER_CLASS)) {
                        return true
                    }
                } catch (e: Throwable) {
                    exc.add(e)
                }
            }

            // Oracle JRE 6-8 / OpenJDK 6-8
            try {
                val cleaner: Any? = CLEANER_METHOD?.invoke(buffer)
                if (cleaner != null && CLEAN_METHOD != null) {
                    CLEAN_METHOD.invoke(cleaner)
                    return true
                }
            } catch (e: Throwable) {
                exc.add(e)
            }

            // Alternate approach of getting the viewed buffer
            try {
                val viewedBuffer: Any? = ATTACHMENT_METHOD?.invoke(buffer)
                if (viewedBuffer != null && releaseBuffer(viewedBuffer as Buffer, exc)) {
                    return true
                }
            } catch (e: Throwable) {
                exc.add(e)
            }

            // Android 5.1.1
            // ((java.nio.DirectByteBuffer)buffer).free()
            // Apache Harmony
            // ((org.apache.harmony.nio.internal.DirectBuffer)buffer).free()
            try {
                // ((java.nio.MappedByteBufferAdapter)buffer).free()
                if (callBufferFree(buffer, DIRECT_BYTE_BUFFER_CLASS)) {
                    return true
                }
            } catch (e: Throwable) {
                exc.add(e)
            }

            return false
        } catch (e: Throwable) {
            exc.add(e)
        }

        return false
    }

    @Throws(Exception::class)
    private fun callBufferFree(buffer: Buffer, bufferClass: Class<*>?): Boolean {
        val freeMethod = buffer.reflectionMethodOrNull("free")
            ?: bufferClass?.reflectionMethodOrNull("free")
        if (freeMethod != null) {
            freeMethod.invoke(buffer)
            return true
        }
        return false
    }
}
