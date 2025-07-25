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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.theme.screenHorizontalPadding

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
    val bigScreen = isBigScreen()
    val padding =
        if (bigScreen) {
            contentPadding + PaddingValues(horizontal = screenHorizontalPadding)
        } else {
            contentPadding + PaddingValues(horizontal = screenHorizontalPadding)
        }
    val actualVerticalSpacing =
        if (bigScreen) {
            verticalItemSpacing
        } else {
            2.dp
        }
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
