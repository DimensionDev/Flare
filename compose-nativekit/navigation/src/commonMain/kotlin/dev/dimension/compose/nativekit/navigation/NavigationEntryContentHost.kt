@file:OptIn(
    dev.dimension.compose.nativekit.LowLevelNativeKitApi::class,
    dev.dimension.compose.nativekit.navigation.ExperimentalNativeKitNavigation::class,
)

package dev.dimension.compose.nativekit.navigation

import androidx.navigation3.runtime.NavEntry
import dev.dimension.compose.nativekit.NativeKitChildren
import dev.dimension.compose.nativekit.NativeKitNativeControllerOwner
import dev.dimension.compose.nativekit.NativeKitSubcomposition
import dev.dimension.compose.nativekit.NativeKitSubcompositionFactory
import dev.dimension.compose.nativekit.ProvideNativeKitNativeControllerOwner

/** Owns the independently disposable NativeKit content rendered by one native entry controller. */
internal class NavigationEntryContentHost(
    root: NativeKitChildren,
    private val nativeControllerOwner: NativeKitNativeControllerOwner?,
    subcompositions: NativeKitSubcompositionFactory,
    initialEntry: ResolvedNavigationEntry,
) {
    private val composition: NativeKitSubcomposition = subcompositions.create(root)
    private var disposed: Boolean = false
    private var active: Boolean = false
    private var installedEntry: NavEntry<*>? = null

    var entry: ResolvedNavigationEntry = initialEntry
        private set

    init {
        activate()
    }

    fun update(value: ResolvedNavigationEntry) {
        check(!disposed) { "Navigation entry content host is already disposed." }
        require(value.identity() == entry.identity()) {
            "A navigation entry content host cannot change identity."
        }
        entry = value
        if (active) install(value)
    }

    fun activate() {
        check(!disposed) { "Navigation entry content host is already disposed." }
        if (active) return
        active = true
        install(entry)
    }

    fun deactivate() {
        check(!disposed) { "Navigation entry content host is already disposed." }
        if (!active) return
        active = false
        installedEntry = null
        composition.deactivate()
    }

    private fun install(value: ResolvedNavigationEntry) {
        if (installedEntry === value.entry) return
        installedEntry = value.entry
        composition.setContent {
            ProvideNativeKitNativeControllerOwner(nativeControllerOwner) {
                value.entry.Content()
            }
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        composition.dispose()
    }
}
