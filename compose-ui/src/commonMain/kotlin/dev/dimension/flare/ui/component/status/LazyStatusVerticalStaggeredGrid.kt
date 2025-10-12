package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(FlowPreview::class)
@Composable
public fun LazyStatusVerticalStaggeredGrid(
    modifier: Modifier = Modifier,
    columns: StaggeredGridCells = StaggeredGridCells.Adaptive(320.dp),
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalItemSpacing: Dp = 0.dp,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyStaggeredGridScope.() -> Unit,
) {
    val density = LocalDensity.current
    val columnCount by remember(state) {
        snapshotFlow { state.layoutInfo.viewportSize.width }
            .distinctUntilChanged()
            .map {
                with(density) {
                    with(columns) {
                        calculateCrossAxisCellSizes(it, 8.dp.roundToPx())
                    }
                }.size
            }.distinctUntilChanged()
    }.collectAsState(1)
    val bigScreen = columnCount > 1
    val padding = contentPadding + PaddingValues(horizontal = screenHorizontalPadding)
    val actualVerticalSpacing =
        if (bigScreen) {
            verticalItemSpacing
        } else {
            2.dp
        }
    val isScrollInProgressDebounced by remember(state) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .debounce(500)
    }.collectAsState(false)
    CompositionLocalProvider(
        LocalIsScrollingInProgress provides isScrollInProgressDebounced,
        LocalMultipleColumns provides bigScreen,
    ) {
        LazyVerticalStaggeredGrid(
            modifier = modifier,
            columns = columns,
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
