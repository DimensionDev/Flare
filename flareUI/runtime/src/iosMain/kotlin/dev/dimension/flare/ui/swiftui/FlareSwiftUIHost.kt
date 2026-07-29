@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.swiftui

import androidx.compose.runtime.CompositionContext
import dev.dimension.flare.ui.FlareAppleComposition
import dev.dimension.flare.ui.FlareComposition
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.FlareWidgetSystem
import kotlin.native.HiddenFromObjC

/** Builds a typed SwiftUI widget system from node factories supplied by Swift plugins. */
@HiddenFromObjC
public fun createSwiftUIWidgetSystem(plugins: List<FlareSwiftUINodePlugin>): FlareWidgetSystem<SwiftUIBackend> =
    FlareWidgetSystem(
        *plugins
            .map { plugin ->
                object : FlareRendererPlugin<SwiftUIBackend> {
                    override fun register(registrar: FlareWidgetRegistrar<SwiftUIBackend>) {
                        plugin.install(
                            FlareSwiftUINodeRegistrar(
                                registrar = registrar,
                            ),
                        )
                    }
                }
            }.toTypedArray(),
    )

/**
 * Kotlin owner of one live SwiftUI renderer tree.
 *
 * Applications normally wrap this in their exported KMP framework and expose [content],
 * [setObserver], and [dispose] to Swift. With no [parent], the host uses Flare's shared production
 * Apple runtime. A custom parent lets tooling own the frame clock and Recomposer lifecycle.
 */
@HiddenFromObjC
public class FlareSwiftUIHost(
    private val tree: FlareSwiftUITree,
    widgetSystem: FlareWidgetSystem<SwiftUIBackend>,
    parent: CompositionContext? = null,
) {
    private val appleComposition: FlareAppleComposition<SwiftUIBackend>? =
        if (parent == null) {
            FlareAppleComposition(
                root = tree.root,
                widgetSystem = widgetSystem,
                backend = SwiftUIBackend(tree),
                hostName = "FlareSwiftUIHost",
            )
        } else {
            null
        }
    private val parentComposition: FlareComposition<SwiftUIBackend>? =
        parent?.let {
            FlareComposition(
                root = tree.root,
                widgetSystem = widgetSystem,
                backend = SwiftUIBackend(tree),
                parent = it,
            )
        }
    private var disposed = false

    /** Root child slot consumed by the SwiftUI host. */
    public val content: FlareSwiftUIChildren
        get() = tree.root

    public fun setObserver(observer: FlareSwiftUITreeObserver?) {
        check(!disposed) { "FlareSwiftUIHost is already disposed." }
        tree.setObserver(observer)
    }

    public fun setContent(content: FlareContent) {
        check(!disposed) { "FlareSwiftUIHost is already disposed." }
        appleComposition?.setContent(content)
            ?: checkNotNull(parentComposition).setContent(content)
    }

    public fun dispose() {
        if (disposed) return
        disposed = true
        tree.setObserver(null)
        appleComposition?.dispose()
            ?: checkNotNull(parentComposition).dispose()
        tree.dispose()
    }
}
