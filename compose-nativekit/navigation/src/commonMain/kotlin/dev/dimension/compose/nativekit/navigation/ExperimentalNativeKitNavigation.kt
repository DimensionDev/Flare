package dev.dimension.compose.nativekit.navigation

/** Marks the first, evolving release of NativeKit's cross-platform navigation API. */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "NativeKit Navigation is experimental and may change without notice.",
)
@Retention(AnnotationRetention.BINARY)
public annotation class ExperimentalNativeKitNavigation
