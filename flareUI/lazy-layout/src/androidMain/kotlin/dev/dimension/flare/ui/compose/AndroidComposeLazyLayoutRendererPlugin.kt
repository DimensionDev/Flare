@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.lazy.LazyCollectionCoordinator
import dev.dimension.flare.ui.lazy.LazyCollectionModel
import dev.dimension.flare.ui.lazy.LazyCollectionWidget
import dev.dimension.flare.ui.lazy.LazyCrossAxisAlignment
import dev.dimension.flare.ui.lazy.LazyItemHost
import dev.dimension.flare.ui.lazy.LazyListItemInfo
import dev.dimension.flare.ui.lazy.LazyListLayoutInfo
import dev.dimension.flare.ui.lazy.LazyListOrientation
import dev.dimension.flare.ui.lazy.LazyListScrollRequest
import dev.dimension.flare.ui.lazy.LazyRealizedItemUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.LazyColumn as ComposeLazyColumn
import androidx.compose.foundation.lazy.LazyListState as ComposeLazyListState
import androidx.compose.foundation.lazy.LazyRow as ComposeLazyRow

/** Jetpack Compose LazyColumn/LazyRow renderer for Flare lazy collections. */
public object AndroidComposeLazyLayoutRendererPlugin : FlareRendererPlugin<AndroidComposeBackend> {
    override fun register(registrar: FlareWidgetRegistrar<AndroidComposeBackend>) {
        registrar.register(LazyCollectionWidget::class) { _ ->
            AndroidComposeLazyCollectionWidget()
        }
    }
}

