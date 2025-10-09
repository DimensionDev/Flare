package dev.dimension.flare.ui.component

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    indicatorPadding: PaddingValues = PaddingValues(0.dp),
) {
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val refreshState = rememberPullToRefreshState()
    // workaround for https://issuetracker.google.com/issues/248274004
    SideEffect {
        if (!isRefreshing && refreshState.distanceFraction == 1f) {
            scope.launch {
                refreshState.animateToHidden()
            }
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { refreshState.distanceFraction }
            .map { it >= 1f }
            .distinctUntilChanged()
            .drop(1) // drop initial value
            .collect {
                hapticFeedback.performHapticFeedback(
                    HapticFeedbackType.GestureThresholdActivate,
                )
            }
    }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        content = content,
        state = refreshState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(indicatorPadding),
                isRefreshing = isRefreshing,
                state = refreshState,
            )
        },
    )
}
