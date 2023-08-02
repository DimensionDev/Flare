package dev.dimension.flare.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pullrefresh.PullRefreshIndicator
import androidx.compose.material3.pullrefresh.pullRefresh
import androidx.compose.material3.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshContainer(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    val refreshState = rememberPullRefreshState(refreshing, onRefresh)
    Box(
        modifier = modifier
            .pullRefresh(refreshState)
    ) {
        content.invoke(this)
        PullRefreshIndicator(
            refreshing,
            refreshState,
            Modifier.align(Alignment.TopCenter)
        )
    }
}
