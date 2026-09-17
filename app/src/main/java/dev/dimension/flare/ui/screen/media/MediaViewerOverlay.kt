package dev.dimension.flare.ui.screen.media

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.LocalMediaTransitionSourceEnabled
import dev.dimension.flare.ui.component.LocalMediaTransitionSources
import dev.dimension.flare.ui.component.MediaTransitionSources
import dev.dimension.flare.ui.route.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

internal val LocalMediaViewerOverlayHost = compositionLocalOf<MediaViewerOverlayHostState?> { null }
internal val LocalMediaViewerOverlay = compositionLocalOf<MediaViewerOverlayState?> { null }

internal class MediaViewerOverlayHostState {
    val sources = MediaTransitionSources()
    val presentations = mutableStateListOf<MediaOverlayPresentation>()
}

/** Must surround the entire navigation scaffold, including both rail and bottom bar. */
@Composable
internal fun MediaViewerOverlayHost(content: @Composable BoxScope.() -> Unit) {
    val host = remember { MediaViewerOverlayHostState() }
    CompositionLocalProvider(
        LocalMediaViewerOverlayHost provides host,
        LocalMediaTransitionSources provides host.sources,
    ) {
        Box(Modifier.fillMaxSize().onGloballyPositioned { host.sources.coordinates = it }) {
            Box(
                Modifier.fillMaxSize().semantics {
                    if (host.presentations.isNotEmpty()) hideFromAccessibility()
                },
                content = content,
            )
            host.presentations.forEach { presentation ->
                key(presentation.state) {
                    CompositionLocalProvider(presentation.context) {
                        CompositionLocalProvider(
                            LocalMediaViewerOverlay provides presentation.state,
                            LocalMediaTransitionSourceEnabled provides false,
                        ) {
                            MediaOverlayLayer(presentation.state) { presentation.entry.Content() }
                        }
                    }
                }
            }
        }
    }
}

internal data class MediaViewport(
    val url: String,
    val previewUrl: String,
    val bounds: Rect,
    val contentScale: ContentScale,
    val alignment: Alignment,
    val headers: Map<String, String>?,
)

private data class MediaHero(
    val source: MediaTransitionSources.Snapshot,
    val viewport: MediaViewport,
)

