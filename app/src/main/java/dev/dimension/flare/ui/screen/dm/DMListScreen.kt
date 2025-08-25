package dev.dimension.flare.ui.screen.dm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Message
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.dm.dmList
import dev.dimension.flare.ui.presenter.dm.DMListPresenter
import dev.dimension.flare.ui.presenter.dm.DMListState
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@Composable
internal fun DMConversationDetailPlaceholder(modifier: Modifier = Modifier) {
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
                FontAwesomeIcons.Solid.Message,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DMListScreen(
    accountType: AccountType,
    onBack: () -> Unit,
    onItemClicked: (MicroBlogKey) -> Unit,
) {
    val state by producePresenter("dm_list_$accountType") {
        presenter(accountType)
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    FlareScaffold(
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.dm_list_title))
                },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BackButton(onBack)
                },
            )
        },
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
                    dmList(
                        data = state.items,
                        onItemClicked = onItemClicked,
                    )
                }
            },
        )
    }
}

@Composable
private fun presenter(accountType: AccountType) =
    run {
        val scope = rememberCoroutineScope()
        val state =
            remember(accountType) {
                DMListPresenter(accountType)
            }.invoke()
        object : DMListState by state {
            fun refresh() {
                scope.launch {
                    state.refreshSuspend()
                }
            }
        }
    }
