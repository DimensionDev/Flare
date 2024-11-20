package dev.dimension.flare.ui.screen.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.StatusMediaRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.common.onLoading
import dev.dimension.flare.common.onSuccess
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.BackButton
import dev.dimension.flare.ui.component.FlareScaffold
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.MediaItem
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.profile.ProfileMediaPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import io.github.fornewid.placeholder.material3.placeholder
import moe.tlaster.precompose.molecule.producePresenter

@Composable
@Destination<RootGraph>(
    wrappers = [ThemeWrapper::class],
)
internal fun ProfileMediaRoute(
    userKey: MicroBlogKey?,
    accountType: AccountType,
    navigator: DestinationsNavigator,
) {
    ProfileMediaScreen(
        userKey = userKey,
        onBack = navigator::navigateUp,
        onItemClicked = { statusKey, index, preview ->
            navigator.navigate(
                StatusMediaRouteDestination(
                    statusKey = statusKey,
                    mediaIndex = index,
                    preview = preview,
                    accountType = accountType,
                ),
            )
        },
        accountType = accountType,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileMediaScreen(
    userKey: MicroBlogKey?,
    accountType: AccountType,
    onItemClicked: (statusKey: MicroBlogKey, index: Int, preview: String?) -> Unit,
    onBack: () -> Unit,
) {
    val state by producePresenter(key = "ProfileMediaScreen_${userKey?.id}_$accountType") {
        profileMediaPresenter(userKey, accountType)
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    FlareScaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.media_viewer_title))
                },
                navigationIcon = {
                    BackButton(onBack = onBack)
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(120.dp),
            contentPadding = it + PaddingValues(horizontal = screenHorizontalPadding),
            verticalItemSpacing = 8.dp,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.mediaState
                .onSuccess {
                    items(itemCount) { index ->
                        val item = get(index)
                        if (item != null) {
                            val media = item.media
                            MediaItem(
                                media = media,
                                showCountdown = false,
                                modifier =
                                    Modifier
                                        .clipToBounds()
                                        .clickable {
                                            val content = item.status.content
                                            if (content is UiTimeline.ItemContent.Status) {
                                                onItemClicked(
                                                    content.statusKey,
                                                    item.index,
                                                    when (media) {
                                                        is UiMedia.Image -> media.previewUrl
                                                        is UiMedia.Video -> media.thumbnailUrl
                                                        is UiMedia.Gif -> media.previewUrl
                                                        else -> null
                                                    },
                                                )
                                            }
                                        },
                            )
                        } else {
                            Card {
                                Box(modifier = Modifier.size(120.dp).placeholder(true))
                            }
                        }
                    }
                }.onLoading {
                    items(10) {
                        Box(modifier = Modifier.size(120.dp).placeholder(true))
                    }
                }
        }
    }
}

@Composable
private fun profileMediaPresenter(
    userKey: MicroBlogKey?,
    accountType: AccountType,
) = run {
    remember(userKey, accountType) {
        ProfileMediaPresenter(
            accountType = accountType,
            userKey = userKey,
        )
    }.invoke()
}
