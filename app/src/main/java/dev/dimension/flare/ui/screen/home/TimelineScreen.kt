package dev.dimension.flare.ui.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Sliders
import dev.dimension.flare.R
import dev.dimension.flare.data.model.BottomBarBehavior
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.model.tab.resolveTimelineAppearance
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.component.TabIcon
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.home.HomeTabItemPresenter
import dev.dimension.flare.ui.presenter.home.LoggedInPresenter
import dev.dimension.flare.ui.presenter.home.LoggedInState
import dev.dimension.flare.ui.presenter.invoke
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeckTimelineScreen(
//    tabItem: TimelineTabItemV2,
    id: String,
    toQuickMenu: () -> Unit,
    toLogin: () -> Unit,
    toTabSettings: () -> Unit,
) {
    val state by producePresenter("deck_timeline_$id") {
        val loginState = remember { LoggedInPresenter() }.invoke()
        val changeLogState = changeLogPresenter()
        val tabState = remember { HomeTabItemPresenter(id = id) }.invoke()
        object : LoggedInState by loginState, ChangeLogState by changeLogState, HomeTabItemPresenter.State by tabState {
        }
    }
    val topAppBarScrollBehavior =
        if (LocalGlobalAppearance.current.bottomBarBehavior == BottomBarBehavior.AlwaysShow) {
            TopAppBarDefaults.pinnedScrollBehavior()
        } else {
            TopAppBarDefaults.enterAlwaysScrollBehavior()
        }
    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                title = {
                    state.tabItem.onSuccess { tabItem ->
                        dev.dimension.flare.ui.component
                            .Text(text = tabItem.title)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            toQuickMenu.invoke()
                        },
                    ) {
                        state.tabItem.onSuccess { tabItem ->
                            TabIcon(tabItem)
                        }
                    }
                },
                scrollBehavior = topAppBarScrollBehavior,
                actions = {
                    IconButton(
                        onClick = {
                            toTabSettings.invoke()
                        },
                    ) {
                        FAIcon(
                            imageVector = FontAwesomeIcons.Solid.Sliders,
                            contentDescription = stringResource(R.string.edit_tab_title),
                        )
                    }
                    if (state.isLoggedIn.takeSuccess() == false) {
                        TextButton(onClick = toLogin) {
                            Text(text = stringResource(id = R.string.login_button))
                        }
                    }
                },
            )
        },
        modifier =
            Modifier
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        state.tabItem.onSuccess { tabItem ->
            val timelineAppearance = LocalTimelineAppearance.current
            CompositionLocalProvider(
                LocalTimelineAppearance provides
                    remember(
                        tabItem.appearancePatch,
                        timelineAppearance,
                    ) {
                        tabItem.resolveTimelineAppearance(timelineAppearance)
                    },
            ) {
                TimelineItemContent(
                    item = tabItem,
                    contentPadding = contentPadding,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimelineScreen(
    tabItem: TimelineTabItemV2,
    onBack: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyStaggeredGridState()
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    dev.dimension.flare.ui.component
                        .Text(tabItem.title)
                },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    if (onBack != null) {
                        BackButton(onBack)
                    }
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

        val timelineAppearance = LocalTimelineAppearance.current
        CompositionLocalProvider(
            LocalTimelineAppearance provides
                remember(
                    tabItem.appearancePatch,
                    timelineAppearance,
                ) {
                    tabItem.resolveTimelineAppearance(timelineAppearance)
                },
        ) {
            TimelineItemContent(
                item = tabItem,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
                lazyStaggeredGridState = listState,
            )
        }
    }
}
