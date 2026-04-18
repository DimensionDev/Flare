package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dimension.flare.data.model.LocalAppearanceSettings
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.theme.PlatformTheme
import dev.dimension.flare.ui.theme.isLightTheme
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
@OptIn(FlowPreview::class)
@Composable
public fun LazyStatusVerticalStaggeredGrid(
    modifier: Modifier = Modifier,
    columns: StaggeredGridCells = StaggeredGridCells.Adaptive(320.dp),
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalItemSpacing: Dp = 0.dp,
    horizontalArrangement: Arrangement.Horizontal =
        Arrangement.spacedBy(
            8.dp,
            Alignment.CenterHorizontally,
        ),
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    forceCardMode: Boolean = false,
    allowGalleryMode: Boolean = false,
    content: LazyStaggeredGridScope.() -> Unit,
) {
    val displayMode = LocalAppearanceSettings.current.timelineDisplayMode
    val effectiveMode =
        when {
            allowGalleryMode -> displayMode
            displayMode == TimelineDisplayMode.Gallery -> TimelineDisplayMode.Card
            else -> displayMode
        }
    val paddingForColumnCalc = contentPadding + PaddingValues(horizontal = screenHorizontalPadding)
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val viewportWidthPx by remember(state) {
        snapshotFlow { state.layoutInfo.viewportSize.width }
            .distinctUntilChanged()
    }.collectAsState(0)
    val isWideViewport = with(density) { viewportWidthPx.toDp() } >= 600.dp
    val effectiveColumns =
        if (effectiveMode == TimelineDisplayMode.Gallery) {
            StaggeredGridCells.Adaptive(if (isWideViewport) 240.dp else 160.dp)
        } else {
            columns
        }
    val columnCount by remember(state, effectiveColumns) {
        snapshotFlow { state.layoutInfo.viewportSize.width }
            .distinctUntilChanged()
            .map {
                with(density) {
                    with(effectiveColumns) {
                        calculateCrossAxisCellSizes(
                            it - paddingForColumnCalc.calculateStartPadding(layoutDirection).roundToPx() -
                                paddingForColumnCalc.calculateEndPadding(layoutDirection).roundToPx(),
                            8.dp.roundToPx(),
                        )
                    }
                }.size
            }.distinctUntilChanged()
    }.collectAsState(1)
    val bigScreen = effectiveMode != TimelineDisplayMode.Gallery && columnCount > 1
    val plainTimeline = !bigScreen && effectiveMode == TimelineDisplayMode.Plain && !forceCardMode
    val padding =
        if (plainTimeline) {
            contentPadding
        } else {
            paddingForColumnCalc
        }
    val actualVerticalSpacing =
        when {
            effectiveMode == TimelineDisplayMode.Gallery -> 8.dp
            bigScreen -> verticalItemSpacing
            else -> 2.dp
        }
    val isScrollInProgressDebounced by remember(state) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .debounce(500)
    }.collectAsState(false)
    val gridModifier =
        if (plainTimeline && isLightTheme()) {
            modifier.background(PlatformTheme.colorScheme.card)
        } else {
            modifier
        }
    CompositionLocalProvider(
        LocalIsScrollingInProgress provides isScrollInProgressDebounced,
        LocalMultipleColumns provides bigScreen,
        LocalEffectiveTimelineDisplayMode provides effectiveMode,
    ) {
        LazyVerticalStaggeredGrid(
            modifier = gridModifier,
            columns = effectiveColumns,
            state = state,
            contentPadding = padding,
            reverseLayout = reverseLayout,
            verticalItemSpacing = actualVerticalSpacing,
            horizontalArrangement = horizontalArrangement,
            flingBehavior = flingBehavior,
            userScrollEnabled = userScrollEnabled,
            content = content,
        )
    }
}

internal val LocalIsScrollingInProgress =
    androidx.compose.runtime.compositionLocalOf { false }

internal val LocalMultipleColumns =
    androidx.compose.runtime.compositionLocalOf { false }

internal val LocalEffectiveTimelineDisplayMode =
    androidx.compose.runtime.compositionLocalOf { TimelineDisplayMode.Card }
