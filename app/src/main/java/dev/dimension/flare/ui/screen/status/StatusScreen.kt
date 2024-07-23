package dev.dimension.flare.ui.screen.status

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.annotation.parameters.DeepLink
import com.ramcosta.composedestinations.annotation.parameters.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.StatusContextPresenter
import kotlinx.coroutines.launch

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
@Destination<RootGraph>(
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
fun AnimatedVisibilityScope.StatusRoute(
    statusKey: MicroBlogKey,
    navigator: DestinationsNavigator,
    accountType: AccountType,
    sharedTransitionScope: SharedTransitionScope,
) = with(sharedTransitionScope) {
    StatusScreen(
        statusKey,
        onBack = navigator::navigateUp,
        accountType = accountType,
    )
}

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun StatusScreen(
    statusKey: MicroBlogKey,
    onBack: () -> Unit,
    accountType: AccountType,
) {
    val state by producePresenter(statusKey.toString()) {
        statusPresenter(accountType = accountType, statusKey = statusKey)
    }
    FlareScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.status_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back),
                        )
                    }
                },
            )
        },
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
                    with(state.state.listState) {
                        status(
                            detailStatusKey = statusKey,
                        )
                    }
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
