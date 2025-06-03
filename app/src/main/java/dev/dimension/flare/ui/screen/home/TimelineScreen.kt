package dev.dimension.flare.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Pen
import dev.dimension.flare.R
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.LocalBottomBarShowing
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.home.UserState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.screen.settings.TabTitle
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimelineScreen(
    tabItem: TimelineTabItem,
    toCompose: () -> Unit,
    toQuickMenu: () -> Unit,
    toLogin: () -> Unit,
) {
    val state by producePresenter(key = "timeline_${tabItem.key}") {
        timelinePresenter(tabItem)
    }
    RegisterTabCallback(lazyListState = state.lazyListState)
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = {
                    TabTitle(title = tabItem.metaData.title)
                },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    if (LocalBottomBarShowing.current) {
                        state.user.onSuccess {
                            IconButton(
                                onClick = toQuickMenu,
                            ) {
                                AvatarComponent(it.avatar, size = 24.dp)
                            }
                        }
                    }
                },
                actions = {
                    state.user
                        .onError {
                            TextButton(onClick = toLogin) {
                                Text(text = stringResource(id = R.string.login_button))
                            }
                        }
                },
            )
        },
        floatingActionButton = {
            state.user.onSuccess {
                AnimatedVisibility(
                    visible = topAppBarScrollBehavior.state.heightOffset == 0f && LocalBottomBarShowing.current,
                    enter = scaleIn(),
                    exit = scaleOut(),
                ) {
                    FloatingActionButton(
                        onClick = toCompose,
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Pen,
                            contentDescription = stringResource(id = R.string.compose_title),
                        )
                    }
                }
            }
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
        val state = timelineItemPresenter(tabItem)
        val accountState =
            remember(tabItem.account) {
                UserPresenter(
                    accountType = tabItem.account,
                    userKey = null,
                )
            }.invoke()
        object : UserState by accountState, TimelineItemState by state {
        }
    }