private class AndroidComposeLazyCollectionWidget :
    AbstractAndroidComposeWidget(),
    LazyCollectionWidget {
    private var renderedModel: LazyCollectionModel? by mutableStateOf(null)
    private var scrollExecutor: ((LazyListScrollRequest) -> Unit)? = null
    private val pendingScrolls = mutableListOf<LazyListScrollRequest>()
    private val scrollJobs = mutableMapOf<LazyListScrollRequest, Job>()
    private val coordinator =
        LazyCollectionCoordinator(
            owner = this,
            onModelChanged = { _, current ->
                renderedModel = current
                LazyRealizedItemUpdate.RendererManaged
            },
            onScroll = ::performScroll,
            onScrollCancelled = ::cancelScroll,
            uiDispatcher = Dispatchers.Main.immediate,
        )

    override fun setModel(model: LazyCollectionModel) {
        coordinator.setModel(model)
    }

    @Composable
    @UiComposable
    override fun Render() {
        val model = renderedModel ?: return
        val state = remember(model.orientation) { ComposeLazyListState() }
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current.density
        InstallScrollExecutor(state, scope, density)
        ReportState(state, model)

        when (model.orientation) {
            LazyListOrientation.Vertical -> {
                ComposeLazyColumn(
                    modifier = composeModifier,
                    state = state,
                    verticalArrangement = Arrangement.spacedBy(model.spacing.dp),
                    horizontalAlignment = model.crossAxisAlignment.horizontalAlignment(),
                ) {
                    items(
                        count = model.itemProvider.itemCount,
                        key = { index -> coordinator.itemIdentities.idFor(model.itemProvider.key(index)) },
                        contentType = model.itemProvider::contentType,
                    ) { index ->
                        RenderItem(model, index)
                    }
                }
            }

            LazyListOrientation.Horizontal -> {
                ComposeLazyRow(
                    modifier = composeModifier,
                    state = state,
                    horizontalArrangement = Arrangement.spacedBy(model.spacing.dp),
                    verticalAlignment = model.crossAxisAlignment.verticalAlignment(),
                ) {
                    items(
                        count = model.itemProvider.itemCount,
                        key = { index -> coordinator.itemIdentities.idFor(model.itemProvider.key(index)) },
                        contentType = model.itemProvider::contentType,
                    ) { index ->
                        RenderItem(model, index)
                    }
                }
            }
        }
    }

    override fun dispose() {
        pendingScrolls.forEach(LazyListScrollRequest::cancel)
        pendingScrolls.clear()
        scrollJobs.values.toList().forEach(Job::cancel)
        scrollJobs.clear()
        scrollExecutor = null
        renderedModel = null
        coordinator.dispose()
    }

    @Composable
    @UiComposable
    private fun RenderItem(
        model: LazyCollectionModel,
        index: Int,
    ) {
        val key = model.itemProvider.key(index)
        val contentType = model.itemProvider.contentType(index)
        val root = remember(key, contentType) { AndroidComposeChildren() }
        val itemHost = remember(root) { coordinator.createItemHost(root) }
        var realized by remember(root) { mutableStateOf(false) }

        DisposableEffect(itemHost, model.itemProvider, index, key, contentType) {
            itemHost.bind(index)
            realized = true
            onDispose {}
        }
        DisposableEffect(itemHost) {
            onDispose(itemHost::dispose)
        }

        val modifier =
            when (model.orientation) {
                LazyListOrientation.Vertical -> {
                    Modifier
                        .fillMaxWidth()
                        .then(if (realized) Modifier else Modifier.height(INITIAL_ITEM_ESTIMATE))
                }

                LazyListOrientation.Horizontal -> {
                    Modifier
                        .fillMaxHeight()
                        .then(if (realized) Modifier else Modifier.width(INITIAL_ITEM_ESTIMATE))
                }
            }
        LazyItemRoot(
            orientation = model.orientation,
            crossAxisAlignment = model.crossAxisAlignment,
            modifier = modifier,
        ) {
            root.Render()
        }
    }

    @Composable
    @UiComposable
    private fun InstallScrollExecutor(
        state: ComposeLazyListState,
        scope: CoroutineScope,
        density: Float,
    ) {
        DisposableEffect(state, scope, density) {
            val executor: (LazyListScrollRequest) -> Unit = { request ->
                val job =
                    scope.launch(start = CoroutineStart.LAZY) {
                        try {
                            val offset = (request.scrollOffset * density).roundToInt()
                            if (request.animated) {
                                state.animateScrollToItem(request.index, offset)
                            } else {
                                state.scrollToItem(request.index, offset)
                            }
                            request.complete()
                        } catch (_: Exception) {
                            request.cancel()
                        } finally {
                            scrollJobs.remove(request)
                        }
                    }
                scrollJobs[request] = job
                job.start()
            }
            scrollExecutor = executor
            val pending = pendingScrolls.toList()
            pendingScrolls.clear()
            pending.forEach(executor)
            onDispose {
                if (scrollExecutor === executor) {
                    scrollExecutor = null
                }
            }
        }
    }

    @Composable
    @UiComposable
    private fun ReportState(
        state: ComposeLazyListState,
        model: LazyCollectionModel,
    ) {
        val density = LocalDensity.current.density
        LaunchedEffect(state, model.itemProvider, density) {
            snapshotFlow { state.layoutInfo to state.isScrollInProgress }
                .collect { (layoutInfo, scrolling) ->
                    coordinator.reportScrollInProgress(scrolling)
                    if (layoutInfo.totalItemsCount != model.itemProvider.itemCount) return@collect
                    coordinator.reportLayoutInfo(
                        LazyListLayoutInfo(
                            totalItemsCount = layoutInfo.totalItemsCount,
                            viewportStartOffset = layoutInfo.viewportStartOffset / density,
                            viewportEndOffset = layoutInfo.viewportEndOffset / density,
                            visibleItems =
                                layoutInfo.visibleItemsInfo.mapNotNull { item ->
                                    val rendererId = item.key as? Long
                                    val businessKey = rendererId?.let(coordinator.itemIdentities::keyFor)
                                    if (businessKey == null) return@mapNotNull null
                                    LazyListItemInfo(
                                        key = businessKey,
                                        index = item.index,
                                        offset = item.offset / density,
                                        size = item.size / density,
                                    )
                                },
                        ),
                    )
                }
        }
    }

    private fun performScroll(request: LazyListScrollRequest) {
        val executor = scrollExecutor
        if (executor == null) {
            pendingScrolls += request
        } else {
            executor(request)
        }
    }

    private fun cancelScroll(request: LazyListScrollRequest) {
        pendingScrolls.remove(request)
        scrollJobs.remove(request)?.cancel()
    }
}

