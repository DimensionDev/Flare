package dev.dimension.flare.data.datasource.microblog.datasource

import dev.dimension.flare.data.datasource.microblog.handler.NotificationHandler

internal interface NotificationDataSource {
    val notificationHandler: NotificationHandler
}
