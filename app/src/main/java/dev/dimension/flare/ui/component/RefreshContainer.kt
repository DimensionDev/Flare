package dev.dimension.flare.ui.component

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshContainer(
    onRefresh: suspend () -> Unit,
    content: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    indicatorPadding: PaddingValues = PaddingValues(0.dp),
) {
    val refreshState = rememberPullToRefreshState()
    if (refreshState.isRefreshing) {
        LaunchedEffect(true) {
            onRefresh.invoke()
            refreshState.endRefresh()
        }
    }
    val scaleFraction =
        if (refreshState.isRefreshing) {
            1f
        } else {
            LinearOutSlowInEasing.transform(refreshState.progress).coerceIn(0f, 1f)
        }
    Box(
        modifier =
            modifier
                .nestedScroll(refreshState.nestedScrollConnection),
    ) {
        content.invoke(this)
        if (scaleFraction > 0) {
            PullToRefreshContainer(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(indicatorPadding),
                state = refreshState,
            )
        }
    }
}
