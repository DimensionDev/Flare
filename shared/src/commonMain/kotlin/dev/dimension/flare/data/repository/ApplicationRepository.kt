package dev.dimension.flare.data.repository

import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbApplication
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiApplication
import dev.dimension.flare.ui.model.UiApplication.Companion.toUi
import kotlinx.coroutines.flow.firstOrNull

class ApplicationRepository(
    private val database: AppDatabase,
) {
    suspend fun findByHost(host: String): UiApplication? =
        database
            .applicationDao()
            .get(host)
            .firstOrNull()
            ?.toUi()

    suspend fun addApplication(
        host: String,
        credentialJson: String,
        platformType: PlatformType,
    ) {
        database.applicationDao().insert(
            DbApplication(
                host = host,
                credential_json = credentialJson,
                platform_type = platformType,
            ),
        )
    }

    suspend fun setPendingOAuth(
        host: String,
        pendingOAuth: Boolean,
    ) {
        database.applicationDao().updatePending(host, if (pendingOAuth) 1L else 0L)
    }

    suspend fun getPendingOAuth(): UiApplication? =
        database
            .applicationDao()
            .getPending()
            .firstOrNull()
            ?.firstOrNull()
            ?.toUi()

    suspend fun clearPendingOAuth() {
        database.applicationDao().clearPending()
    }
}
