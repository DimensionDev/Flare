package dev.dimension.flare.ui.component.status.misskey

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.data.network.misskey.api.model.Notification
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.HtmlText2
import dev.dimension.flare.ui.component.status.StatusRetweetHeaderComponent
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.nameDirection
import dev.dimension.flare.ui.theme.MediumAlpha

@Composable
internal fun MisskeyNotificationComponent(
    data: UiStatus.MisskeyNotification,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    when (data.type) {
        Notification.Type.Follow ->
            MisskeyFollowNotificationComponent(
                data = data,
                event = event,
                modifier = modifier,
            )

        Notification.Type.Mention ->
            MisskeyMentionNotificationComponent(
                data = data,
                event = event,
                modifier = modifier,
            )

        Notification.Type.Reply ->
            MisskeyReplyNotificationComponent(
                data = data,
                event = event,
                modifier = modifier,
            )
        Notification.Type.Renote ->
            MisskeyRetweetNotificationComponent(
                data = data,
                event = event,
                modifier = modifier,
            )
        Notification.Type.Quote ->
            MisskeyQuoteNotificationComponent(
                data = data,
                event = event,
                modifier = modifier,
            )
        Notification.Type.Reaction ->
            MisskeyReactionNotificationComponent(
                data = data,
                event = event,
                modifier = modifier,
            )
        Notification.Type.PollEnded ->
            MisskeyPollEndedNotificationComponent(
                data = data,
                event = event,
                modifier = modifier,
            )
        Notification.Type.ReceiveFollowRequest ->
            MisskeyReceiveFollowRequestNotificationComponent(
                data = data,
                event = event,
                modifier = modifier,
            )
        Notification.Type.FollowRequestAccepted ->
            MisskeyFollowRequestAcceptedNotificationComponent(
                data = data,
                event = event,
                modifier = modifier,
            )
        Notification.Type.AchievementEarned ->
            MisskeyAchievementEarnedNotificationComponent(
                data = data,
                event = event,
                modifier = modifier,
            )
        Notification.Type.App ->
            MisskeyAppNotificationComponent(
                data = data,
                event = event,
                modifier = modifier,
            )
    }
}

@Composable
private fun MisskeyAppNotificationComponent(
    data: UiStatus.MisskeyNotification,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        Text(text = stringResource(id = R.string.misskey_notification_item_app))
    }
}

@Composable
private fun MisskeyAchievementEarnedNotificationComponent(
    data: UiStatus.MisskeyNotification,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        Text(text = stringResource(id = R.string.misskey_notification_item_achievement_earned))
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = data.achievement.orEmpty(), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun MisskeyFollowRequestAcceptedNotificationComponent(
    data: UiStatus.MisskeyNotification,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        StatusRetweetHeaderComponent(
            icon = Icons.Default.PersonAdd,
            user = data.user,
            text = stringResource(id = R.string.misskey_notification_item_follow_request_accepted),
        )
        data.user?.let { user ->
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarComponent(
                    data = user.avatarUrl,
                    modifier =
                        Modifier
                            .clickable {
                                event.onUserClick(user.userKey)
                            },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    modifier =
                        Modifier
                            .weight(1f),
                ) {
                    HtmlText2(
                        element = user.nameElement,
                        layoutDirection = user.nameDirection,
                        modifier =
                            Modifier
                                .clickable {
                                    event.onUserClick(user.userKey)
                                },
                    )
                    Text(
                        text = user.handle,
                        style = MaterialTheme.typography.bodySmall,
                        modifier =
                            Modifier
                                .alpha(MediumAlpha)
                                .clickable {
                                    event.onUserClick(user.userKey)
                                },
                    )
                }
            }
        }
    }
}

