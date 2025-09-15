package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.TabTitle
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.presenter.TimelineItemPresenter
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimelineScreen(
    tabItem: TimelineTabItem,
    toLogin: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    val state by producePresenter(key = "timeline_${tabItem.key}") {
        timelinePresenter(tabItem)
    }
    RegisterTabCallback(
        lazyListState = state.lazyListState,
        onRefresh = {
            state.refreshSync()
        },
    )
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    TabTitle(title = tabItem.metaData.title)
                },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BackButton(onBack)
                },
                actions = {
                    if (toLogin != null) {
                        state.user
                            .onError {
                                TextButton(onClick = toLogin) {
                                    Text(text = stringResource(id = R.string.login_button))
                                }
                            }
                    }
                },
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        TimelineItemContent(
            state = state,
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun timelinePresenter(tabItem: TimelineTabItem) =
    run {
        val state = remember(tabItem.key) { TimelineItemPresenter(tabItem) }.invoke()
        val accountState =
            remember(tabItem.account) {
                UserPresenter(
                    accountType = tabItem.account,
                    userKey = null,
                )
            }.invoke()
        object : UserState by accountState, TimelineItemPresenter.State by state {
        }
    }
