package dev.dimension.flare.ui.screen.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.annotation.parameters.DeepLink
import com.ramcosta.composedestinations.annotation.parameters.FULL_ROUTE_PLACEHOLDER
import com.ramcosta.composedestinations.bottomsheet.spec.DestinationStyleBottomSheet
import com.ramcosta.composedestinations.generated.destinations.ProfileRouteDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.common.PodcastManager
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.RichText
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onError
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.presenter.podcast.PodcastPresenter
import dev.dimension.flare.ui.theme.screenHorizontalPadding
import moe.tlaster.precompose.molecule.producePresenter
import org.koin.compose.koinInject

@Composable
@Destination<RootGraph>(
    style = DestinationStyleBottomSheet::class,
    deepLinks = [
        DeepLink(
            uriPattern = "flare://$FULL_ROUTE_PLACEHOLDER",
        ),
    ],
    wrappers = [ThemeWrapper::class],
)
internal fun PodcastRoute(
    accountType: AccountType,
    id: String,
    navigator: DestinationsNavigator,
) {
    PodcastScreen(
        accountType = accountType,
        id = id,
        toUser = {
            navigator.navigate(
                ProfileRouteDestination(
                    accountType = accountType,
                    userKey = it,
                ),
            )
        },
    )
}

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
internal fun PodcastDeeplinkRoute(
    accountKey: MicroBlogKey,
    id: String,
    navigator: DestinationsNavigator,
) {
    PodcastScreen(
        accountType = AccountType.Specific(accountKey),
        id = id,
        toUser = {
            navigator.navigate(
                ProfileRouteDestination(
                    accountType = AccountType.Specific(accountKey),
                    userKey = it,
                ),
            )
        },
    )
}

@Composable
private fun PodcastScreen(
    accountType: AccountType,
    id: String,
    toUser: (MicroBlogKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by producePresenter("podcast_$accountType$id") {
        presenter(accountType, id)
    }
    Column(
        modifier =
            modifier
                .padding(
                    horizontal = screenHorizontalPadding,
                ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.data
            .onSuccess { data ->
                Text(data.title, style = MaterialTheme.typography.titleSmall)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(72.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(data.hosts) { item ->
                        UserItem(
                            item = item,
                            modifier =
                                Modifier.clickable {
                                    toUser(item.key)
                                },
                        ) {
                            Text(stringResource(R.string.podcast_host))
                        }
                    }
                    items(data.speakers) { item ->
                        UserItem(
                            item = item,
                            modifier =
                                Modifier.clickable {
                                    toUser(item.key)
                                },
                        ) {
                            Text(stringResource(R.string.podcast_speaker))
                        }
                    }
                }
                if (data.ended) {
                    Button(
                        onClick = {
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                    ) {
                        Text(stringResource(R.string.podcast_ended))
                    }
                } else {
                    state.isPlaying.onSuccess {
                        if (it) {
                            Button(
                                onClick = {
                                    state.leavePodcast()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.podcast_leave))
                            }
                        } else {
                            FilledTonalButton(
                                onClick = {
                                    state.playPodcast()
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.podcast_listen))
                            }
                        }
                    }
                }
            }.onLoading {
            }.onError {
            }
    }
}

@Composable
private fun UserItem(
    item: UiUserV2,
    modifier: Modifier = Modifier,
    bottom: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        AvatarComponent(
            data = item.avatar,
        )
        RichText(
            text = item.name,
            maxLines = 1,
        )
        bottom.invoke()
    }
}

@Composable
private fun presenter(
    accountType: AccountType,
    id: String,
    podcastManager: PodcastManager = koinInject(),
) = run {
    val state =
        remember(
            accountType,
            id,
        ) {
            PodcastPresenter(
                accountType = accountType,
                id = id,
            )
        }.invoke()
    val current by podcastManager.currentPodcast.collectAsState(null)
    val isPlaying =
        state.data.map { data ->
            current?.id == data.id
        }
    object : PodcastPresenter.State by state {
        val isPlaying: UiState<Boolean> = isPlaying

        fun playPodcast() {
            data.onSuccess {
                podcastManager.playPodcast(
                    podcast = it,
                )
            }
        }

        fun leavePodcast() {
            data.onSuccess {
                podcastManager.stopPodcast()
            }
        }
    }
}
