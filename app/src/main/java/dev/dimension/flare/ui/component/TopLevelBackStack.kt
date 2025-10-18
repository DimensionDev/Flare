package dev.dimension.flare.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.navigation3.runtime.NavKey

internal class TopLevelBackStack<T : NavKey>(
    startKey: T,
) {
    // Maintain a stack for each top level route
    private var topLevelStacks: LinkedHashMap<T, SnapshotStateList<T>> =
        linkedMapOf(
            startKey to mutableStateListOf(startKey),
        )

    // Expose the current top level route for consumers
    var topLevelKey by mutableStateOf(startKey)
        private set

    // Expose the back stack so it can be rendered by the NavDisplay
    val backStack = mutableStateListOf(startKey)

    private fun updateBackStack() =
        backStack.apply {
            clear()
            addAll(topLevelStacks.flatMap { it.value })
        }

    fun addTopLevel(key: T) {
        // If the top level doesn't exist, add it
        if (topLevelStacks[key] == null) {
            topLevelStacks.put(key, mutableStateListOf(key))
        } else {
            // Otherwise just move it to the end of the stacks
            topLevelStacks.apply {
                remove(key)?.let {
                    put(key, it)
                }
            }
        }
        topLevelKey = key
        updateBackStack()
    }

    fun add(key: T) {
        val last =
            topLevelStacks[topLevelKey]?.lastOrNull()
                ?: throw IllegalStateException("No stack found for top level key: $topLevelKey")
        if (last == key) {
            // If the key is already the last one, do nothing
            return
        }
        topLevelStacks[topLevelKey]?.add(key)
        updateBackStack()
    }

    fun removeLast() {
        if (!canRemoveLast()) {
            return
        }
        val removedKey = topLevelStacks[topLevelKey]?.removeLastOrNull()
        // If the removed key was a top level key, remove the associated top level stack
        topLevelStacks.remove(removedKey)
        topLevelKey = topLevelStacks.keys.last()
        updateBackStack()
    }

    fun canRemoveLast(): Boolean = (topLevelStacks[topLevelKey]?.size ?: 0) > 1 || topLevelStacks.size > 1

    fun isInBackStack(key: T): Boolean =
        topLevelStacks.values.any { stack ->
            stack.contains(key)
        }
}
