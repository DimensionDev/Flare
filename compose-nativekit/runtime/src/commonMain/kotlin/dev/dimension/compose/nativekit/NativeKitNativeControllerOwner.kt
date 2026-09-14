@file:OptIn(dev.dimension.compose.nativekit.LowLevelNativeKitApi::class)

package dev.dimension.compose.nativekit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opaque native controller-containment owner supplied by a controller-aware platform host.
 *
 * Runtime deliberately assigns no navigation semantics to this owner. Optional modules provide
 * platform-specific owner implementations and interpret them at their renderer seam.
 */
@LowLevelNativeKitApi
public interface NativeKitNativeControllerOwner

private val LocalNativeKitNativeControllerOwner =
    staticCompositionLocalOf<NativeKitNativeControllerOwner?> { null }

/** Returns the nearest native controller owner, or null inside a view-only host. */
@LowLevelNativeKitApi
@Composable
@NativeKitComposable
public fun currentNativeKitNativeControllerOwner(): NativeKitNativeControllerOwner? = LocalNativeKitNativeControllerOwner.current

/** Provides a native controller owner to this NativeKit content and every deferred subcomposition. */
@LowLevelNativeKitApi
@Composable
@NativeKitComposable
public fun ProvideNativeKitNativeControllerOwner(
    owner: NativeKitNativeControllerOwner?,
    content: NativeKitContent,
) {
    CompositionLocalProvider(
        LocalNativeKitNativeControllerOwner provides owner,
        content = content,
    )
}
