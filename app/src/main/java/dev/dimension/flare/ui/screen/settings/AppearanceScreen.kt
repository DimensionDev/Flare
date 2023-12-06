package dev.dimension.flare.ui.screen.settings

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.dimension.flare.R
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.component.ThemeWrapper
import dev.dimension.flare.ui.component.status.StatusEvent
import dev.dimension.flare.ui.component.status.StatusItem
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.map
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.ActiveAccountPresenter
import dev.dimension.flare.ui.presenter.home.ActiveAccountState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.Clock

@Destination(
    wrappers = [ThemeWrapper::class],
)
@Composable
fun AppearanceRoute(navigator: DestinationsNavigator) {
    AppearanceScreen(
        onBack = navigator::navigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceScreen(onBack: () -> Unit) {
    val state by producePresenter { appearancePresenter() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.settings_appearance_title))
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
        Column(
            modifier =
                Modifier
                    .padding(it)
                    .verticalScroll(rememberScrollState()),
        ) {
            state.sampleStatus.onSuccess {
                StatusItem(it, StatusEvent.empty)
            }
            HorizontalDivider()
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(id = R.string.settings_appearance_generic),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
            )
            BoxWithConstraints {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_theme))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_theme_description))
                    },
                    trailingContent = {
                        if (maxWidth >= 400.dp) {
                            SingleChoiceSegmentedButtonRow {
                                SegmentedButton(
                                    selected = true,
                                    onClick = {},
                                    shape =
                                        SegmentedButtonDefaults.itemShape(
                                            index = 0,
                                            count = 3,
                                        ),
                                ) {
                                    Text(text = stringResource(id = R.string.settings_appearance_theme_light))
                                }
                                SegmentedButton(
                                    selected = false,
                                    onClick = {},
                                    shape =
                                        SegmentedButtonDefaults.itemShape(
                                            index = 1,
                                            count = 3,
                                        ),
                                ) {
                                    Text(text = stringResource(id = R.string.settings_appearance_theme_auto))
                                }
                                SegmentedButton(
                                    selected = false,
                                    onClick = {},
                                    shape =
                                        SegmentedButtonDefaults.itemShape(
                                            index = 2,
                                            count = 3,
                                        ),
                                ) {
                                    Text(text = stringResource(id = R.string.settings_appearance_theme_dark))
                                }
                            }
                        } else {
                            TextButton(onClick = {}) {
                                Text(text = stringResource(id = R.string.settings_appearance_theme_light))
                            }
                        }
                    },
                )
            }
            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_dynamic_theme))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_dynamic_theme_description))
                },
                trailingContent = {
                    Switch(checked = true, onCheckedChange = {})
                },
            )
            BoxWithConstraints {
                ListItem(
                    headlineContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_avatar_shape))
                    },
                    supportingContent = {
                        Text(text = stringResource(id = R.string.settings_appearance_avatar_shape_description))
                    },
                    trailingContent = {
                        if (maxWidth >= 400.dp) {
                            SingleChoiceSegmentedButtonRow {
                                SegmentedButton(
                                    selected = true,
                                    onClick = {},
                                    shape =
                                        SegmentedButtonDefaults.itemShape(
                                            index = 0,
                                            count = 2,
                                        ),
                                ) {
                                    Text(text = stringResource(id = R.string.settings_appearance_avatar_shape_round))
                                }
                                SegmentedButton(
                                    selected = false,
                                    onClick = {},
                                    shape =
                                        SegmentedButtonDefaults.itemShape(
                                            index = 1,
                                            count = 2,
                                        ),
                                ) {
                                    Text(text = stringResource(id = R.string.settings_appearance_avatar_shape_square))
                                }
                            }
                        } else {
                            TextButton(onClick = {}) {
                                Text(text = stringResource(id = R.string.settings_appearance_avatar_shape_round))
                            }
                        }
                    },
                )
            }
            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_show_actions))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_show_actions_description))
                },
                trailingContent = {
                    Switch(checked = true, onCheckedChange = {})
                },
            )
            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_show_numbers))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_show_numbers_description))
                },
                trailingContent = {
                    Switch(checked = true, onCheckedChange = {})
                },
            )
            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_show_link_previews))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_show_link_previews_description))
                },
                trailingContent = {
                    Switch(checked = true, onCheckedChange = {})
                },
            )
            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_show_media))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_show_media_description))
                },
                trailingContent = {
                    Switch(checked = true, onCheckedChange = {})
                },
            )
            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_show_cw_img))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_show_cw_img_description))
                },
                trailingContent = {
                    Switch(checked = true, onCheckedChange = {})
                },
            )
            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_enable_swipe))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_enable_swipe_description))
                },
                trailingContent = {
                    Switch(checked = true, onCheckedChange = {})
                },
            )
            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_swipe_left))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_swipe_left_description))
                },
                trailingContent = {
                    TextButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = null,
                        )
                        Text(text = "Retoot")
                    }
                },
            )

            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_swipe_right))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.settings_appearance_swipe_right_description))
                },
                trailingContent = {
                    TextButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = null,
                        )
                        Text(text = "Like")
                    }
                },
            )

            state.user.onSuccess { user ->
                ListItem(
                    headlineContent = {
                        when (user) {
                            is UiUser.Bluesky ->
                                Text(
                                    text = "Bluesky",
                                    style = MaterialTheme.typography.titleMedium,
                                )

                            is UiUser.Mastodon ->
                                Text(
                                    text = "Mastodon",
                                    style = MaterialTheme.typography.titleMedium,
                                )

                            is UiUser.Misskey ->
                                Text(
                                    text = "Misskey",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                        }
                    },
                )

                when (user) {
                    is UiUser.Bluesky -> BlueskyAppearance(state)
                    is UiUser.Mastodon -> MastodonAppearance(state)
                    is UiUser.Misskey -> MisskeyAppearance(state)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.BlueskyAppearance(state: ActiveAccountState) {
}

@Composable
private fun ColumnScope.MastodonAppearance(state: ActiveAccountState) {
    ListItem(
        headlineContent = {
            Text(text = stringResource(id = R.string.settings_appearance_mastodon_show_visibility))
        },
        supportingContent = {
            Text(text = stringResource(id = R.string.settings_appearance_mastodon_show_visibility_description))
        },
        trailingContent = {
            Switch(checked = true, onCheckedChange = {})
        },
    )
}

@Composable
private fun ColumnScope.MisskeyAppearance(state: ActiveAccountState) {
}

@Composable
private fun appearancePresenter() =
    run {
        val activeAccountState = remember { ActiveAccountPresenter() }.invoke()
        val sampleStatus =
            activeAccountState.user.map {
                when (it) {
                    is UiUser.Bluesky -> createBlueskyStatus(it)
                    is UiUser.Mastodon -> createMastodonStatus(it)
                    is UiUser.Misskey -> createMisskeyStatus(it)
                }
            }

        object : ActiveAccountState by activeAccountState {
            val sampleStatus = sampleStatus
        }
    }

fun createMastodonStatus(user: UiUser.Mastodon): UiStatus.Mastodon {
    return UiStatus.Mastodon(
        statusKey = MicroBlogKey(id = "123", host = user.userKey.host),
        accountKey = MicroBlogKey(id = "456", host = user.userKey.host),
        user = user,
        content = "Sample content for Mastodon status",
        contentWarningText = null,
        matrices =
            UiStatus.Mastodon.Matrices(
                replyCount = 10,
                reblogCount = 5,
                favouriteCount = 15,
            ),
        media = persistentListOf(),
        createdAt = Clock.System.now(),
        visibility = UiStatus.Mastodon.Visibility.Public,
        poll = null,
        card = null,
        reaction =
            UiStatus.Mastodon.Reaction(
                liked = false,
                reblogged = false,
                bookmarked = false,
            ),
        sensitive = false,
        reblogStatus = null,
        raw = Status(),
    )
}

fun createBlueskyStatus(user: UiUser.Bluesky): UiStatus.Bluesky {
    return UiStatus.Bluesky(
        accountKey = MicroBlogKey(id = "123", host = user.userKey.host),
        statusKey = MicroBlogKey(id = "456", host = user.userKey.host),
        user = user,
        indexedAt = Clock.System.now(),
        repostBy = null,
        quote = null,
        content = "Bluesky post content",
        medias = persistentListOf(),
        card = null,
        matrices =
            UiStatus.Bluesky.Matrices(
                replyCount = 20,
                likeCount = 30,
                repostCount = 40,
            ),
        reaction =
            UiStatus.Bluesky.Reaction(
                repostUri = null,
                likedUri = null,
            ),
        cid = "cid_sample",
        uri = "https://bluesky.post/uri",
    )
}

fun createMisskeyStatus(user: UiUser.Misskey): UiStatus.Misskey {
    return UiStatus.Misskey(
        statusKey = MicroBlogKey(id = "123", host = user.userKey.host),
        accountKey = MicroBlogKey(id = "456", host = user.userKey.host),
        user = user,
        content = "Misskey post content",
        contentWarningText = null,
        matrices =
            UiStatus.Misskey.Matrices(
                replyCount = 15,
                renoteCount = 25,
            ),
        media = persistentListOf(),
        createdAt = Clock.System.now(),
        visibility = UiStatus.Misskey.Visibility.Public,
        poll = null,
        card = null,
        reaction =
            UiStatus.Misskey.Reaction(
                emojiReactions = persistentListOf(),
                myReaction = null,
            ),
        sensitive = false,
        quote = null,
        renote = null,
    )
}
