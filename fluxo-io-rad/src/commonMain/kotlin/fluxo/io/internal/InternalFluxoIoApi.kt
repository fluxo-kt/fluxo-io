package fluxo.io.internal

/**
 * Marks declarations that cannot be safely used and/or inherited from.
 */
@Target(AnnotationTarget.CLASS)
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "API that is internal and/or not intended to be inherited from.",
)
internal annotation class InternalFluxoIoApi
