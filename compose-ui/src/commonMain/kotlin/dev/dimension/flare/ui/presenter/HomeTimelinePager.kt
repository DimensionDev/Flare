package dev.dimension.flare.ui.presenter

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

@Composable
public fun rememberHomeTimelinePagerState(
    tabIds: List<String>,
    initialSelectedTabId: String?,
    selectTab: (String) -> Unit,
): PagerState {
    val pager = rememberPagerState(initialPage = tabIds.indexOf(initialSelectedTabId).coerceAtLeast(0)) { tabIds.size }
    val onSelect by rememberUpdatedState(selectTab)
    LaunchedEffect(pager) {
        // Pager already preserves the keyed page when tabs move; indices can still describe the old layout.
        snapshotFlow {
            pager.layoutInfo.visiblePagesInfo
                .firstOrNull { it.index == pager.settledPage }
                ?.key as? String
        }.filterNotNull().distinctUntilChanged().collect { onSelect(it) }
    }
    return pager
}
