package dev.dimension.flare.ui.screen.media

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.component.status.CommonStatusComponent
import dev.dimension.flare.ui.component.status.StatusSummaryComponent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

/** Only the sheet participates in visibility transitions; media stays mounted underneath it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaPostSheet(
    post: UiTimelineV2.Post?,
    quotes: ImmutableList<UiTimelineV2.Post>,
    visible: Boolean,
    uriHandler: UriHandler,
    content: @Composable (peekHeight: Dp) -> Unit,
) {
    val density = LocalDensity.current
    var summaryHeight by remember { mutableStateOf(80.dp) }
    var handleHeight by remember { mutableStateOf(48.dp) }
    val showSheet = visible && post != null
    val motionScheme = MaterialTheme.motionScheme
    val visibility = updateTransition(showSheet, label = "Media post visibility")
    val bottomInset by
        visibility.animateDp(
            transitionSpec = { motionScheme.defaultSpatialSpec() },
            label = "Media controls inset",
        ) {
            if (it) summaryHeight + handleHeight else 0.dp
        }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expandedHeight = (maxHeight * 0.9f - handleHeight).coerceAtLeast(summaryHeight)
        content(bottomInset.coerceAtLeast(0.dp))
        visibility.AnimatedVisibility(
            visible = { it },
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(motionScheme.defaultEffectsSpec()),
            exit = fadeOut(motionScheme.defaultEffectsSpec()),
        ) {
            val sheetState =
                rememberBottomSheetState(
                    initialValue = SheetValue.PartiallyExpanded,
                    enabledValues = setOf(SheetValue.PartiallyExpanded, SheetValue.Expanded),
                )
            val scope = rememberCoroutineScope()
            val postTransitionState = remember(post?.statusKey, post?.accountType) { SeekableTransitionState(false) }
            val postTransition = rememberTransition(postTransitionState, label = "Media post expansion")
            var progress by remember { mutableFloatStateOf(0f) }
            val collapsedOffset = with(density) { maxHeight.roundToPx() - (summaryHeight + handleHeight).roundToPx() }
            val expandedOffset = with(density) { maxHeight.roundToPx() - expandedHeight.roundToPx() - handleHeight.roundToPx() }
            LaunchedEffect(sheetState, postTransitionState, collapsedOffset, expandedOffset) {
                snapshotFlow {
                    val offset =
                        if (sheetState.hasExpandedState) {
                            sheetState.requireOffset()
                        } else {
                            collapsedOffset.toFloat()
                        }
                    offset to sheetState.isAnimationRunning
                }.collect { (offset, settling) ->
                    val fraction =
                        if (collapsedOffset > expandedOffset) {
                            ((collapsedOffset - offset) / (collapsedOffset - expandedOffset)).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    progress = fraction
                    // Keep the same direction during a gesture, including reversals. Finish only
                    // at rest so the sheet's spring can overshoot without recreating the content.
                    when {
                        postTransitionState.currentState == postTransitionState.targetState &&
                            fraction == (if (postTransitionState.currentState) 1f else 0f) -> {
                            Unit
                        }

                        offset == collapsedOffset.toFloat() && !settling -> {
                            postTransitionState.snapTo(false)
                        }

                        offset == expandedOffset.toFloat() && !settling -> {
                            postTransitionState.snapTo(true)
                        }

                        else -> {
                            if (postTransitionState.currentState == postTransitionState.targetState) {
                                // Shared bounds are discovered during layout. Establish both ends
                                // at time zero before seeking, so newly created bounds animations
                                // don't start their own timeline at the first drag fraction.
                                postTransitionState.seekTo(0f, !postTransitionState.currentState)
                                withFrameNanos { }
                            }
                            if (postTransitionState.currentState) {
                                postTransitionState.seekTo(1f - fraction, false)
                            } else {
                                postTransitionState.seekTo(fraction, true)
                            }
                        }
                    }
                }
            }
            val collapse: () -> Unit = { scope.launch { sheetState.partialExpand() } }
            // Also collapse if a show interrupts an exit before AnimatedVisibility disposes the sheet.
            LaunchedEffect(showSheet) {
                if (showSheet) sheetState.partialExpand()
            }
            val slideDistance =
                with(density) { (summaryHeight + handleHeight + (expandedHeight - summaryHeight) * progress).roundToPx() }
            Box(Modifier.fillMaxSize()) {
                if (progress > 0f) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = progress }
                            .background(BottomSheetDefaults.ScrimColor)
                            .clickable(enabled = showSheet, onClick = collapse),
                    )
                }
                BottomSheetScaffold(
                    modifier =
                        Modifier.animateEnterExit(
                            enter = slideInVertically(motionScheme.defaultSpatialSpec()) { slideDistance },
                            exit = slideOutVertically(motionScheme.defaultSpatialSpec()) { slideDistance },
                        ),
                    scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState),
                    sheetPeekHeight = summaryHeight + handleHeight,
                    sheetSwipeEnabled = showSheet,
                    containerColor = Color.Transparent,
                    sheetDragHandle = {
                        BottomSheetDefaults.DragHandle(Modifier.onSizeChanged { handleHeight = with(density) { it.height.toDp() } })
                    },
                    sheetContent = {
                        if (post != null) {
                            CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                                MediaPostContent(
                                    post = post,
                                    quotes = quotes,
                                    transition = postTransition,
                                    progress = progress,
                                    modifier = Modifier.fillMaxWidth().height(expandedHeight),
                                    onSummaryHeightChanged = { summaryHeight = it },
                                )
                            }
                        }
                    },
                ) {}
            }
            BackHandler(enabled = showSheet && progress > 0f, onBack = collapse)
        }
    }
}

@Composable
private fun MediaPostContent(
    post: UiTimelineV2.Post,
    quotes: ImmutableList<UiTimelineV2.Post>,
    transition: Transition<Boolean>,
    progress: Float,
    modifier: Modifier = Modifier,
    onSummaryHeightChanged: (Dp) -> Unit,
) {
    val density = LocalDensity.current
    SharedTransitionLayout(modifier.clipToBounds()) {
        transition.AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart,
            // Equal, linear timelines make seekTo(fraction) a spatial fraction too. The sheet
            // supplies all timing and spring motion, including settling after the finger lifts.
            transitionSpec = {
                (fadeIn(tween(easing = LinearEasing)) togetherWith fadeOut(tween(easing = LinearEasing))).using(null)
            },
        ) { expanded ->
            val nameModifier =
                Modifier.sharedBounds(
                    rememberSharedContentState(MediaPostElement.Name),
                    animatedVisibilityScope = this,
                    boundsTransform = { _, _ -> tween(easing = LinearEasing) },
                    enter = fadeIn(tween(easing = LinearEasing)),
                    exit = fadeOut(tween(easing = LinearEasing)),
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                )
            val handleModifier =
                Modifier.sharedBounds(
                    rememberSharedContentState(MediaPostElement.Handle),
                    animatedVisibilityScope = this,
                    boundsTransform = { _, _ -> tween(easing = LinearEasing) },
                    enter = fadeIn(tween(easing = LinearEasing)),
                    exit = fadeOut(tween(easing = LinearEasing)),
                    resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
                )
            val avatarModifier =
                Modifier.sharedElement(
                    rememberSharedContentState(MediaPostElement.Avatar),
                    animatedVisibilityScope = this,
                    boundsTransform = { _, _ -> tween(easing = LinearEasing) },
                )
            val actionsModifier =
                Modifier.sharedBounds(
                    rememberSharedContentState(MediaPostElement.Actions),
                    animatedVisibilityScope = this,
                    boundsTransform = { _, _ -> tween(easing = LinearEasing) },
                    enter = fadeIn(tween(easing = LinearEasing)),
                    exit = fadeOut(tween(easing = LinearEasing)),
                )
            val semanticsModifier = if (expanded == (progress >= 0.5f)) Modifier else Modifier.clearAndSetSemantics {}
            val padding =
                Modifier
                    .padding(horizontal = screenHorizontalPadding, vertical = 8.dp)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            if (expanded) {
                CommonStatusComponent(
                    item = post,
                    isDetail = true,
                    showMedia = false,
                    quotes = quotes,
                    nameModifier = nameModifier,
                    handleModifier = handleModifier,
                    avatarModifier = avatarModifier,
                    actionsModifier = actionsModifier,
                    modifier = semanticsModifier.verticalScroll(rememberScrollState()).then(padding),
                )
            } else {
                Column(
                    semanticsModifier
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.Top)
                        .onSizeChanged { onSummaryHeightChanged(with(density) { it.height.toDp() }) },
                ) {
                    StatusSummaryComponent(
                        item = post,
                        modifier = padding,
                        nameModifier = nameModifier,
                        handleModifier = handleModifier,
                        avatarModifier = avatarModifier,
                        actionsModifier = actionsModifier,
                    )
                }
            }
        }
    }
}

private enum class MediaPostElement {
    Name,
    Handle,
    Avatar,
    Actions,
}
