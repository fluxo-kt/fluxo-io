@file:Suppress("KDocUnresolvedReference", "RedundantSuppression")

package fluxo.io.internal

/**
 * Marks a class as thread-safe.
 * The class to which this annotation is applied is thread-safe.
 *
 * This means that no sequences of accesses (reads and writes to public fields,
 * calls to public methods) may put the object into an invalid state,
 * regardless of the interleaving of those actions by the runtime,
 * and without requiring any additional synchronization or coordination
 * on the part of the caller.
 *
 * This annotation is a hint to the compiler and the developer.
 * It does not enforce any thread-safety guarantees by itself.
 *
 * @see javax.annotation.concurrent.ThreadSafe
 */
@MustBeDocumented
@OptionalExpectation
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
@Suppress("GrazieInspection")
public expect annotation class ThreadSafe()
