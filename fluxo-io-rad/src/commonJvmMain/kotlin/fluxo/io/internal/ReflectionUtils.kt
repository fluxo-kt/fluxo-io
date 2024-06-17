@file:Suppress("unused", "UNCHECKED_CAST", "ReplaceCallWithBinaryOperator")

package fluxo.io.internal

import java.lang.reflect.AccessibleObject
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier


@Throws(NoSuchFieldException::class)
internal fun <T> Any.getStaticField(name: String): T {
    val c = if (this is Class<*>) this else javaClass
    return c.getReflectionField(name, writeAccess = false).get(null) as T
}


internal fun Any.reflectionMethodOrNull(name: String): Method? {
    return javaClass.reflectionMethodOrNull(name)
}

@Suppress("NestedBlockDepth")
@Throws(NoSuchMethodException::class)
private fun Class<*>.reflectionMethod(name: String): Method {
    var ex: Throwable? = null
    var method: Method? = null
    try {
        method = getDeclaredMethod(name)
    } catch (e: Throwable) {
        ex = e
    }

    if (method == null) {
        try {
            @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
            method = getMethod(name)!!
        } catch (e: Throwable) {
            if (ex != null) {
                when (e.cause) {
                    null -> e.initCause(ex)
                    else -> e.addSuppressed(ex)
                }
            }
            throw e
        }
    }

    method.setAccessibleSafe()
    return method
}

@Suppress("NestedBlockDepth")
@Throws(NoSuchMethodException::class)
private fun Class<*>.reflectionMethod(name: String, vararg args: Class<*>): Method {
    // TODO: Complex search as for dynamic methods ?
    var ex: Throwable? = null

    var method: Method? = null
    try {
        method = getDeclaredMethod(name, *args)
    } catch (e: Throwable) {
        ex = e
    }

    if (method == null)
        try {
            @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
            method = getMethod(name, *args)!!
        } catch (e: Throwable) {
            if (ex != null) {
                when (e.cause) {
                    null -> e.initCause(ex)
                    else -> e.addSuppressed(ex)
                }
            }
            throw e
        }

    method.setAccessibleSafe()
    return method
}

internal fun Class<*>.reflectionMethodOrNull(name: String): Method? {
    var method: Method? = null
    try {
        method = reflectionMethod(name)
    } catch (_: Throwable) {
    }
    return method
}

internal fun Class<*>.reflectionMethodOrNull(name: String, vararg args: Class<*>): Method? {
    var method: Method? = null
    try {
        method = reflectionMethod(name, *args)
    } catch (_: Throwable) {
    }
    return method
}


@Throws(NoSuchMethodException::class)
internal fun Class<*>.invokeStaticMethod(name: String): Any? {
    return reflectionMethod(name)(null)
}


@Throws(NoSuchFieldException::class)
private fun Class<*>.getReflectionField(name: String, writeAccess: Boolean = true): Field {
    var field: Field?
    var curClass: Class<in Any> = this as Class<in Any>
    while (true) {
        field = curClass.declaredFields.firstOrNull { it.name == name }
        if (field != null) break
        curClass = curClass.superclass ?: break
    }
    field ?: throw NoSuchFieldException(name)
    field.setAccessibleSafe()
    if (writeAccess) {
        val modifiers = field.modifiers
        if (modifiers and Modifier.FINAL == Modifier.FINAL) {
            try {
                @Suppress("MagicNumber")
                fieldSlot?.set(field, 3)
                fieldModifiers?.setInt(field, modifiers and Modifier.FINAL.inv())
            } catch (_: Throwable) {
            }
        }
    }
    return field
}

private val fieldModifiers: Field? by lazy(mode = LazyThreadSafetyMode.NONE) {
    var field: Field? = null
    try {
        // Works only in JVM.
        // https://stackoverflow.com/a/11234135/1816338
        field = Field::class.java.getDeclaredField("modifiers")
        field?.isAccessible = true
    } catch (_: Throwable) {
    }
    if (field == null && IS_ANDROID) {
        try {
            // Works only in Android
            // https://stackoverflow.com/questions/13755117/android-changing-private-static-final-field-using-java-reflection/21316501#comment80654176_21316501
            // https://github.com/BaoBaoJianqiang/TestReflection2/issues/1#issue-410106728
            field = Field::class.java.getDeclaredField("accessFlags")
            field?.isAccessible = true
        } catch (_: Throwable) {
        }
    }
    field
}
private val fieldSlot: Field? by lazy(mode = LazyThreadSafetyMode.NONE) {
    var field: Field? = null
    if (IS_ANDROID && fieldModifiers == null) {
        try {
            // Needed only in Android
            // https://stackoverflow.com/a/21316501/1816338
            field = Field::class.java.getDeclaredField("slot")
            field?.isAccessible = true
        } catch (_: Throwable) {
        }
    }
    field
}

private fun AccessibleObject.setAccessibleSafe() {
    try {
        @Suppress("DEPRECATION")
        if (isAccessible) {
            return
        }
        isAccessible = true
    } catch (_: RuntimeException) {
        // setAccessible not allowed by security policy
        // InaccessibleObjectException: Unable to make [..] accessible (JDK9+)
    }
}


internal fun classForNameOrNull(className: String): Class<*>? {
    return try {
        Class.forName(className)
    } catch (_: Throwable) {
        null
    }
}
