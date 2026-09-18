package dev.dimension.flare.ui.screen.media

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.LocalMediaSharedDestination
import dev.dimension.flare.ui.component.LocalMediaSharedSourcesEnabled
import dev.dimension.flare.ui.component.LocalMediaSharedTransition
import dev.dimension.flare.ui.component.MediaSharedElementDestination
import dev.dimension.flare.ui.component.MediaSharedTransitionState
import dev.dimension.flare.ui.route.Route
import kotlinx.coroutines.flow.first

internal val LocalMediaSharedTransitionHost = compositionLocalOf<MediaSharedTransitionHostState?> { null }
internal val LocalMediaSharedDismiss = compositionLocalOf<(() -> Unit)?> { null }

internal class MediaSharedTransitionHostState(
    val shared: MediaSharedTransitionState,
) {
    val presentations = mutableStateListOf<MediaSharedPresentation>()
}

internal class MediaSharedPresentation(
    val entry: NavEntry<NavKey>,
    route: Route.Media,
    val shared: MediaSharedTransitionState,
    private val onBack: () -> Unit,
    context: CompositionLocalContext,
) {
    val group = shared.pressedKey?.takeIf { it.preview in route.previews() }?.group
    val visibility = MutableTransitionState(false).apply { targetState = true }
    var context by mutableStateOf(context)
    private var dismissRequested = false

    init {
        // Hide the source before composing the destination so they never both target visible.
        shared.hiddenKey = shared.pressedKey?.takeIf { it.group == group }
        shared.pressedKey = null
    }

    fun dismiss() {
        if (!dismissRequested) {
            dismissRequested = true
            onBack()
        }
    }

    suspend fun close() {
        visibility.targetState = false
        shared.hiddenKey = null
        withFrameNanos { }
        snapshotFlow { visibility.isIdle && !shared.scope.isTransitionActive }.first { it }
    }
}

/** The shared-element overlay and media content both cover the entire navigation scaffold. */
@Composable
internal fun MediaSharedTransitionHost(content: @Composable BoxScope.() -> Unit) {
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        val windowInfo = LocalWindowInfo.current
        val shared = remember(windowInfo) { MediaSharedTransitionState(this, windowInfo) }
        val host = remember(shared) { MediaSharedTransitionHostState(shared) }
        CompositionLocalProvider(
            LocalMediaSharedTransitionHost provides host,
            LocalMediaSharedTransition provides shared,
        ) {
            Box(
                Modifier.fillMaxSize().semantics {
                    if (host.presentations.isNotEmpty()) hideFromAccessibility()
                },
                content = content,
            )
            host.presentations.forEach { presentation ->
                key(presentation) {
                    CompositionLocalProvider(presentation.context) {
                        AnimatedVisibility(
                            visibleState = presentation.visibility,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            CompositionLocalProvider(
                                LocalMediaSharedDismiss provides presentation::dismiss,
                                LocalMediaSharedSourcesEnabled provides false,
                                LocalMediaSharedDestination provides
                                    remember {
                                        MediaSharedElementDestination(presentation.group, this)
                                    },
                            ) {
                                val title = stringResource(R.string.media_viewer_title)
                                BackHandler(onBack = presentation::dismiss)
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .testTag("media_shared_viewer")
                                        .semantics { paneTitle = title }
                                        .pointerInput(Unit) { detectTapGestures { } },
                                ) {
                                    presentation.entry.Content()
                                }
                            }
                        }
                    }
                    DisposableEffect(presentation) {
                        onDispose {
                            shared.hiddenKey = null
                            shared.dragging = false
                        }
                    }
                }
            }
        }
    }
}

private fun Route.Media.previews(): List<String?> =
    when (this) {
        is Route.Media.Image -> listOf(previewUrl, uri)
        is Route.Media.RawMedia -> listOf(preview, medias.getOrNull(index)?.previewKey())
        is Route.Media.StatusMedia -> listOf(preview)
        is Route.Media.Podcast -> emptyList()
    }
