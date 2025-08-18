package dev.dimension.flare.ui.screen.status

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.FileCircleExclamation
import dev.dimension.flare.Res
import dev.dimension.flare.common.PagingState
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.status_detail_comment
import dev.dimension.flare.status_detail_repost
import dev.dimension.flare.status_loadmore_error_retry
import dev.dimension.flare.ui.component.FAIcon
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.component.status.LazyStatusVerticalStaggeredGrid
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.component.status.status
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.status.VVOStatusDetailPresenter
import dev.dimension.flare.ui.presenter.status.VVOStatusDetailState
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.composefluent.component.SegmentedButton
import io.github.composefluent.component.SegmentedControl
import io.github.composefluent.component.SegmentedItemPosition
import io.github.composefluent.component.Text
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun VVOStatusScreen(
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
    val bigScreen = isBigScreen()
    if (bigScreen) {
        Row {
            StatusContent(
                detailStatusKey = statusKey,
                statusState = state.status,
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .width(432.dp)
                        .padding(PaddingValues(horizontal = screenHorizontalPadding)),
            )
            LazyStatusVerticalStaggeredGrid {
                reactionContent(
                    comment = state.comment,
                    repost = state.repost,
                    detailType = state.type,
                    onDetailTypeChange = state::onTypeChanged,
                )
            }
        }
    } else {
        LazyStatusVerticalStaggeredGrid {
            item {
                StatusContent(statusState = state.status, detailStatusKey = statusKey)
            }
            reactionContent(
                comment = state.comment,
                repost = state.repost,
                detailType = state.type,
                onDetailTypeChange = state::onTypeChanged,
            )
        }
    }
}

@Composable
private fun StatusContent(
    statusState: UiState<UiTimeline>,
    detailStatusKey: MicroBlogKey,
    modifier: Modifier = Modifier,
) {
    statusState
        .onSuccess { status ->
            key(status.itemKey, status.content) {
                StatusItem(
                    item = status,
                    detailStatusKey = detailStatusKey,
//                    modifier =
//                        modifier.sharedBounds(
//                            rememberSharedContentState(key = status.itemKey),
//                            animatedVisibilityScope = this@AnimatedVisibilityScope,
//                            // ANY transition will lead to the entire screen being animated to
//                            // exit state after list -> detail -> go back -> scroll a little bit,
//                            // I have no idea why, so just use None here
//                            enter = EnterTransition.None,
//                            exit = ExitTransition.None,
//                            renderInOverlayDuringTransition = false,
//                            placeHolderSize = SharedTransitionScope.PlaceHolderSize.animatedSize,
//                        ),
                )
            }
        }.onLoading {
            StatusItem(
                item = null,
                modifier = modifier,
            )
        }.onError {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FAIcon(
                    imageVector = FontAwesomeIcons.Solid.FileCircleExclamation,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = stringResource(Res.string.status_loadmore_error_retry),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
}

private fun LazyStaggeredGridScope.reactionContent(
    comment: PagingState<UiTimeline>,
    repost: PagingState<UiTimeline>,
    detailType: DetailType,
    onDetailTypeChange: (DetailType) -> Unit,
) {
    item(
        span = StaggeredGridItemSpan.FullLine,
    ) {
        SegmentedControl(
            modifier = Modifier.padding(horizontal = screenHorizontalPadding),
        ) {
            DetailType.entries.forEachIndexed { index, type ->
                SegmentedButton(
                    checked = detailType == type,
                    onCheckedChanged = {
                        onDetailTypeChange(type)
                    },
                    position =
                        when (index) {
                            0 -> SegmentedItemPosition.Start
                            DetailType.entries.size - 1 -> SegmentedItemPosition.End
                            else -> SegmentedItemPosition.Center
                        },
                ) {
                    Text(text = stringResource(type.title))
                }
            }
        }
    }
    when (detailType) {
        DetailType.Comment ->
            status(comment)

        DetailType.Repost ->
            status(repost)
    }
}

@Composable
private fun presenter(
    statusKey: MicroBlogKey,
    accountType: AccountType,
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

private val DetailType.title: StringResource
    get() =
        when (this) {
            DetailType.Comment -> Res.string.status_detail_comment
            DetailType.Repost -> Res.string.status_detail_repost
        }
