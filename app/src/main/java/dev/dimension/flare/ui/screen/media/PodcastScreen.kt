package dev.dimension.flare.ui.screen.media

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.annotation.parameters.DeepLink
import com.ramcosta.composedestinations.annotation.parameters.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.bottomsheet.spec.DestinationStyleBottomSheet
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.podcast.PodcastPresenter
import moe.tlaster.precompose.molecule.producePresenter

@Composable
@Destination<RootGraph>(
    style = DestinationStyleBottomSheet::class,
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
        DeepLink(
            uriPattern = AppDeepLink.Podcast.ROUTE,
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
internal fun PodcastRoute(
    accountKey: MicroBlogKey,
    id: String,
    navigator: DestinationsNavigator,
) {
    PodcastScreen(
        accountKey = accountKey,
        id = id,
    )
}

@Composable
private fun PodcastScreen(
    accountKey: MicroBlogKey,
    id: String,
    modifier: Modifier = Modifier,
) {
    val state by producePresenter("podcast_$accountKey$id") {
        presenter(accountKey, id)
    }
    Column {
    }
}

@Composable
private fun presenter(
    accountKey: MicroBlogKey,
    id: String,
) = run {
    remember(
        accountKey,
        id,
    ) {
        PodcastPresenter(
            accountKey = accountKey,
            id = id,
        )
    }.invoke()
}