internal class MediaViewerOverlayState(
    private val sources: MediaTransitionSources,
    route: Route.Media,
    private val onBack: () -> Unit,
) {
    private val origin = sources.takeOpeningSource(route.previewUrls())
    val progress = Animatable(0f)
    var viewport: MediaViewport? by mutableStateOf(null)
        private set
    var dragY by mutableFloatStateOf(0f)
    var hostSize by mutableStateOf(Size.Zero)
        private set
    private var hero by mutableStateOf<MediaHero?>(null)
    private var opening by mutableStateOf(true)
    private var returning by mutableStateOf(false)
    private var dismissRequested = false
    private var disposed = false
    val hasHero: Boolean get() = hero != null
    val isInteractive: Boolean get() = !opening && !returning && !dismissRequested
    val dragScale: Float get() = 1f - (abs(dragY) / hostSize.height.coerceAtLeast(1f)).coerceIn(0f, 1f) * 0.2f
    val backgroundAlpha: Float get() = progress.value * (1f - abs(dragY) / hostSize.height.coerceAtLeast(1f)).coerceIn(0f, 1f)

    fun updateViewport(value: MediaViewport) {
        if (!returning && dragY == 0f) viewport = value
    }

    fun selectMedia(
        url: String?,
        previewUrl: String?,
        hasVisual: Boolean,
    ) {
        if (returning || url == null) return
        if (!hasVisual || viewport?.let { it.url != url && it.previewUrl != previewUrl } == true) viewport = null
    }

    fun updateHostSize(value: Size) {
        if (hostSize != Size.Zero && hostSize != value) {
            hero = null
            sources.hiddenSource = null
        }
        hostSize = value
    }

    fun boundsOf(coordinates: LayoutCoordinates): Rect? {
        val root = sources.coordinates ?: return null
        if (!root.isAttached || !coordinates.isAttached) return null
        return root.localBoundingBoxOf(coordinates, clipBounds = false)
    }

    suspend fun open() {
        // The cached preview can lay out before the status presenter has loaded the original.
        if (origin != null) withTimeoutOrNull(350) { snapshotFlow { viewport }.first { it != null } }
        withFrameNanos { }
        if (dismissRequested || returning || disposed) return
        val target = viewport
        if (origin != null && target != null) {
            hero = MediaHero(origin, target)
            sources.hiddenSource = origin.id
        }
        progress.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
        if (!returning && !dismissRequested) {
            hero = null
            opening = false
            sources.hiddenSource = null
        }
    }

    fun beginReturn() {
        if (returning) return
        returning = true
        val target = viewport
        val source =
            if (origin != null && target != null) {
                sources.returningSource(origin, setOf(target.url, target.previewUrl))
            } else {
                null
            }
        if (source != null && target != null) {
            hero = MediaHero(source, target.copy(bounds = draggedBounds(target.bounds, hostSize, dragY, dragScale)))
            sources.hiddenSource = source.id
        } else {
            hero = null
            sources.hiddenSource = null
        }
    }

    suspend fun cancelReturn() {
        if (dismissRequested || disposed) return
        progress.animateTo(1f, tween(180))
        if (dismissRequested || disposed) return
        returning = false
        opening = false
        hero = null
        sources.hiddenSource = null
    }

    fun requestDismiss() {
        if (dismissRequested || disposed) return
        dismissRequested = true
        onBack()
    }

    suspend fun close() {
        dismissRequested = true
        if (!opening || returning) beginReturn()
        progress.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
        sources.hiddenSource = null
    }

    suspend fun resetDrag() {
        animate(dragY, 0f, animationSpec = tween(180)) { value, _ -> dragY = value }
    }

    fun dispose() {
        disposed = true
        sources.hiddenSource = null
    }

    @Composable
    fun HeroImage() {
        val current = hero ?: return
        val fraction = progress.value
        val source = current.source
        val target = current.viewport
        val bounds = lerp(source.bounds, target.bounds, fraction)
        val visibleBounds = lerp(source.visibleBounds, target.bounds, fraction).translate(-bounds.topLeft)
        val density = LocalDensity.current
        val direction = LocalLayoutDirection.current
        val outline = source.shape.createOutline(source.bounds.size, direction, density)
        val corners = (outline as? Outline.Rounded)?.roundRect
        val shape =
            AbsoluteRoundedCornerShape(
                topLeft = with(density) { ((corners?.topLeftCornerRadius?.x ?: 0f) * (1f - fraction)).toDp() },
                topRight = with(density) { ((corners?.topRightCornerRadius?.x ?: 0f) * (1f - fraction)).toDp() },
                bottomRight = with(density) { ((corners?.bottomRightCornerRadius?.x ?: 0f) * (1f - fraction)).toDp() },
                bottomLeft = with(density) { ((corners?.bottomLeftCornerRadius?.x ?: 0f) * (1f - fraction)).toDp() },
            )
        val context = LocalContext.current
        val headers = target.headers ?: source.headers
        val request =
            remember(source.previewUrl, headers, context) {
                ImageRequest
                    .Builder(context)
                    .data(source.previewUrl)
                    .memoryCacheKey(source.previewUrl)
                    .placeholderMemoryCacheKey(source.previewUrl)
                    .crossfade(false)
                    .apply {
                        headers?.let { values ->
                            httpHeaders(NetworkHeaders.Builder().apply { values.forEach { (name, value) -> set(name, value) } }.build())
                        }
                    }.build()
            }
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier =
                Modifier
                    .wrapContentSize(AbsoluteAlignment.TopLeft, unbounded = true)
                    .absoluteOffset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                    .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                    .testTag("media_hero")
                    .clip(shape)
                    .drawWithContent {
                        // Keep the original image scale even when only part of the thumbnail is visible.
                        clipRect(visibleBounds.left, visibleBounds.top, visibleBounds.right, visibleBounds.bottom) {
                            this@drawWithContent.drawContent()
                        }
                    },
            contentScale =
                InterpolatedContentScale(
                    source.contentScale,
                    target.contentScale,
                    source.bounds.size,
                    target.bounds.size,
                    fraction,
                ),
            alignment = InterpolatedAlignment(source.alignment, target.alignment, fraction),
            onError = {
                // A failed/evicted thumbnail must not leave the viewer invisible during a transition.
                if (hero === current) {
                    hero = null
                    sources.hiddenSource = null
                }
            },
        )
    }
}

