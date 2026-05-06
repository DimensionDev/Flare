package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineTargetRef
import dev.dimension.flare.data.model.tab.UiTimelineItem
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@Composable
internal fun TimelineScreen(
    tabItem: TimelineTargetRef,
    onBack: () -> Unit,
) {
    val state by producePresenter("uitimeline:${tabItem.id}") {
        val resolver: TimelineResolver = koinInject()
        resolver.toUi(tabItem)
    }
    TimelineScreen(
        tabItem = state,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimelineScreen(
    tabItem: UiTimelineItem,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyStaggeredGridState()
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    dev.dimension.flare.ui.component.Text(tabItem.title)
                },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BackButton(onBack)
                },
                modifier =
                    Modifier
                        .clickable(
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            },
                            indication = null,
                            interactionSource =
                                remember {
                                    androidx.compose.foundation.interaction
                                        .MutableInteractionSource()
                                },
                        ),
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        TimelineItemContent(
            item = tabItem,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
            lazyStaggeredGridState = listState,
        )
    }
}
