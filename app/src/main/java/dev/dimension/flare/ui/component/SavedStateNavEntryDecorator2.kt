package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.navEntryDecorator
import androidx.savedstate.SavedState
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import androidx.savedstate.savedState

/**
 * Returns a [SavedStateNavEntryDecorator] that is remembered across recompositions.
 *
 * @param saveableStateHolder the [SaveableStateHolder] that scopes the returned NavEntryDecorator
 */
@Composable
internal inline fun <reified T : NavKey> rememberSavedStateNavEntryDecorator2(
    saveableStateHolder: SaveableStateHolder = rememberSaveableStateHolder(),
    crossinline shouldRemoveState: (key: T) -> Boolean = { true },
): NavEntryDecorator<T> = remember { savedStateNavEntryDecorator2<T>(saveableStateHolder, shouldRemoveState) }

/**
 * Wraps the content of a [NavEntry] with a [SaveableStateHolder.SaveableStateProvider] to ensure
 * that calls to [rememberSaveable] within the content work properly and that state can be saved.
 * Also provides the content of a [NavEntry] with a [SavedStateRegistryOwner] which can be accessed
 * in the content with [LocalSavedStateRegistryOwner].
 *
 * This [NavEntryDecorator] is the only one that is **required** as saving state is considered a
 * non-optional feature.
 */
internal inline fun <reified T : NavKey> savedStateNavEntryDecorator2(
    saveableStateHolder: SaveableStateHolder,
    crossinline shouldRemoveState: (key: T) -> Boolean = { true },
): NavEntryDecorator<T> {
    val registryMap = mutableMapOf<String, EntrySavedStateRegistry>()

    val onPop: (Any) -> Unit = { key ->
        val shouldRemove =
            if (key is T) {
                shouldRemoveState(key)
            } else {
                // If the key is not of type T, we assume we should not remove the state
                false
            }
        val id = getIdForKey(key)
        if (registryMap.contains(id) && shouldRemove) {
            // saveableStateHolder onPop
            saveableStateHolder.removeState(id)

            // saved state onPop
            val savedState = savedState()
            val childRegistry = registryMap.getValue(id)
            childRegistry.savedStateRegistryController.performSave(savedState)
            childRegistry.savedState = savedState
            childRegistry.lifecycle.currentState = Lifecycle.State.DESTROYED
        }
    }

    return navEntryDecorator<T>(onPop = onPop) { entry ->
        val key = entry.key
        val id = getIdForKey(key)

        val childRegistry by
            rememberSaveable(
                key,
                stateSaver =
                    Saver(
                        save = { it.savedState },
                        restore = { EntrySavedStateRegistry().apply { savedState = it } },
                    ),
            ) {
                mutableStateOf(EntrySavedStateRegistry())
            }
        registryMap.put(id, childRegistry)

        saveableStateHolder.SaveableStateProvider(id) {
            CompositionLocalProvider(LocalSavedStateRegistryOwner provides childRegistry) {
                entry.content.invoke(entry.key)
            }
        }
        childRegistry.lifecycle.currentState = Lifecycle.State.RESUMED
    }
}

internal fun getIdForKey(key: Any): String = "${key::class.qualifiedName}:$key"

internal class EntrySavedStateRegistry : SavedStateRegistryOwner {
    override val lifecycle: LifecycleRegistry = LifecycleRegistry(this)
    val savedStateRegistryController: SavedStateRegistryController =
        SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry =
        savedStateRegistryController.savedStateRegistry
    var savedState: SavedState? = null

    init {
        savedStateRegistryController.performRestore(savedState)
    }
}
