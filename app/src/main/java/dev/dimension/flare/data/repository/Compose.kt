package dev.dimension.flare.data.repository

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.moriatsushi.koject.Provides
import dev.dimension.flare.R
import dev.dimension.flare.data.network.mastodon.api.model.PostPoll
import dev.dimension.flare.data.network.mastodon.api.model.PostStatus
import dev.dimension.flare.data.network.mastodon.api.model.Visibility
import dev.dimension.flare.data.repository.app.UiAccount
import dev.dimension.flare.ui.model.UiStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

private const val CHANNEL_ID = "compose"

@Provides
internal class ComposeUseCase(
    private val scope: CoroutineScope,
    private val context: Context
) {
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    operator fun invoke(data: ComposeData) = composeUseCase(data, scope, context)
}

@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
private fun composeUseCase(
    data: ComposeData,
    scope: CoroutineScope,
    context: Context
) {
    scope.launch {
        val notificationId = UUID.randomUUID().hashCode()
        var builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.compose_notification_title))
            .setContentText(context.getString(R.string.compose_notification_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        val notificationManager = NotificationManagerCompat.from(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_compose_name)
            val description = context.getString(R.string.notification_channel_compose_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = description
            notificationManager.createNotificationChannel(channel)
        }

        runCatching {
            when (data) {
                is ComposeData.Mastodon -> mastodonComposeUseCase(
                    data = data,
                    context = context
                ) { current, max ->
                    if (notificationManager.areNotificationsEnabled()) {
                        builder = builder.setProgress(max, current, false)
                        notificationManager.notify(notificationId, builder.build())
                    }
                }
            }
        }.onSuccess {
            if (notificationManager.areNotificationsEnabled()) {
                builder = builder
                    .setContentTitle(context.getString(R.string.compose_notification_success_title))
                    .setContentText(context.getString(R.string.compose_notification_success_text))
                    .setProgress(0, 0, false)
                notificationManager.notify(notificationId, builder.build())
                delay(5.seconds)
                notificationManager.cancel(notificationId)
            }
        }.onFailure {
            it.printStackTrace()
            if (notificationManager.areNotificationsEnabled()) {
                builder = builder
                    .setContentTitle(context.getString(R.string.compose_notification_error_title))
                    .setContentText(context.getString(R.string.compose_notification_error_text))
                notificationManager.notify(notificationId, builder.build())
            }
        }
    }
}

private suspend fun mastodonComposeUseCase(
    data: ComposeData.Mastodon,
    context: Context,
    notifyProgress: (current: Int, max: Int) -> Unit
) {
    val maxProgress = data.medias.size + 1
    val mediaIds = data.medias.mapIndexedNotNull { index, uri ->
        context.contentResolver.openInputStream(uri)?.use { stream ->
            data.account.service.upload(
                stream,
                name = uri.lastPathSegment ?: "unknown"
            ).also {
                notifyProgress(index + 1, maxProgress)
            }
        }
    }.mapNotNull {
        it.id
    }
    data.account.service.compose(
        UUID.randomUUID().toString(),
        PostStatus(
            status = data.content,
            visibility = when (data.visibility) {
                UiStatus.Mastodon.Visibility.Public -> Visibility.Public
                UiStatus.Mastodon.Visibility.Unlisted -> Visibility.Unlisted
                UiStatus.Mastodon.Visibility.Private -> Visibility.Private
                UiStatus.Mastodon.Visibility.Direct -> Visibility.Direct
            },
            inReplyToID = data.inReplyToID,
            mediaIDS = mediaIds.takeIf { it.isNotEmpty() },
            sensitive = data.sensitive.takeIf { mediaIds.isNotEmpty() },
            spoilerText = data.spoilerText.takeIf { it?.isNotEmpty() == true && it.isNotBlank() },
            poll = data.poll?.let { poll ->
                PostPoll(
                    options = poll.options,
                    expiresIn = poll.expiresIn,
                    multiple = poll.multiple
                )
            }
        )
    )
}

internal sealed interface ComposeData {
    data class Mastodon(
        val account: UiAccount.Mastodon,
        val content: String,
        val visibility: UiStatus.Mastodon.Visibility = UiStatus.Mastodon.Visibility.Public,
        val inReplyToID: String? = null,
        val medias: List<Uri> = emptyList(),
        val sensitive: Boolean = false,
        val spoilerText: String? = null,
        val poll: Poll? = null
    ) : ComposeData {
        data class Poll(
            val options: List<String>,
            val expiresIn: Long,
            val multiple: Boolean
        )
    }
}