@Composable
internal fun MediaOverlayLayer(
    state: MediaViewerOverlayState,
    content: @Composable () -> Unit,
) {
    val title = stringResource(R.string.media_viewer_title)
    val focus = remember { FocusRequester() }
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsState()
    LaunchedEffect(state) {
        focus.requestFocus()
        state.open()
    }
    // Registered before the viewer so ImageItem's reset-zoom BackHandler has priority.
    PredictiveBackHandler(enabled = lifecycle == Lifecycle.State.RESUMED) { events ->
        state.beginReturn()
        try {
            events.collect { state.progress.snapTo(1f - it.progress) }
            state.requestDismiss()
        } catch (_: CancellationException) {
            withContext(NonCancellable) { state.cancelReturn() }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .testTag("media_viewer_overlay")
            .focusRequester(focus)
            .focusProperties { onExit = { cancelFocusChange() } }
            .focusGroup()
            .focusable()
            .semantics {
                paneTitle = title
                isTraversalGroup = true
            }.onGloballyPositioned { state.updateHostSize(Size(it.size.width.toFloat(), it.size.height.toFloat())) }
            .background(Color.Black.copy(alpha = state.backgroundAlpha))
            .pointerInput(Unit) {
                // Own the full window, including the rail and bars, even where the content is transparent.
                awaitPointerEventScope {
                    while (true) {
                        val initial = awaitPointerEvent(PointerEventPass.Initial)
                        if (!state.isInteractive) initial.changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                alpha = if (state.hasHero) 0f else state.progress.value
                translationY = state.dragY
                scaleX = state.dragScale
                scaleY = state.dragScale
            },
        ) { content() }
        state.HeroImage()
    }
}

@Composable
internal fun Modifier.mediaViewerViewport(
    url: String,
    previewUrl: String,
    headers: Map<String, String>?,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
    enabled: Boolean = true,
): Modifier {
    val overlay = LocalMediaViewerOverlay.current ?: return this
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun update() {
        if (enabled) {
            coordinates?.let { overlay.boundsOf(it) }?.let {
                overlay.updateViewport(MediaViewport(url, previewUrl, it, contentScale, alignment, headers))
            }
        }
    }
    SideEffect { update() }
    return onGloballyPositioned {
        coordinates = it
        update()
    }
}

@Composable
internal fun MediaOverlayDismissArea(
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val overlay = checkNotNull(LocalMediaViewerOverlay.current)
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    Box(
        Modifier.fillMaxSize().pointerInput(overlay, enabled) {
            if (!enabled) return@pointerInput
            detectVerticalDragGestures(
                onVerticalDrag = { change, amount ->
                    if (overlay.isInteractive) {
                        change.consume()
                        overlay.dragY += amount
                    }
                },
                onDragEnd = {
                    if (abs(overlay.dragY) >= threshold) {
                        overlay.requestDismiss()
                    } else {
                        scope.launch { overlay.resetDrag() }
                    }
                },
                onDragCancel = { scope.launch { overlay.resetDrag() } },
            )
        },
    ) { content() }
}

internal fun draggedBounds(
    bounds: Rect,
    host: Size,
    offsetY: Float,
    scale: Float,
): Rect {
    val center = Offset(host.width / 2f, host.height / 2f)
    return Rect(
        topLeft = center + (bounds.topLeft - center) * scale + Offset(0f, offsetY),
        bottomRight = center + (bounds.bottomRight - center) * scale + Offset(0f, offsetY),
    )
}

internal class InterpolatedContentScale(
    private val from: ContentScale,
    private val to: ContentScale,
    private val fromSize: Size,
    private val toSize: Size,
    private val fraction: Float,
) : ContentScale {
    override fun computeScaleFactor(
        srcSize: Size,
        dstSize: Size,
    ): ScaleFactor {
        // Resolve both endpoints against their own viewport, not the growing intermediate box.
        val start = from.computeScaleFactor(srcSize, fromSize)
        val end = to.computeScaleFactor(srcSize, toSize)
        return ScaleFactor(start.scaleX + (end.scaleX - start.scaleX) * fraction, start.scaleY + (end.scaleY - start.scaleY) * fraction)
    }
}

private class InterpolatedAlignment(
    private val from: Alignment,
    private val to: Alignment,
    private val fraction: Float,
) : Alignment {
    override fun align(
        size: IntSize,
        space: IntSize,
        layoutDirection: LayoutDirection,
    ): IntOffset {
        val start = from.align(size, space, layoutDirection)
        val end = to.align(size, space, layoutDirection)
        return IntOffset((start.x + (end.x - start.x) * fraction).roundToInt(), (start.y + (end.y - start.y) * fraction).roundToInt())
    }
}

private fun Route.Media.previewUrls(): Set<String> =
    when (this) {
        is Route.Media.Image -> setOfNotNull(uri, previewUrl)
        is Route.Media.RawMedia -> setOfNotNull(preview, medias.getOrNull(index)?.url)
        is Route.Media.StatusMedia -> setOfNotNull(preview)
        is Route.Media.Podcast -> emptySet()
    }
