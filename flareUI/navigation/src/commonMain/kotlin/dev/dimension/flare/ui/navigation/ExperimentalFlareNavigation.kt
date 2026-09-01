package dev.dimension.flare.ui.navigation

/** Marks the first, evolving release of Flare's cross-platform navigation API. */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Flare Navigation is experimental and may change without notice.",
)
@Retention(AnnotationRetention.BINARY)
public annotation class ExperimentalFlareNavigation
