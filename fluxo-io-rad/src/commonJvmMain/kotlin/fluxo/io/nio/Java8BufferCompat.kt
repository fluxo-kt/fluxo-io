@file:JvmName("Java8BufferCompat")
@file:Suppress("KDocUnresolvedReference", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

/**
 * Wrappers around [Buffer] methods that are covariantly overridden in Java 9+.
 * See https://github.com/google/guava/issues/3990.
 *
 * @see com.google.common.hash.Java8Compatibility
 */

package fluxo.io.nio

import java.nio.Buffer
import kotlin.internal.InlineOnly


@InlineOnly
@JvmName("mark")
public inline fun Buffer.markCompat(): Buffer = mark()

@InlineOnly
@JvmName("reset")
public inline fun Buffer.resetCompat(): Buffer = reset()

@InlineOnly
@JvmName("clear")
public inline fun Buffer.clearCompat(): Buffer = clear()

@InlineOnly
@JvmName("flip")
public inline fun Buffer.flipCompat(): Buffer = flip()

@InlineOnly
@JvmName("limit")
public inline fun Buffer.limitCompat(limit: Int): Buffer = limit(limit)

@InlineOnly
@JvmName("position")
public inline fun Buffer.positionCompat(position: Int): Buffer = position(position)
