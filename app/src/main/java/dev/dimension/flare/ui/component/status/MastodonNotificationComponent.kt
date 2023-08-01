package dev.dimension.flare.ui.component.status

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.data.network.mastodon.api.model.NotificationTypes
import dev.dimension.flare.ui.component.AvatarComponent
import dev.dimension.flare.ui.component.HtmlText
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.theme.MediumAlpha

@Composable
internal fun MastodonNotificationComponent(
    data: UiStatus.MastodonNotification,
    state: MastodonStatusState,
    event: MastodonStatusEvent,
    modifier: Modifier = Modifier,
) {
    when (data.type) {
        NotificationTypes.follow -> MastodonFollowNotificationComponent(
            data = data,
            state = state,
            event = event,
            modifier = modifier
        )

        NotificationTypes.favourite -> MastodonFavouriteNotificationComponent(
            data = data,
            state = state,
            event = event,
            modifier = modifier
        )

        NotificationTypes.reblog -> MastodonRetweetNotificationComponent(
            data = data,
            state = state,
            event = event,
            modifier = modifier
        )
        NotificationTypes.mention -> {
            if (data.status != null) {
                MastodonStatusComponent(
                    data = data.status,
                    state = state,
                    event = event,
                    modifier = modifier
                )
            } else {
                // TODO: Handle this case
            }
        }

        NotificationTypes.poll -> MastodonPollNotificationComponent(
            data = data,
            state = state,
            event = event,
            modifier = modifier
        )
        NotificationTypes.follow_request -> MastodonFollowRequestNotificationComponent(
            data = data,
            state = state,
            event = event,
            modifier = modifier
        )
        NotificationTypes.status -> MastodonStatusNotificationComponent(
            data = data,
            state = state,
            event = event,
            modifier = modifier
        )
        NotificationTypes.update -> MastodonUpdateNotificationComponent(
            data = data,
            state = state,
            event = event,
            modifier = modifier
        )
    }
}

@Composable
private fun MastodonUpdateNotificationComponent(
    data: UiStatus.MastodonNotification,
    state: MastodonStatusState,
    event: MastodonStatusEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        StatusRetweetHeaderComponent(
            icon = Icons.Default.Edit,
            user = data.user,
            text = stringResource(id = R.string.mastodon_notification_item_updated_status),
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (data.status != null) {
            MastodonStatusComponent(
                data = data.status,
                state = state,
                event = event,
            )
        }
    }
}

@Composable
private fun MastodonStatusNotificationComponent(
    data: UiStatus.MastodonNotification,
    state: MastodonStatusState,
    event: MastodonStatusEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        StatusRetweetHeaderComponent(
            icon = Icons.Default.Add,
            user = data.user,
            text = stringResource(id = R.string.mastodon_notification_item_posted_status),
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (data.status != null) {
            MastodonStatusComponent(
                data = data.status,
                state = state,
                event = event,
            )
        }
    }
}

@Composable
private fun MastodonFollowRequestNotificationComponent(
    data: UiStatus.MastodonNotification,
    state: MastodonStatusState,
    event: MastodonStatusEvent,
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
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarComponent(
                data = data.user.avatarUrl,
                modifier = Modifier
                    .clickable {
                        event.onUserClick(data.user.userKey)
                    }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier
                    .weight(1f),
            ) {
                HtmlText(
                    element = data.user.nameElement,
                    layoutDirection = data.user.nameDirection,
                    modifier = Modifier
                        .clickable {
                            event.onUserClick(data.user.userKey)
                        }
                )
                Text(
                    text = data.user.handle,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .alpha(MediumAlpha)
                        .clickable {
                            event.onUserClick(data.user.userKey)
                        }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun MastodonPollNotificationComponent(
    data: UiStatus.MastodonNotification,
    state: MastodonStatusState,
    event: MastodonStatusEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        StatusRetweetHeaderComponent(
            icon = Icons.Default.Poll,
            user = null,
            text = stringResource(id = R.string.mastodon_notification_item_poll_ended),
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (data.status != null) {
            MastodonStatusComponent(
                data = data.status,
                state = state,
                event = event,
            )
        }
    }
}

@Composable
private fun MastodonRetweetNotificationComponent(
    data: UiStatus.MastodonNotification,
    state: MastodonStatusState,
    event: MastodonStatusEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        StatusRetweetHeaderComponent(
            icon = Icons.Default.SyncAlt,
            user = data.user,
            text = stringResource(id = R.string.mastodon_notification_item_reblogged_your_status),
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (data.status != null) {
            MastodonStatusComponent(
                data = data.status,
                state = state,
                event = event,
            )
        }
    }
}

@Composable
private fun MastodonFavouriteNotificationComponent(
    data: UiStatus.MastodonNotification,
    state: MastodonStatusState,
    event: MastodonStatusEvent,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        StatusRetweetHeaderComponent(
            icon = Icons.Default.Favorite,
            user = data.user,
            text = stringResource(id = R.string.mastodon_notification_item_favourited_your_status),
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (data.status != null) {
            MastodonStatusComponent(
                data = data.status,
                state = state,
                event = event,
            )
        }
    }
}

@Composable
private fun MastodonFollowNotificationComponent(
    data: UiStatus.MastodonNotification,
    state: MastodonStatusState,
    event: MastodonStatusEvent,
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
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarComponent(
                data = data.user.avatarUrl,
                modifier = Modifier
                    .clickable {
                        event.onUserClick(data.user.userKey)
                    }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                modifier = Modifier
                    .weight(1f),
            ) {
                HtmlText(
                    element = data.user.nameElement,
                    layoutDirection = data.user.nameDirection,
                    modifier = Modifier
                        .clickable {
                            event.onUserClick(data.user.userKey)
                        }
                )
                Text(
                    text = data.user.handle,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .alpha(MediumAlpha)
                        .clickable {
                            event.onUserClick(data.user.userKey)
                        }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
