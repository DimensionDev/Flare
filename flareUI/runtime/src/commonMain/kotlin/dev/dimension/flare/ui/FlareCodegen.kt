package dev.dimension.flare.ui

/**
 * Marks a common interface as the complete public definition of one primitive.
 *
 * The interface must expose one composable `operator fun invoke`. Code generation turns it into a
 * callable object, a typed widget contract, and a strongly typed component token.
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
public annotation class FlarePrimitive

/** Marks generated widget contracts for platform renderer code generation. */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
@LowLevelFlareApi
public annotation class FlareWidgetContract

/**
 * Marks one platform-native implementation for automatic plugin registration.
 *
 * The primitive is inferred from the generated widget contract and the backend from
 * [FlareBackendWidget].
 */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS)
public annotation class FlareRenderer
