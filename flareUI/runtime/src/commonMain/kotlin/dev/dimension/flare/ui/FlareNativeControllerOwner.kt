@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opaque native controller-containment owner supplied by a controller-aware platform host.
 *
 * Runtime deliberately assigns no navigation semantics to this owner. Optional modules provide
 * platform-specific owner implementations and interpret them at their renderer seam.
 */
@LowLevelFlareApi
public interface FlareNativeControllerOwner

private val LocalFlareNativeControllerOwner =
    staticCompositionLocalOf<FlareNativeControllerOwner?> { null }

/** Returns the nearest native controller owner, or null inside a view-only host. */
@LowLevelFlareApi
@Composable
@FlareUiComposable
public fun currentFlareNativeControllerOwner(): FlareNativeControllerOwner? = LocalFlareNativeControllerOwner.current

/** Provides a native controller owner to this Flare content and every deferred subcomposition. */
@LowLevelFlareApi
@Composable
@FlareUiComposable
public fun ProvideFlareNativeControllerOwner(
    owner: FlareNativeControllerOwner?,
    content: FlareContent,
) {
    CompositionLocalProvider(
        LocalFlareNativeControllerOwner provides owner,
        content = content,
    )
}
