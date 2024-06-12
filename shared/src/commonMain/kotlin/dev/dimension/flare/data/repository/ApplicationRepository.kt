package dev.dimension.flare.data.repository

import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiApplication
import dev.dimension.flare.ui.model.UiApplication.Companion.toUi

class ApplicationRepository(
    private val database: AppDatabase,
) {
    fun findByHost(host: String): UiApplication? =
        database.dbApplicationQueries
            .get(host)
            .executeAsOneOrNull()
            ?.toUi()

    fun addApplication(
        host: String,
        credentialJson: String,
        platformType: PlatformType,
    ) {
        database.dbApplicationQueries.insert(host, credentialJson, platformType)
    }

    fun setPendingOAuth(
        host: String,
        pendingOAuth: Boolean,
    ) {
        database.dbApplicationQueries.updatePending(if (pendingOAuth) 1L else 0L, host)
    }

    fun getPendingOAuth(): UiApplication? =
        database.dbApplicationQueries
            .getPending()
            .executeAsOneOrNull()
            ?.toUi()

    fun clearPendingOAuth() {
        database.dbApplicationQueries.clearPending()
    }
}
