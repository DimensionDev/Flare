package dev.dimension.flare.ui.screen.rss

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import dev.dimension.flare.common.isRefreshing
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.FlareTopAppBar
import dev.dimension.flare.ui.component.RefreshContainer
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.rss.RssSourcePresenter
import dev.dimension.flare.ui.presenter.invoke
import kotlinx.coroutines.launch
import moe.tlaster.precompose.molecule.producePresenter

// @OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
// @Composable
// internal fun RssTimelineDetailScreen(
//    id: Int,
//    onBack: () -> Unit,
// ) {
//    val scaffoldNavigator =
//        rememberListDetailPaneScaffoldNavigator<RssDetailPaneNavArgs>()
//    CompositionLocalProvider(
//        LocalUriHandler provides
//            remember {
//                object : UriHandler {
//                    override fun openUri(uri: String) {
//                        scaffoldNavigator.navigateTo(
//                            ListDetailPaneScaffoldRole.Detail,
//                            RssDetailPaneNavArgs(uri),
//                        )
//                    }
//                }
//            },
//    ) {
//        ListDetailPaneScaffold(
//            directive = scaffoldNavigator.scaffoldDirective,
//            value = scaffoldNavigator.scaffoldValue,
//            listPane = {
//                AnimatedPane {
//                    RssTimelineScreen(
//                        id = id,
//                        onBack = onBack,
//                    )
//                }
//            },
//            detailPane = {
//                AnimatedPane {
//                    scaffoldNavigator.currentDestination?.content?.let { args ->
//                        RssDetailScreen(
//                            args.url,
//                            onBack = {
//                                scaffoldNavigator.navigateBack()
//                            },
//                        )
//                    }
//                }
//            },
//        )
//    }
//    BackHandler(
//        scaffoldNavigator.canNavigateBack(),
//    ) {
//        scaffoldNavigator.navigateBack()
//    }
// }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RssTimelineScreen(
    id: Int,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state by producePresenter("rss_timeline_$id") { presenter(id) }
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    FlareScaffold(
        topBar = {
            FlareTopAppBar(
                scrollBehavior = topAppBarScrollBehavior,
                title = {
                    state.data.onSuccess {
                        Text(text = it.title ?: it.host)
                    }
                },
                navigationIcon = {
                    BackButton(onBack)
                },
            )
        },
        modifier = Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        state.timelineState.onSuccess { state ->
            RefreshContainer(
                onRefresh = {
                    scope.launch {
                        state.refresh()
                    }
                },
                isRefreshing = state.listState.isRefreshing,
                indicatorPadding = contentPadding,
                content = {
                    LazyStatusVerticalStaggeredGrid(
                        contentPadding = contentPadding,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        status(state.listState)
                    }
                },
            )
        }
    }
}

// @Parcelize
// private data class RssDetailPaneNavArgs(
//    val url: String,
// ) : Parcelable

@Composable
private fun presenter(id: Int) =
    run {
        remember { RssSourcePresenter(id = id) }.invoke()
    }
