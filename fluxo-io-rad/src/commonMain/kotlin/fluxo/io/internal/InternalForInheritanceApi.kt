package fluxo.io.internal

/**
 * Marks declarations that cannot be safely inherited from.
 */
@Target(AnnotationTarget.CLASS)
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "API that is not intended to be inherited from.",
)
public annotation class InternalForInheritanceApi
