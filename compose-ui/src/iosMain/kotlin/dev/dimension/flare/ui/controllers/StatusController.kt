package dev.dimension.flare.ui.controllers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.slapps.cupertino.CupertinoText
import com.slapps.cupertino.theme.CupertinoTheme
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.AnglesUp
import dev.dimension.flare.ui.component.ComponentAppearance
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.LocalComponentAppearance
import dev.dimension.flare.ui.component.ScrollToTopHandler
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.presenter.TimelineItemPresenter
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

@OptIn(ExperimentalComposeUiApi::class)
public fun TimelineController(
    state: ComposeUIStateProxy<TimelineItemPresenter.State>,
): UIViewController = FlareComposeUIViewController(state) { state ->
    CupertinoTheme {
        CompositionLocalProvider(
            LocalComponentAppearance provides ComponentAppearance()
        ) {
            val scope = rememberCoroutineScope()
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                ScrollToTopHandler(state.lazyListState)
                LazyStatusVerticalStaggeredGrid(
                    state = state.lazyListState,
                    contentPadding = WindowInsets.safeDrawing.asPaddingValues()
                ) {
                    status(state.listState)
                }
                AnimatedVisibility(
                    state.showNewToots,
                    enter = slideInVertically { -it } + fadeIn(),
                    exit = slideOutVertically { -it } + fadeOut(),
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.clickable {
                            state.onNewTootsShown()
                            scope.launch {
                                state.lazyListState.scrollToItem(0)
                            }
                        },
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.AnglesUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        CupertinoText(text = "sadsa")
                    }
                }
            }
        }
    }
}

@Stable
public class ComposeUIStateProxy<T>(
    initialState: T,
) {
    internal var onDispose: (() -> Unit)? = null
    internal var state by mutableStateOf(initialState)
        private set

    public fun update(newState: T) {
        if (newState != state) {
            state = newState
        }
    }

    public fun dispose() {
        onDispose?.invoke()
    }
}

public object ComposeUIStateProxyCache {
    private val cache = mutableMapOf<String, ComposeUIStateProxy<*>>()
    public fun <T> getOrCreate(key: String, factory: () -> T): ComposeUIStateProxy<T> {
        @Suppress("UNCHECKED_CAST")
        return cache.getOrPut(key) {
            ComposeUIStateProxy(factory())
        } as ComposeUIStateProxy<T>
    }
    public fun remove(key: String) {
        cache.remove(key)?.dispose()
    }
}

internal class ComposeLifecycleProxy(
    private val parentLifecycleOwner: LifecycleOwner,
    private val parentViewModelStoreOwner: ViewModelStoreOwner,
): LifecycleOwner, LifecycleEventObserver {
    private val lifecycleRegistry = androidx.lifecycle.LifecycleRegistry(this).apply {
        currentState = parentLifecycleOwner.lifecycle.currentState
        parentLifecycleOwner.lifecycle.addObserver(this@ComposeLifecycleProxy)
    }
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override fun onStateChanged(
        source: LifecycleOwner,
        event: Lifecycle.Event
    ) {
//        lifecycleRegistry.handleLifecycleEvent(event)
    }

    fun dispose() {
        parentLifecycleOwner.lifecycle.removeObserver(this)
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        parentViewModelStoreOwner.viewModelStore.clear()
    }
}

@OptIn(ExperimentalComposeUiApi::class)
internal fun <T> FlareComposeUIViewController(
    state: ComposeUIStateProxy<T>,
    content: @Composable (T) -> Unit
): UIViewController = ComposeUIViewController(
    configure = {
        parallelRendering = true
        enableBackGesture = false
        opaque = false
    }
) {
    val parentLifecycleOwner = checkNotNull(LocalLifecycleOwner.current) {
        "Parent ViewController is not a LifecycleOwner"
    }
    val parentViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
        "Parent ViewController is not a ViewModelStoreOwner"
    }
    val lifecycleProxy = remember(parentLifecycleOwner, parentViewModelStoreOwner) {
        ComposeLifecycleProxy(parentLifecycleOwner, parentViewModelStoreOwner)
    }
    DisposableEffect(lifecycleProxy) {
        state.onDispose = {
            lifecycleProxy.dispose()
        }
        onDispose {
            state.onDispose = null
        }
    }
    CompositionLocalProvider(
        androidx.lifecycle.compose.LocalLifecycleOwner provides parentLifecycleOwner,
        LocalViewModelStoreOwner provides parentViewModelStoreOwner,
    ) {
        content(state.state)
    }
}


