@file:Suppress("KDocUnresolvedReference")

package fluxo.io.internal

/**
 * Denotes that any overriding methods should invoke this method as well.
 *
 * @see androidx.annotation.CallSuper
 */
@MustBeDocumented
@OptionalExpectation
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
public expect annotation class CallSuper()
