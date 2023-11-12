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
import dev.dimension.flare.data.datasource.ComposeData
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

private const val CHANNEL_ID = "compose"

internal class ComposeUseCase(
    private val scope: CoroutineScope,
    private val context: Context,
) {
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    operator fun invoke(data: ComposeData) = composeUseCase(data, scope, context)
}

@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
private fun composeUseCase(
    data: ComposeData,
    scope: CoroutineScope,
    context: Context,
) {
    scope.launch {
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

        runCatching {
            when (data) {
                is MastodonDataSource.MastodonComposeData ->
                    data.account.dataSource.compose(
                        data = data,
                    ) { (current, max) ->
                        if (notificationManager.areNotificationsEnabled()) {
                            builder = builder.setProgress(max, current, false)
                            notificationManager.notify(notificationId, builder.build())
                        }
                    }

                is MisskeyDataSource.MissKeyComposeData ->
                    data.account.dataSource.compose(
                        data = data,
                    ) { (current, max) ->
                        if (notificationManager.areNotificationsEnabled()) {
                            builder = builder.setProgress(max, current, false)
                            notificationManager.notify(notificationId, builder.build())
                        }
                    }

                else -> Unit

//                is ComposeData.Bluesky -> blueskyComposeUseCase(
//                    data = data,
//                    context = context,
//                ) { current, max ->
//                    if (notificationManager.areNotificationsEnabled()) {
//                        builder = builder.setProgress(max, current, false)
//                        notificationManager.notify(notificationId, builder.build())
//                    }
//                }
            }
        }.onSuccess {
            if (notificationManager.areNotificationsEnabled()) {
                builder =
                    builder
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
                builder =
                    builder
                        .setContentTitle(context.getString(R.string.compose_notification_error_title))
                        .setContentText(context.getString(R.string.compose_notification_error_text))
                notificationManager.notify(notificationId, builder.build())
            }
        }
    }
}

// private suspend fun blueskyComposeUseCase(
//    data: ComposeData.Bluesky,
//    context: Context,
//    notifyProgress: (current: Int, max: Int) -> Unit,
// ) {
//    val maxProgress = data.medias.size + 1
//    val service = data.account.getService()
//    val mediaBlob = data.medias.mapIndexedNotNull { index, uri ->
//        context.contentResolver.openInputStream(uri)?.use { stream ->
//            service.uploadBlob(stream.readBytes()).also {
//                notifyProgress(index + 1, maxProgress)
//            }.maybeResponse()
//        }
//    }.map {
//        it.blob
//    }
//    service.createRecord(
//        CreateRecordRequest(
//            repo = AtIdentifier(data.account.accountKey.id),
//            collection = Nsid("app.bsky.feed.post"),
//            record = buildJsonObject {
//                put("\$type", "app.bsky.feed.post")
//                put("createdAt", Clock.System.now().toString())
//                put("text", data.content)
//                if (data.quoteId != null) {
//                    val item =
//                        service.getPosts(GetPostsQueryParams(persistentListOf(AtUri(data.quoteId))))
//                            .maybeResponse()
//                            ?.posts
//                            ?.firstOrNull()
//                    if (item != null) {
//                        put(
//                            "embed",
//                            buildJsonObject {
//                                put("\$type", "app.bsky.embed.record")
//                                put(
//                                    "record",
//                                    buildJsonObject {
//                                        put("cid", item.cid.cid)
//                                        put("uri", item.uri.atUri)
//                                    },
//                                )
//                            },
//                        )
//                    }
//                }
//                if (data.inReplyToID != null) {
//                    val item =
//                        service.getPosts(GetPostsQueryParams(persistentListOf(AtUri(data.inReplyToID))))
//                            .maybeResponse()
//                            ?.posts
//                            ?.firstOrNull()
//                    if (item != null) {
//                        put(
//                            "reply",
//                            buildJsonObject {
//                                put(
//                                    "parent",
//                                    buildJsonObject {
//                                        put("cid", item.cid.cid)
//                                        put("uri", item.uri.atUri)
//                                    },
//                                )
//                                put(
//                                    "root",
//                                    buildJsonObject {
//                                        item.record.jsonObjectOrNull?.get("reply")?.jsonObjectOrNull?.get("root")
//                                            ?.jsonObjectOrNull?.let { root ->
//                                                put("cid", root["cid"]?.jsonPrimitive?.content)
//                                                put("uri", root["uri"]?.jsonPrimitive?.content)
//                                            } ?: run {
//                                            put("cid", item.cid.cid)
//                                            put("uri", item.uri.atUri)
//                                        }
//                                    },
//                                )
//                            },
//                        )
//                    }
//                }
//                if (mediaBlob.any()) {
//                    put(
//                        "embed",
//                        buildJsonObject {
//                            put("\$type", "app.bsky.embed.images")
//                            put(
//                                "images",
//                                buildJsonArray {
//                                    mediaBlob.forEach { blob ->
//                                        add(
//                                            buildJsonObject {
//                                                put("image", blob)
//                                                put("alt", "")
//                                            },
//                                        )
//                                    }
//                                },
//                            )
//                        },
//                    )
//                }
//                put(
//                    "langs",
//                    buildJsonArray {
//                        data.language.forEach { lang ->
//                            add(lang)
//                        }
//                    },
//                )
//            },
//        ),
//    )
// }