@Composable
private fun MisskeyReceiveFollowRequestNotificationComponent(
    data: UiStatus.MisskeyNotification,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        StatusRetweetHeaderComponent(
            icon = Icons.Default.PersonAdd,
            user = data.user,
            text = stringResource(id = R.string.mastodon_notification_item_requested_follow),
        )
        data.user?.let { user ->
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarComponent(
                    data = user.avatarUrl,
                    modifier =
                        Modifier
                            .clickable {
                                event.onUserClick(user.userKey)
                            },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    modifier =
                        Modifier
                            .weight(1f),
                ) {
                    HtmlText2(
                        element = user.nameElement,
                        layoutDirection = user.nameDirection,
                        modifier =
                            Modifier
                                .clickable {
                                    event.onUserClick(user.userKey)
                                },
                    )
                    Text(
                        text = user.handle,
                        style = MaterialTheme.typography.bodySmall,
                        modifier =
                            Modifier
                                .alpha(MediumAlpha)
                                .clickable {
                                    event.onUserClick(user.userKey)
                                },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun MisskeyPollEndedNotificationComponent(
    data: UiStatus.MisskeyNotification,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        StatusRetweetHeaderComponent(
            icon = Icons.Default.PersonAdd,
            user = null,
            text = stringResource(id = R.string.misskey_notification_item_poll_ended),
        )
        Spacer(modifier = Modifier.height(8.dp))
        data.note?.let { MisskeyStatusComponent(data = it, event = event) }
    }
}

@Composable
private fun MisskeyReactionNotificationComponent(
    data: UiStatus.MisskeyNotification,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        StatusRetweetHeaderComponent(
            icon = Icons.Default.PersonAdd,
            user = data.user,
            text = stringResource(id = R.string.misskey_notification_item_reacted_to_your_status),
        )
        Spacer(modifier = Modifier.height(8.dp))
        data.note?.let { MisskeyStatusComponent(data = it, event = event) }
    }
}

@Composable
private fun MisskeyQuoteNotificationComponent(
    data: UiStatus.MisskeyNotification,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        StatusRetweetHeaderComponent(
            icon = Icons.Default.PersonAdd,
            user = data.user,
            text = stringResource(id = R.string.misskey_notification_item_quoted_your_status),
        )
        Spacer(modifier = Modifier.height(8.dp))
        data.note?.let { MisskeyStatusComponent(data = it, event = event) }
    }
}

@Composable
private fun MisskeyRetweetNotificationComponent(
    data: UiStatus.MisskeyNotification,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        StatusRetweetHeaderComponent(
            icon = Icons.Default.PersonAdd,
            user = data.user,
            text = stringResource(id = R.string.misskey_notification_item_reposted_your_status),
        )
        Spacer(modifier = Modifier.height(8.dp))
        data.note?.let { MisskeyStatusComponent(data = it, event = event) }
    }
}

@Composable
private fun MisskeyReplyNotificationComponent(
    data: UiStatus.MisskeyNotification,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        StatusRetweetHeaderComponent(
            icon = Icons.Default.PersonAdd,
            user = data.user,
            text = stringResource(id = R.string.misskey_notification_item_replied_to_you),
        )
        Spacer(modifier = Modifier.height(8.dp))
        data.note?.let { MisskeyStatusComponent(data = it, event = event) }
    }
}

@Composable
private fun MisskeyMentionNotificationComponent(
    data: UiStatus.MisskeyNotification,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        StatusRetweetHeaderComponent(
            icon = Icons.Default.PersonAdd,
            user = data.user,
            text = stringResource(id = R.string.misskey_notification_item_mentioned_you),
        )
        Spacer(modifier = Modifier.height(8.dp))
        data.note?.let { MisskeyStatusComponent(data = it, event = event) }
    }
}

@Composable
private fun MisskeyFollowNotificationComponent(
    data: UiStatus.MisskeyNotification,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        StatusRetweetHeaderComponent(
            icon = Icons.Default.PersonAdd,
            user = data.user,
            text = stringResource(id = R.string.mastodon_notification_item_followed_you),
        )
        data.user?.let { user ->
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarComponent(
                    data = user.avatarUrl,
                    modifier =
                        Modifier
                            .clickable {
                                event.onUserClick(user.userKey)
                            },
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(
                    modifier =
                        Modifier
                            .weight(1f),
                ) {
                    HtmlText2(
                        element = user.nameElement,
                        layoutDirection = user.nameDirection,
                        modifier =
                            Modifier
                                .clickable {
                                    event.onUserClick(user.userKey)
                                },
                    )
                    Text(
                        text = user.handle,
                        style = MaterialTheme.typography.bodySmall,
                        modifier =
                            Modifier
                                .alpha(MediumAlpha)
                                .clickable {
                                    event.onUserClick(user.userKey)
                                },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
