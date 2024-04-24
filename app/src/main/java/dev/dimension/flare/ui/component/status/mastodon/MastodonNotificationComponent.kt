package dev.dimension.flare.ui.component.status.mastodon

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import dev.dimension.flare.R
import dev.dimension.flare.data.network.mastodon.api.model.NotificationTypes
import dev.dimension.flare.ui.component.status.CommonStatusHeaderComponent
import dev.dimension.flare.ui.component.status.StatusRetweetHeaderComponent
import dev.dimension.flare.ui.model.UiStatus

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MastodonNotificationComponent(
    data: UiStatus.MastodonNotification,
    event: MastodonStatusEvent,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier,
    ) {
        when (data.type) {
            NotificationTypes.Follow ->
                StatusRetweetHeaderComponent(
                    icon = Icons.Default.PersonAdd,
                    user = data.user,
                    text = stringResource(id = R.string.mastodon_notification_item_followed_you),
                )

            NotificationTypes.Favourite ->
                StatusRetweetHeaderComponent(
                    icon = Icons.Default.Favorite,
                    user = data.user,
                    text = stringResource(id = R.string.mastodon_notification_item_favourited_your_status),
                )

            NotificationTypes.Reblog ->
                StatusRetweetHeaderComponent(
                    icon = Icons.Default.SyncAlt,
                    user = data.user,
                    text = stringResource(id = R.string.mastodon_notification_item_reblogged_your_status),
                )

            NotificationTypes.Mention -> {
                StatusRetweetHeaderComponent(
                    icon = Icons.Default.AlternateEmail,
                    user = data.user,
                    text = stringResource(id = R.string.mastodon_notification_item_mentioned_you),
                )
            }

            NotificationTypes.Poll ->
                StatusRetweetHeaderComponent(
                    icon = Icons.Default.Poll,
                    user = null,
                    text = stringResource(id = R.string.mastodon_notification_item_poll_ended),
                )

            NotificationTypes.FollowRequest ->
                StatusRetweetHeaderComponent(
                    icon = Icons.Default.PersonAdd,
                    user = data.user,
                    text = stringResource(id = R.string.mastodon_notification_item_requested_follow),
                )

            NotificationTypes.Status ->
                StatusRetweetHeaderComponent(
                    icon = Icons.Default.Add,
                    user = data.user,
                    text = stringResource(id = R.string.mastodon_notification_item_posted_status),
                )

            NotificationTypes.Update ->
                StatusRetweetHeaderComponent(
                    icon = Icons.Default.Edit,
                    user = data.user,
                    text = stringResource(id = R.string.mastodon_notification_item_updated_status),
                )
        }
        data.status?.let { status ->
            MastodonStatusComponent(
                data = status,
                event = event,
            )
        }
        if (data.type in listOf(NotificationTypes.Follow, NotificationTypes.FollowRequest)) {
            CommonStatusHeaderComponent(
                data = data.user,
                onUserClick = {
                    event.onUserClick(accountKey = data.accountKey, userKey = data.user.userKey, uriHandler = uriHandler)
                },
            )
        }
    }
}
