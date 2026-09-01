@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    dev.dimension.flare.ui.navigation.ExperimentalFlareNavigation::class,
)

package dev.dimension.flare.ui.navigation

import androidx.navigation3.runtime.NavEntry
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareNativeControllerOwner
import dev.dimension.flare.ui.FlareSubcomposition
import dev.dimension.flare.ui.FlareSubcompositionFactory
import dev.dimension.flare.ui.ProvideFlareNativeControllerOwner

/** Owns the independently disposable Flare content rendered by one native entry controller. */
internal class NavigationEntryContentHost(
    root: FlareChildren,
    private val nativeControllerOwner: FlareNativeControllerOwner?,
    subcompositions: FlareSubcompositionFactory,
    initialEntry: ResolvedNavigationEntry,
) {
    private val composition: FlareSubcomposition = subcompositions.create(root)
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
            ProvideFlareNativeControllerOwner(nativeControllerOwner) {
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
