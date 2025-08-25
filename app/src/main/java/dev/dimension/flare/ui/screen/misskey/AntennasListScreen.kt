package dev.dimension.flare.ui.screen.misskey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareLargeFlexibleTopAppBar
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import moe.tlaster.precompose.molecule.producePresenter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AntennasListScreen(
    accountType: AccountType,
    toTimeline: (UiList) -> Unit,
    onBack: () -> Unit,
) {
    val state by producePresenter("antennas_list_$accountType") {
        presenter(accountType)
    }

    val topAppBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    FlareScaffold(
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
        topBar = {
            FlareLargeFlexibleTopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.home_tab_antennas_title))
                },
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
            )
        },
    ) { contentPadding ->
        RefreshContainer(
            modifier =
                Modifier
                    .fillMaxSize(),
            indicatorPadding = contentPadding,
            isRefreshing = state.data.isRefreshing,
            onRefresh = state::refresh,
            content = {
                LazyColumn(
                    contentPadding = contentPadding,
                    modifier =
                        Modifier
                            .padding(horizontal = screenHorizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    misskeyAntennasWithTabs(
                        state = state,
                        onClick = toTimeline,
                    )
                }
            },
        )
    }
}

@Composable
private fun presenter(accountType: AccountType) =
    run {
        remember(accountType) { MisskeyAntennasListWithTabsPresenter(accountType) }.invoke()
    }
