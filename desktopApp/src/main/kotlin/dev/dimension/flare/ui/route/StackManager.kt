package dev.dimension.flare.ui.route

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.get
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.reflect.KClass
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Composable
internal fun rememberStackManager(
    startRoute: Route,
    key: Any? = null,
    topLevelRoutes: List<Route> = emptyList(),
): StackManager {
    val navControllerViewModel = viewModel<NavControllerViewModel>()
    val savableStateHolder = rememberSaveableStateHolder()

    return remember(key) {
        StackManager(
            startRoute = startRoute,
            navControllerViewModel = navControllerViewModel,
            savableStateHolder = savableStateHolder,
            topLevelRoutes = topLevelRoutes,
        )
    }
}

internal class StackManager(
    private val startRoute: Route,
    private val topLevelRoutes: List<Route>,
    private val navControllerViewModel: NavControllerViewModel,
    private val savableStateHolder: SaveableStateHolder,
) {
    val stack: MutableList<Entry> = mutableStateListOf()

    private var _current =
        mutableStateOf(
            stack.lastOrNull() ?: Entry(
                route = startRoute,
                viewModelStoreProvider = navControllerViewModel,
                savableStateHolder = savableStateHolder,
            ),
        )

    val current: Entry
        get() = _current.value

    private var _canGoBack = mutableStateOf(false)

    val canGoBack: Boolean
        get() = _canGoBack.value

    init {
        // Initialize the stack with the start route
        push(startRoute)
    }

    fun push(route: Route) {
        if (stack.isNotEmpty() && stack.last().route == route) {
            return
        }
        if (route in topLevelRoutes) {
            val entry = stack.find { it.route == route }
            if (entry != null) {
                // remove rest of the stack and set the entry as current
                for (item in stack) {
                    if (item != entry && item.route !in topLevelRoutes) {
                        item.setWillBeCleared()
                    }
                }
                stack.removeAll { it.route !in topLevelRoutes || it == entry }
                stack.add(entry)
            } else {
                val entry =
                    Entry(
                        route = route,
                        viewModelStoreProvider = navControllerViewModel,
                        savableStateHolder = savableStateHolder,
                    )
                stack.add(entry)
            }
        } else {
            val entry =
                Entry(
                    route = route,
                    viewModelStoreProvider = navControllerViewModel,
                    savableStateHolder = savableStateHolder,
                )
            stack.add(entry)
        }

        updateEntry()
    }

    fun pop() {
        if (stack.size > 1) {
            val item = stack.last()
            item.setWillBeCleared()
            stack.removeLast()
        } else {
            null
        }
        updateEntry()
    }

    private fun updateEntry() {
        if (stack.isNotEmpty()) {
            _current.value = stack.last()
        } else {
            _current.value =
                Entry(
                    route = startRoute,
                    viewModelStoreProvider = navControllerViewModel,
                    savableStateHolder = savableStateHolder,
                )
        }
        _canGoBack.value = stack.size > 1
    }

    fun clear() {
        for (item in stack) {
            item.setWillBeCleared()
        }
        stack.clear()
    }

    data class Entry
        @OptIn(ExperimentalUuidApi::class)
        constructor(
            private val id: String = Uuid.random().toString(),
            val route: Route,
            private val viewModelStoreProvider: ViewModelStoreProvider,
            private val savableStateHolder: SaveableStateHolder,
        ) : LifecycleOwner,
            ViewModelStoreOwner {
            private var willBeCleared = false
            private val lifecycleRegistry =
                androidx.lifecycle.LifecycleRegistry(this).apply {
                    handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                }

            override val lifecycle: Lifecycle
                get() = lifecycleRegistry

            fun active() {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }

            fun inactive() {
                if (willBeCleared) {
                    savableStateHolder.removeState(id)
                    viewModelStoreProvider.clear(id)
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
                    println("Entry $id is cleared and destroyed")
                } else {
                    lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                }
            }

            fun setWillBeCleared() {
                willBeCleared = true
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                    inactive()
                }
            }

            override val viewModelStore by lazy {
                viewModelStoreProvider.getViewModelStore(id)
            }

            @Composable
            fun Content(content: @Composable (route: Route) -> Unit) {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides this,
                    LocalViewModelStoreOwner provides this,
                ) {
                    savableStateHolder.SaveableStateProvider(id) {
                        content.invoke(route)
                    }
                }
                DisposableEffect(Unit) {
                    // Activate the lifecycle when the composable is composed
                    active()
                    onDispose {
                        // Inactive the lifecycle when the composable is disposed
                        inactive()
                    }
                }
            }
        }
}

interface ViewModelStoreProvider {
    fun getViewModelStore(backStackEntryId: String): ViewModelStore

    fun clear(backStackEntryId: String)
}

internal class NavControllerViewModel :
    ViewModel(),
    ViewModelStoreProvider {
    private val viewModelStores = mutableMapOf<String, ViewModelStore>()

    override fun clear(backStackEntryId: String) {
        // Clear and remove the NavGraph's ViewModelStore
        val viewModelStore = viewModelStores.remove(backStackEntryId)
        viewModelStore?.clear()
    }

    override fun onCleared() {
        for (store in viewModelStores.values) {
            store.clear()
        }
        viewModelStores.clear()
    }

    override fun getViewModelStore(backStackEntryId: String): ViewModelStore {
        var viewModelStore = viewModelStores[backStackEntryId]
        if (viewModelStore == null) {
            viewModelStore = ViewModelStore()
            viewModelStores[backStackEntryId] = viewModelStore
        }
        return viewModelStore
    }

    companion object {
        private val FACTORY: ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: KClass<T>,
                    extras: CreationExtras,
                ): T = NavControllerViewModel() as T
            }

        fun getInstance(viewModelStore: ViewModelStore): NavControllerViewModel {
            val viewModelProvider = ViewModelProvider.create(viewModelStore, FACTORY)
            return viewModelProvider.get()
        }
    }
}
