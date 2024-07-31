package dev.dimension.flare.ui.component.status

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.solid.Retweet
import dev.dimension.flare.R
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickContext
import dev.dimension.flare.ui.model.UiTimeline

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun UiTimelineComponent(
    item: UiTimeline,
    modifier: Modifier = Modifier,
    detailStatusKey: MicroBlogKey? = null,
) {
    Column(
        modifier = modifier,
    ) {
        item.topMessage?.let { TopMessageComponent(it) }
        Spacer(modifier = Modifier.height(4.dp))
        item.content?.let {
            ItemContentComponent(
                item = it,
                detailStatusKey = detailStatusKey,
            )
        }
    }
}

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ItemContentComponent(
    item: UiTimeline.ItemContent,
    detailStatusKey: MicroBlogKey?,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    when (item) {
        is UiTimeline.ItemContent.Status ->
            StatusContent(
                data = item,
                detailStatusKey = detailStatusKey,
                modifier = modifier,
            )

        is UiTimeline.ItemContent.User ->
            CommonStatusHeaderComponent(
                data = item.value,
                onUserClick = {
                    item.value.onClicked.invoke(
                        ClickContext(
                            launcher = {
                                uriHandler.openUri(it)
                            },
                        ),
                    )
                },
                modifier =
                    modifier
                        .padding(bottom = 8.dp),
            )

        is UiTimeline.ItemContent.UserList ->
            UserListContent(
                data = item,
                modifier =
                    modifier
                        .padding(bottom = 8.dp),
            )
    }
}

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun UserListContent(
    data: UiTimeline.ItemContent.UserList,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(data.users.size) {
            val user = data.users[it]
            Card(
                modifier = Modifier.width(256.dp),
            ) {
                CommonStatusHeaderComponent(
                    data = user,
                    onUserClick = {},
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun StatusContent(
    data: UiTimeline.ItemContent.Status,
    detailStatusKey: MicroBlogKey?,
    modifier: Modifier = Modifier,
) {
    CommonStatusComponent(
        item = data,
        isDetail = detailStatusKey == data.statusKey,
        modifier = modifier,
    )
}

@Composable
private fun TopMessageComponent(
    data: UiTimeline.TopMessage,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val icon =
        when (data.icon) {
            UiTimeline.TopMessage.Icon.Retweet -> FontAwesomeIcons.Solid.Retweet
            UiTimeline.TopMessage.Icon.Follow -> Icons.Default.PersonAdd
            UiTimeline.TopMessage.Icon.Favourite -> Icons.Default.Favorite
            UiTimeline.TopMessage.Icon.Mention -> Icons.Default.AlternateEmail
            UiTimeline.TopMessage.Icon.Poll -> Icons.Default.Poll
            UiTimeline.TopMessage.Icon.Edit -> Icons.Default.Edit
            UiTimeline.TopMessage.Icon.Info -> Icons.Default.Info
            UiTimeline.TopMessage.Icon.Reply -> Icons.AutoMirrored.Filled.Reply
        }
    val text: String =
        when (val type = data.type) {
            is UiTimeline.TopMessage.MessageType.Bluesky ->
                when (type) {
                    UiTimeline.TopMessage.MessageType.Bluesky.Follow ->
                        stringResource(id = R.string.bluesky_notification_item_followed_you)

                    UiTimeline.TopMessage.MessageType.Bluesky.Like ->
                        stringResource(
                            id = R.string.bluesky_notification_item_favourited_your_status,
                        )

                    UiTimeline.TopMessage.MessageType.Bluesky.Mention ->
                        stringResource(
                            id = R.string.bluesky_notification_item_mentioned_you,
                        )

                    UiTimeline.TopMessage.MessageType.Bluesky.Quote ->
                        stringResource(
                            id = R.string.bluesky_notification_item_quoted_your_status,
                        )

                    UiTimeline.TopMessage.MessageType.Bluesky.Reply ->
                        stringResource(
                            id = R.string.bluesky_notification_item_replied_to_you,
                        )

                    UiTimeline.TopMessage.MessageType.Bluesky.Repost ->
                        stringResource(
                            id = R.string.bluesky_notification_item_reblogged_your_status,
                        )
                }

            is UiTimeline.TopMessage.MessageType.Mastodon ->
                when (type) {
                    UiTimeline.TopMessage.MessageType.Mastodon.Favourite ->
                        stringResource(
                            id = R.string.mastodon_notification_item_favourited_your_status,
                        )

                    UiTimeline.TopMessage.MessageType.Mastodon.Follow ->
                        stringResource(
                            id = R.string.mastodon_notification_item_followed_you,
                        )

                    UiTimeline.TopMessage.MessageType.Mastodon.FollowRequest ->
                        stringResource(
                            id = R.string.mastodon_notification_item_requested_follow,
                        )

                    UiTimeline.TopMessage.MessageType.Mastodon.Mention ->
                        stringResource(
                            id = R.string.mastodon_notification_item_mentioned_you,
                        )

                    UiTimeline.TopMessage.MessageType.Mastodon.Poll ->
                        stringResource(id = R.string.mastodon_notification_item_poll_ended)

                    UiTimeline.TopMessage.MessageType.Mastodon.Reblogged ->
                        stringResource(
                            id = R.string.mastodon_notification_item_reblogged_your_status,
                        )

                    UiTimeline.TopMessage.MessageType.Mastodon.Status ->
                        stringResource(
                            id = R.string.mastodon_notification_item_posted_status,
                        )

                    UiTimeline.TopMessage.MessageType.Mastodon.Update ->
                        stringResource(
                            id = R.string.mastodon_notification_item_updated_status,
                        )
                }

            is UiTimeline.TopMessage.MessageType.Misskey ->
                when (type) {
                    UiTimeline.TopMessage.MessageType.Misskey.AchievementEarned ->
                        stringResource(
                            id = R.string.misskey_notification_item_achievement_earned,
                        )

                    UiTimeline.TopMessage.MessageType.Misskey.App ->
                        stringResource(id = R.string.misskey_notification_item_app)

                    UiTimeline.TopMessage.MessageType.Misskey.Follow ->
                        stringResource(id = R.string.misskey_notification_item_followed_you)

                    UiTimeline.TopMessage.MessageType.Misskey.FollowRequestAccepted ->
                        stringResource(
                            id = R.string.misskey_notification_item_follow_request_accepted,
                        )

                    UiTimeline.TopMessage.MessageType.Misskey.Mention ->
                        stringResource(
                            id = R.string.misskey_notification_item_mentioned_you,
                        )

                    UiTimeline.TopMessage.MessageType.Misskey.PollEnded ->
                        stringResource(
                            id = R.string.misskey_notification_item_poll_ended,
                        )

                    UiTimeline.TopMessage.MessageType.Misskey.Quote ->
                        stringResource(
                            id = R.string.misskey_notification_item_quoted_your_status,
                        )

                    UiTimeline.TopMessage.MessageType.Misskey.Reaction ->
                        stringResource(
                            id = R.string.misskey_notification_item_reacted_to_your_status,
                        )

                    UiTimeline.TopMessage.MessageType.Misskey.ReceiveFollowRequest ->
                        stringResource(
                            id = R.string.misskey_notification_item_requested_follow,
                        )

                    UiTimeline.TopMessage.MessageType.Misskey.Renote ->
                        stringResource(
                            id = R.string.misskey_notification_item_reposted_your_status,
                        )

                    UiTimeline.TopMessage.MessageType.Misskey.Reply ->
                        stringResource(
                            id = R.string.misskey_notification_item_replied_to_you,
                        )
                }

            is UiTimeline.TopMessage.MessageType.VVO ->
                when (type) {
                    is UiTimeline.TopMessage.MessageType.VVO.Custom -> type.message
                    UiTimeline.TopMessage.MessageType.VVO.Like ->
                        stringResource(id = R.string.vvo_notification_like)
                }

            is UiTimeline.TopMessage.MessageType.XQT ->
                when (type) {
                    is UiTimeline.TopMessage.MessageType.XQT.Custom -> type.message
                    UiTimeline.TopMessage.MessageType.XQT.Mention ->
                        stringResource(id = R.string.xqt_item_mention_status)

                    UiTimeline.TopMessage.MessageType.XQT.Retweet ->
                        stringResource(id = R.string.xqt_item_reblogged_status)
                }
        }

    StatusRetweetHeaderComponent(
        icon = icon,
        user = data.user,
        text = text,
        modifier =
            modifier
                .clickable {
                    data.onClicked.invoke(
                        ClickContext(
                            launcher = {
                                uriHandler.openUri(it)
                            },
                        ),
                    )
                },
    )
}
