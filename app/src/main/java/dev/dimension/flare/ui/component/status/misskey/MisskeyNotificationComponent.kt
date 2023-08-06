package dev.dimension.flare.ui.component.status.misskey

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.dimension.flare.data.network.misskey.api.model.Notification
import dev.dimension.flare.ui.model.UiStatus

@Composable
internal fun MisskeyNotificationComponent(
    data: UiStatus.MisskeyNotification,
    event: MisskeyStatusEvent,
    modifier: Modifier = Modifier
) {
    when (data.type) {
        Notification.Type.Follow -> TODO()
        Notification.Type.Mention -> TODO()
        Notification.Type.Reply -> TODO()
        Notification.Type.Renote -> TODO()
        Notification.Type.Quote -> TODO()
        Notification.Type.Reaction -> TODO()
        Notification.Type.PollEnded -> TODO()
        Notification.Type.ReceiveFollowRequest -> TODO()
        Notification.Type.FollowRequestAccepted -> TODO()
        Notification.Type.AchievementEarned -> TODO()
        Notification.Type.App -> TODO()
    }
}