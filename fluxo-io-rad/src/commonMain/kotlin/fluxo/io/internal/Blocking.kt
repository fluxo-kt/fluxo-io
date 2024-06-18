@file:Suppress("KDocUnresolvedReference")

package fluxo.io.internal

/**
 * Indicates that the annotated method is inherently blocking
 * and should not be executed in a non-blocking context.
 *
 * @see org.jetbrains.annotations.Blocking
 */
@MustBeDocumented
@OptionalExpectation
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.CONSTRUCTOR)
public expect annotation class Blocking()
