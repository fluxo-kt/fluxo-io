package fluxo.io.internal

/**
 * Marks declarations that are public only so fluxo-io's own modules (the Okio and kotlinx-io
 * adapters) can use them; they may change in any release. Public because Kotlin has no
 * module-friend visibility, and an opt-in ERROR so nobody else depends on them by accident.
 * It also guards implementing `RandomAccessData`: the lifetime rules every implementation
 * must keep live in the core, not in a subclass.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "fluxo-io internal API: for fluxo-io's own modules, may change in any release.",
)
@MustBeDocumented
public annotation class InternalFluxoIoApi
