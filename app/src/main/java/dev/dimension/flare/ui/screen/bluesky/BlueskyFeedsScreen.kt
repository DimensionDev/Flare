package dev.dimension.flare.ui.screen.bluesky

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.SquareRss
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun BlueskyFeedDetailPlaceholder(modifier: Modifier = Modifier) {
    FlareScaffold(
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(it),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, alignment = Alignment.CenterVertically),
        ) {
            FAIcon(
                FontAwesomeIcons.Solid.SquareRss,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlueskyFeedsScreen(
    accountType: AccountType,
    toFeed: (UiList) -> Unit,
    onBack: () -> Unit,
) {
    val state by producePresenter {
        presenter(accountType = accountType)
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    FlareScaffold(
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.home_tab_feeds_title))
                },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        RefreshContainer(
            modifier =
                Modifier
                    .fillMaxSize(),
            indicatorPadding = contentPadding,
            isRefreshing = state.isRefreshing,
            onRefresh = state::refresh,
            content = {
                LazyColumn(
                    contentPadding = contentPadding,
                    modifier =
                        Modifier
                            .padding(horizontal = screenHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item {
                        ListItem(
                            headlineContent = {
                                Text(text = stringResource(id = R.string.feeds_my_feeds_title))
                            },
                            colors =
                                ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                ),
                        )
                    }

                    myBlueskyFeedWithTabs(
                        state = state,
                        toFeed = toFeed,
                    )

                    item {
                        ListItem(
                            headlineContent = {
                                Text(text = stringResource(id = R.string.feeds_discover_feeds_title))
                            },
                            colors =
                                ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                ),
                        )
                    }
                    popularBlueskyFeedWithTabs(
                        state = state,
                        toFeed = toFeed,
                    )
                }
            },
        )
    }
}

@Composable
private fun presenter(accountType: AccountType) =
    run {
        remember(accountType) { BlueskyFeedsWithTabsPresenter(accountType) }.invoke()
    }
