package dev.dimension.flare.ui.component.status.vvo

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dimension.flare.R
import dev.dimension.flare.ui.component.status.StatusHeaderComponent
import dev.dimension.flare.ui.component.status.UiStatusQuoted
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.localizedShortTime

context(AnimatedVisibilityScope, SharedTransitionScope)
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun VVONotificationComponent(
    data: UiStatus.VVONotification,
    event: VVOStatusEvent,
    modifier: Modifier = Modifier,
    showStatus: Boolean = true,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = modifier,
    ) {
        data.displayUser?.let {
            StatusHeaderComponent(
                user = it,
                humanizedTime = data.localizedShortTime,
                onUserClick = {
                    event.onUserClick(
                        accountKey = data.accountKey,
                        userKey = it,
                        uriHandler = uriHandler,
                    )
                },
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(id = R.string.vvo_notification_like),
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (showStatus) {
            data.status?.let { status ->
                UiStatusQuoted(
                    status = status,
                    onMediaClick = {
                        event.onMediaClick(
                            accountKey = data.accountKey,
                            statusKey = status.statusKey,
                            index = status.medias.indexOf(it),
                            uriHandler = uriHandler,
                            preview =
                                when (it) {
                                    is UiMedia.Image -> it.previewUrl
                                    is UiMedia.Video -> it.thumbnailUrl
                                    is UiMedia.Gif -> it.previewUrl
                                    else -> null
                                },
                        )
                    },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
