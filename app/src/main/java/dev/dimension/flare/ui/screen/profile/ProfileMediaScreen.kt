package dev.dimension.flare.ui.screen.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.DeepLink
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.common.plus
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.placeholder.placeholder
import dev.dimension.flare.ui.component.status.MediaItem
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.profile.ProfileMediaPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding

@Composable
@Destination(
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
        DeepLink(
            uriPattern = AppDeepLink.Profile.ROUTE,
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
internal fun ProfileMediaRoute(
    userKey: MicroBlogKey?,
    navigator: DestinationsNavigator,
) {
    ProfileMediaScreen(
        userKey = userKey,
        onBack = navigator::navigateUp,
        onItemClicked = { media ->
            if (media is UiMedia.Image) {
                navigator.navigate(
                    dev.dimension.flare.ui.screen.destinations.MediaRouteDestination(media.url),
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileMediaScreen(
    userKey: MicroBlogKey?,
    onItemClicked: (UiMedia) -> Unit,
    onBack: () -> Unit,
) {
    val state by producePresenter(key = userKey.toString()) {
        profileMediaPresenter(userKey)
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.media_viewer_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_back),
                        )
                    }
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
            state.mediaState.onSuccess { items ->
                items(items.itemCount) { index ->
                    val item = items[index]
                    if (item != null) {
                        MediaItem(media = item, modifier = Modifier.clickable { onItemClicked(item) })
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
private fun profileMediaPresenter(userKey: MicroBlogKey?) =
    run {
        remember(userKey) { ProfileMediaPresenter(userKey) }.invoke()
    }
