package dev.dimension.flare.data.datasource.microblog.loader
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public interface NotificationLoader {
    public suspend fun notificationBadgeCount(): Int
}
