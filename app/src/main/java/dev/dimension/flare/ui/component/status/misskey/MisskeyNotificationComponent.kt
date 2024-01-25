package dev.dimension.flare.ui.component.status.misskey

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import dev.dimension.flare.data.network.misskey.api.model.Notification
import dev.dimension.flare.ui.component.status.CommonStatusHeaderComponent
import dev.dimension.flare.ui.component.status.StatusRetweetHeaderComponent
import dev.dimension.flare.ui.model.UiStatus

@Composable
internal fun MisskeyNotificationComponent(
    data: UiStatus.MisskeyNotification,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier,
    ) {
        when (data.type) {
            Notification.Type.Follow ->
                StatusRetweetHeaderComponent(
                    icon = Icons.Default.PersonAdd,
                    user = data.user,
                    text = stringResource(id = R.string.mastodon_notification_item_followed_you),
                )

            Notification.Type.Mention ->
                StatusRetweetHeaderComponent(
                    icon = Icons.Default.AlternateEmail,
                    user = data.user,
                    text = stringResource(id = R.string.misskey_notification_item_mentioned_you),
                )

            Notification.Type.Reply ->
                StatusRetweetHeaderComponent(
                    icon = Icons.AutoMirrored.Default.Reply,
                    user = data.user,
                    text = stringResource(id = R.string.misskey_notification_item_replied_to_you),
                )

            Notification.Type.Renote ->
                StatusRetweetHeaderComponent(
                    icon = Icons.Default.Replay,
                    user = data.user,
                    text = stringResource(id = R.string.misskey_notification_item_reposted_your_status),
                )

            Notification.Type.Quote ->
                StatusRetweetHeaderComponent(
                    icon = Icons.Default.FormatQuote,
                    user = data.user,
                    text = stringResource(id = R.string.misskey_notification_item_quoted_your_status),
                )

            Notification.Type.Reaction ->
                StatusRetweetHeaderComponent(
                    icon = Icons.Default.PersonAdd,
                    user = data.user,
                    text = stringResource(id = R.string.misskey_notification_item_reacted_to_your_status),
                )

            Notification.Type.PollEnded ->
                StatusRetweetHeaderComponent(
                    icon = Icons.Default.Poll,
                    user = null,
                    text = stringResource(id = R.string.misskey_notification_item_poll_ended),
                )

            Notification.Type.ReceiveFollowRequest ->
                StatusRetweetHeaderComponent(
                    icon = Icons.Default.PersonAdd,
                    user = data.user,
                    text = stringResource(id = R.string.mastodon_notification_item_requested_follow),
                )

            Notification.Type.FollowRequestAccepted ->
                StatusRetweetHeaderComponent(
                    icon = Icons.Default.PersonAdd,
                    user = data.user,
                    text = stringResource(id = R.string.misskey_notification_item_follow_request_accepted),
                )

            Notification.Type.AchievementEarned ->
                StatusRetweetHeaderComponent(
                    icon = Icons.Default.Info,
                    user = null,
                    text = stringResource(id = R.string.misskey_notification_item_achievement_earned),
                )

            Notification.Type.App ->
                StatusRetweetHeaderComponent(
                    icon = Icons.Default.Info,
                    user = null,
                    text = stringResource(id = R.string.misskey_notification_item_app),
                )
        }
        data.note?.let {
            MisskeyStatusComponent(data = it, event = event)
        }
        data.user?.let { user ->
            if (data.type in
                listOf(
                    Notification.Type.Follow,
                    Notification.Type.FollowRequestAccepted,
                    Notification.Type.ReceiveFollowRequest,
                )
            ) {
                CommonStatusHeaderComponent(
                    data = user,
                    onUserClick = {
                        event.onUserClick(it, uriHandler)
                    },
                )
            }
        }
    }
}
