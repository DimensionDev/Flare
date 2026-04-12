package dev.dimension.flare.data.datasource.microblog.loader

internal interface NotificationLoader {
    suspend fun notificationBadgeCount(): Int
}
