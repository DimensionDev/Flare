package dev.dimension.flare.data.datasource.microblog.loader

public interface NotificationLoader {
    public suspend fun notificationBadgeCount(): Int
}
