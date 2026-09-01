package dev.dimension.flare.ui.navigation

import androidx.navigation3.runtime.NavMetadataKey

/**
 * Cross-platform presentation intent interpreted by the active renderer.
 *
 * The current renderer set supports [Page] only. The overlay values are reserved for the planned
 * native presentation chain and fail fast when passed to any currently shipped adapter; callers
 * must not use them as a capability signal.
 */
@ExperimentalFlareNavigation
public enum class NavigationPresentation {
    Page,
    Dialog,
    Sheet,
    Fullscreen,
}

/** Navigation3 metadata key used to select a [NavigationPresentation]. */
@ExperimentalFlareNavigation
public object NavigationPresentationMetadata : NavMetadataKey<NavigationPresentation> {
    override fun toString(): String = "dev.dimension.flare.ui.navigation.presentation"
}
