package dev.dimension.flare.ui.screen.status

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoodBad
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.paging.compose.LazyPagingItems
import androidx.window.core.layout.WindowWidthSizeClass
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.annotation.parameters.DeepLink
import com.ramcosta.composedestinations.annotation.parameters.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.VVOStatusDetailPresenter
import dev.dimension.flare.ui.presenter.status.VVOStatusDetailState
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import org.koin.compose.koinInject

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
internal fun AnimatedVisibilityScope.VVOStatusRoute(
    statusKey: MicroBlogKey,
    navigator: DestinationsNavigator,
    accountType: AccountType,
    sharedTransitionScope: SharedTransitionScope,
) = with(sharedTransitionScope) {
    VVOStatusScreen(
        statusKey,
        onBack = navigator::navigateUp,
        accountType = accountType,
    )
}

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun VVOStatusScreen(
    statusKey: MicroBlogKey,
    onBack: () -> Unit,
    accountType: AccountType,
) {
    val state by producePresenter(key = "status_detail_${statusKey}_$accountType") {
        presenter(
            statusKey = statusKey,
            accountType = accountType,
        )
    }
    val windowInfo = currentWindowAdaptiveInfo()
    val windowSize =
        with(LocalDensity.current) {
            currentWindowSize().toSize().toDpSize()
        }
    val bigScreen = windowInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
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
    ) { contentPadding ->
        if (bigScreen) {
            val width =
                when (windowSize.width) {
                    in 840.dp..1024.dp -> 332.dp
                    else -> 432.dp
                }
            Row {
                StatusContent(
                    statusState = state.status,
                    event = state.statusEvent,
                    modifier =
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .width(width)
                            .padding(contentPadding + PaddingValues(horizontal = screenHorizontalPadding)),
                )
                LazyStatusVerticalStaggeredGrid(
                    contentPadding = contentPadding,
                ) {
                    reactionContent(
                        comment = state.comment,
                        repost = state.repost,
                        event = state.statusEvent,
                        detailType = state.type,
                        onDetailTypeChange = state::onTypeChanged,
                    )
                }
            }
        } else {
            LazyStatusVerticalStaggeredGrid(
                contentPadding = contentPadding,
            ) {
                item {
                    StatusContent(statusState = state.status, event = state.statusEvent)
                }
                reactionContent(
                    comment = state.comment,
                    repost = state.repost,
                    event = state.statusEvent,
                    detailType = state.type,
                    onDetailTypeChange = state::onTypeChanged,
                )
            }
        }
    }
}

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun StatusContent(
    statusState: UiState<UiStatus.VVO>,
    event: StatusEvent,
    modifier: Modifier = Modifier,
) {
    statusState
        .onSuccess { status ->
            key(status.statusKey, status.content) {
                StatusItem(
                    item = status,
                    event = event,
                    modifier =
                        modifier.sharedBounds(
                            rememberSharedContentState(key = status.itemKey),
                            animatedVisibilityScope = this@AnimatedVisibilityScope,
                            // ANY transition will lead to the entire screen being animated to
                            // exit state after list -> detail -> go back -> scroll a little bit,
                            // I have no idea why, so just use None here
                            enter = EnterTransition.None,
                            exit = ExitTransition.None,
                            renderInOverlayDuringTransition = false,
                            placeHolderSize = SharedTransitionScope.PlaceHolderSize.animatedSize,
                        ),
                    isDetail = true,
                )
            }
        }.onLoading {
            StatusItem(item = null, event = event, modifier = modifier, isDetail = true)
        }.onError {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Default.MoodBad,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = stringResource(id = R.string.status_loadmore_error_retry),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
}

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
private fun LazyStaggeredGridScope.reactionContent(
    comment: UiState<LazyPagingItems<UiStatus>>,
    repost: UiState<LazyPagingItems<UiStatus>>,
    event: StatusEvent,
    detailType: DetailType,
    onDetailTypeChange: (DetailType) -> Unit,
) {
    item(
        span = StaggeredGridItemSpan.FullLine,
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.padding(horizontal = screenHorizontalPadding),
        ) {
            DetailType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    selected = detailType == type,
                    onClick = {
                        onDetailTypeChange(type)
                    },
                    shape =
                        SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = DetailType.entries.size,
                        ),
                ) {
                    Text(text = stringResource(id = type.title))
                }
            }
        }
    }
    with(event) {
        when (detailType) {
            DetailType.Comment ->
                with(comment) {
                    status(showVVOStatus = false)
                }

            DetailType.Repost ->
                with(repost) {
                    status(showVVOStatus = false)
                }
        }
    }
}

@Composable
private fun presenter(
    statusKey: MicroBlogKey,
    accountType: AccountType,
    statusEvent: StatusEvent = koinInject(),
) = run {
    var type by remember {
        mutableStateOf(DetailType.Comment)
    }
    val state =
        remember {
            VVOStatusDetailPresenter(
                accountType = accountType,
                statusKey = statusKey,
            )
        }.invoke()

    object : VVOStatusDetailState by state {
        val type = type
        val statusEvent = statusEvent

        fun onTypeChanged(value: DetailType) {
            type = value
        }
    }
}

@Immutable
private enum class DetailType {
    Repost,
    Comment,
}

private val DetailType.title: Int
    get() =
        when (this) {
            DetailType.Comment -> R.string.status_detail_comment
            DetailType.Repost -> R.string.status_detail_repost
        }
