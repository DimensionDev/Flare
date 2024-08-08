package dev.dimension.flare.ui.screen.status

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.common.refreshSuspend
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.VVOCommentPresenter
import kotlinx.coroutines.launch

@Destination<RootGraph>(
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
        DeepLink(
            uriPattern = AppDeepLink.VVO.CommentDetail.ROUTE,
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun VVOCommentDeeplinkRoute(
    navigator: DestinationsNavigator,
    statusKey: MicroBlogKey,
    accountKey: MicroBlogKey,
) {
    VVOCommentScreen(
        commentKey = statusKey,
        onBack = navigator::navigateUp,
        accountType = AccountType.Specific(accountKey = accountKey),
    )
}

@Destination<RootGraph>(
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
@Composable
internal fun VVOCommentRoute(
    navigator: DestinationsNavigator,
    accountType: AccountType,
    commentKey: MicroBlogKey,
) {
    VVOCommentScreen(
        commentKey = commentKey,
        onBack = navigator::navigateUp,
        accountType = accountType,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun VVOCommentScreen(
    commentKey: MicroBlogKey,
    onBack: () -> Unit,
    accountType: AccountType,
) {
    val state by producePresenter("comment_$commentKey") {
        presenter(
            commentKey = commentKey,
            accountType = accountType,
        )
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
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back),
                        )
                    }
                },
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        RefreshContainer(
            onRefresh = state::refresh,
            isRefreshing = state.isRefreshing,
            indicatorPadding = contentPadding,
            content = {
                LazyStatusVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(1),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                ) {
                    item {
                        state.state.root
                            .onSuccess {
                                StatusItem(item = it)
                            }.onLoading {
                                StatusItem(item = null)
                            }
                    }
                    with(state.state.list) {
                        status()
                    }
                }
            },
        )
    }
}

@Composable
private fun presenter(
    commentKey: MicroBlogKey,
    accountType: AccountType,
) = run {
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val state =
        remember(
            commentKey,
            accountType,
        ) {
            VVOCommentPresenter(
                commentKey = commentKey,
                accountType = accountType,
            )
        }.invoke()

    object {
        val state = state
        val isRefreshing = isRefreshing

        fun refresh() {
            scope.launch {
                isRefreshing = true
                state.list.onSuccess {
                    refreshSuspend()
                }
                isRefreshing = false
            }
        }
    }
}
