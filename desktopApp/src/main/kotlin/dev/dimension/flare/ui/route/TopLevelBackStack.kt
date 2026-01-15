package dev.dimension.flare.ui.route

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal class TopLevelBackStack(
    private val startKey: Route,
    private val topLevelRoutes: List<Route>,
) {
    private val _stack =
        mutableStateListOf(
            startKey,
        )
    val stack: ImmutableList<Route>
        get() = _stack.toImmutableList()

    var currentRoute by mutableStateOf(stack.last())
        private set

    var canGoBack: Boolean by mutableStateOf(false)
        private set

    fun push(route: Route) {
        if (currentRoute == route) {
            return
        }
        if (route in topLevelRoutes) {
            val entry = stack.find { it == route }
            if (entry != null) {
                // remove rest of the stack and set the entry as current
                _stack.removeAll { it !in topLevelRoutes || it == entry }
                _stack.add(entry)
            } else {
                _stack.add(route)
            }
        } else {
            _stack.add(route)
        }

        updateEntry()
    }

    fun pop() {
        if (stack.size > 1) {
            _stack.removeLast()
        }
        updateEntry()
    }

    private fun updateEntry() {
        currentRoute =
            if (stack.isNotEmpty()) {
                stack.last()
            } else {
                startKey
            }
        canGoBack = stack.any { it !in topLevelRoutes }
    }

    fun clear() {
        _stack.clear()
    }
}
