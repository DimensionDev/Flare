package dev.dimension.flare.data.datasource.microblog.datasource

import dev.dimension.flare.data.datasource.microblog.handler.NotificationHandler
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface NotificationDataSource {
    public val notificationHandler: NotificationHandler
}