private fun LazyCrossAxisAlignment.horizontalAlignment(): Alignment.Horizontal =
    when (this) {
        LazyCrossAxisAlignment.Start, LazyCrossAxisAlignment.Stretch -> Alignment.Start
        LazyCrossAxisAlignment.Center -> Alignment.CenterHorizontally
        LazyCrossAxisAlignment.End -> Alignment.End
    }

private fun LazyCrossAxisAlignment.verticalAlignment(): Alignment.Vertical =
    when (this) {
        LazyCrossAxisAlignment.Start, LazyCrossAxisAlignment.Stretch -> Alignment.Top
        LazyCrossAxisAlignment.Center -> Alignment.CenterVertically
        LazyCrossAxisAlignment.End -> Alignment.Bottom
    }

@Composable
@UiComposable
private fun LazyItemRoot(
    orientation: LazyListOrientation,
    crossAxisAlignment: LazyCrossAxisAlignment,
    modifier: Modifier,
    content: AndroidComposeContent,
) {
    Layout(
        content = content,
        modifier = modifier,
    ) { measurables, constraints ->
        when (orientation) {
            LazyListOrientation.Vertical -> {
                val childConstraints =
                    Constraints(
                        minWidth =
                            if (crossAxisAlignment == LazyCrossAxisAlignment.Stretch && constraints.hasBoundedWidth) {
                                constraints.maxWidth
                            } else {
                                0
                            },
                        maxWidth = constraints.maxWidth,
                        minHeight = 0,
                        maxHeight = Constraints.Infinity,
                    )
                val placeables = measurables.map { it.measure(childConstraints) }
                val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
                val height = placeables.sumOf { it.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
                layout(width, height) {
                    var y = 0
                    placeables.forEach { placeable ->
                        placeable.placeRelative(
                            x = crossAxisAlignment.position(width, placeable.width),
                            y = y,
                        )
                        y += placeable.height
                    }
                }
            }

            LazyListOrientation.Horizontal -> {
                val childConstraints =
                    Constraints(
                        minWidth = 0,
                        maxWidth = Constraints.Infinity,
                        minHeight =
                            if (crossAxisAlignment == LazyCrossAxisAlignment.Stretch && constraints.hasBoundedHeight) {
                                constraints.maxHeight
                            } else {
                                0
                            },
                        maxHeight = constraints.maxHeight,
                    )
                val placeables = measurables.map { it.measure(childConstraints) }
                val width = placeables.sumOf { it.width }.coerceIn(constraints.minWidth, constraints.maxWidth)
                val height = (placeables.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
                layout(width, height) {
                    var x = 0
                    placeables.forEach { placeable ->
                        placeable.placeRelative(
                            x = x,
                            y = crossAxisAlignment.position(height, placeable.height),
                        )
                        x += placeable.width
                    }
                }
            }
        }
    }
}

private fun LazyCrossAxisAlignment.position(
    available: Int,
    child: Int,
): Int =
    when (this) {
        LazyCrossAxisAlignment.Start, LazyCrossAxisAlignment.Stretch -> 0
        LazyCrossAxisAlignment.Center -> (available - child) / 2
        LazyCrossAxisAlignment.End -> available - child
    }

// ponytail: One conservative first-frame estimate prevents zero-sized items from realizing the
// whole data set. Add per-contentType estimates only when heterogeneous production lists need them.
private val INITIAL_ITEM_ESTIMATE = 48.dp
