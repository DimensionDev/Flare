package dev.dimension.flare.data.repository

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.dimension.flare.R
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.ui.presenter.compose.ComposeProgressState
import dev.dimension.flare.ui.presenter.compose.ComposeUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

private const val CHANNEL_ID = "compose"

internal class ComposeNotifyUseCase(
    private val composeUseCase: ComposeUseCase,
    private val scope: CoroutineScope,
    private val context: Context,
) {
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    operator fun invoke(data: ComposeData) {
        val notificationId = UUID.randomUUID().hashCode()
        var builder =
            NotificationCompat.Builder(context, CHANNEL_ID)
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

        composeUseCase.invoke(data) {
            if (notificationManager.areNotificationsEnabled()) {
                scope.launch {
                    when (it) {
                        is ComposeProgressState.Error -> {
                            builder =
                                builder
                                    .setContentTitle(context.getString(R.string.compose_notification_error_title))
                                    .setContentText(context.getString(R.string.compose_notification_error_text))
                            notificationManager.notify(notificationId, builder.build())
                        }

                        is ComposeProgressState.Progress -> {
                            builder = builder.setProgress(it.max, it.current, false)
                            notificationManager.notify(notificationId, builder.build())
                        }

                        ComposeProgressState.Success -> {
                            builder =
                                builder
                                    .setContentTitle(context.getString(R.string.compose_notification_success_title))
                                    .setContentText(context.getString(R.string.compose_notification_success_text))
                                    .setProgress(0, 0, false)
                            notificationManager.notify(notificationId, builder.build())
                            delay(5.seconds)
                            notificationManager.cancel(notificationId)
                        }
                    }
                }
            }
        }
    }
}
