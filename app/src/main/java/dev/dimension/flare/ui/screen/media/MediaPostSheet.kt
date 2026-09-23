package dev.dimension.flare.ui.screen.media

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.ChevronDown
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.FAIcon
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
            val expanded = sheetState.currentValue == SheetValue.Expanded || sheetState.targetValue == SheetValue.Expanded
            val collapse: () -> Unit = { scope.launch { sheetState.partialExpand() } }
            // Also collapse if a show interrupts an exit before AnimatedVisibility disposes the sheet.
            LaunchedEffect(showSheet) {
                if (showSheet) sheetState.partialExpand()
            }
            val slideDistance =
                with(density) { (if (expanded) expandedHeight + handleHeight else summaryHeight + handleHeight).roundToPx() }
            Box(Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = expanded && showSheet,
                    enter = fadeIn(motionScheme.defaultEffectsSpec()),
                    exit = fadeOut(motionScheme.defaultEffectsSpec()),
                ) {
                    Box(Modifier.fillMaxSize().background(BottomSheetDefaults.ScrimColor).clickable(onClick = collapse))
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
                                Box(Modifier.fillMaxWidth().height(expandedHeight)) {
                                    if (!expanded) {
                                        Column(
                                            Modifier
                                                .fillMaxWidth()
                                                .onSizeChanged { summaryHeight = with(density) { it.height.toDp() } },
                                        ) {
                                            StatusSummaryComponent(
                                                item = post,
                                                modifier =
                                                    Modifier
                                                        .padding(horizontal = screenHorizontalPadding, vertical = 8.dp)
                                                        .windowInsetsPadding(
                                                            WindowInsets.systemBars.only(
                                                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                                            ),
                                                        ),
                                            )
                                        }
                                    }
                                    if (expanded) {
                                        Column(Modifier.fillMaxSize()) {
                                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                IconButton(onClick = collapse, modifier = Modifier.align(Alignment.CenterEnd)) {
                                                    FAIcon(
                                                        FontAwesomeIcons.Solid.ChevronDown,
                                                        stringResource(R.string.media_post_collapse),
                                                    )
                                                }
                                            }
                                            CommonStatusComponent(
                                                item = post,
                                                isDetail = true,
                                                showMedia = false,
                                                quotes = quotes,
                                                modifier =
                                                    Modifier
                                                        .verticalScroll(rememberScrollState())
                                                        .padding(horizontal = screenHorizontalPadding, vertical = 8.dp)
                                                        .windowInsetsPadding(
                                                            WindowInsets.systemBars.only(
                                                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                                            ),
                                                        ),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                ) {}
            }
            BackHandler(enabled = showSheet && expanded, onBack = collapse)
        }
    }
}
