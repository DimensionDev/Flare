package dev.dimension.flare.ui.screen.status

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.annotation.parameters.DeepLink
import com.ramcosta.composedestinations.annotation.parameters.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.StatusContextPresenter
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

@Composable
@Destination<RootGraph>(
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
        DeepLink(
            uriPattern = AppDeepLink.StatusDetail.ROUTE,
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
fun StatusDeeplinkRoute(
    statusKey: MicroBlogKey,
    accountKey: MicroBlogKey?,
    navigator: DestinationsNavigator,
) {
    StatusScreen(
        statusKey,
        onBack = navigator::navigateUp,
        accountType = accountKey?.let { AccountType.Specific(it) } ?: AccountType.Guest,
    )
}

@Composable
@Destination<RootGraph>(
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
fun StatusRoute(
    statusKey: MicroBlogKey,
    navigator: DestinationsNavigator,
    accountType: AccountType,
) {
    StatusScreen(
        statusKey,
        onBack = navigator::navigateUp,
        accountType = accountType,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatusScreen(
    statusKey: MicroBlogKey,
    onBack: () -> Unit,
    accountType: AccountType,
) {
    val state by producePresenter(statusKey.toString()) {
        statusPresenter(accountType = accountType, statusKey = statusKey)
    }
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    FlareScaffold(
        topBar = {
            TopAppBar(
                scrollBehavior = topAppBarScrollBehavior,
                title = {
                    Text(text = stringResource(id = R.string.status_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) {
        RefreshContainer(
            onRefresh = state::refresh,
            isRefreshing = state.isRefreshing,
            indicatorPadding = it,
            content = {
                LazyStatusVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(1),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = it,
                ) {
                    status(
                        state.state.listState,
                        detailStatusKey = statusKey,
                    )
                }
            },
        )
    }
}

@Composable
private fun statusPresenter(
    statusKey: MicroBlogKey,
    accountType: AccountType,
) = run {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val state =
        remember(statusKey) {
            StatusContextPresenter(accountType = accountType, statusKey = statusKey)
        }.invoke()

    object {
        val state = state
        val isRefreshing = isRefreshing

        fun refresh() {
            scope.launch {
                isRefreshing = true
                state.refresh()
                isRefreshing = false
            }
        }
    }
}
