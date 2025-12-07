package dev.dimension.flare.common

import androidx.compose.runtime.mutableStateMapOf
import dev.dimension.flare.ui.route.FloatingWindowState
import dev.dimension.flare.ui.route.Route

internal class FlareWindowManager {
    private val windows = mutableStateMapOf<String, FloatingWindowState>()

    fun containsKey(key: String): Boolean = windows.containsKey(key)

    operator fun get(key: String): FloatingWindowState? = windows[key]

    fun put(
        key: String,
        state: FloatingWindowState,
    ) {
        windows[key] = state
    }

    fun put(
        key: String,
        route: Route.WindowRoute,
    ) {
        windows[key] = FloatingWindowState(route)
    }

    fun remove(key: String) {
        windows.remove(key)
    }

    fun clear() {
        windows.clear()
    }

    inline fun forEach(action: (Map.Entry<String, FloatingWindowState>) -> Unit) {
        windows.forEach(action)
    }
}
