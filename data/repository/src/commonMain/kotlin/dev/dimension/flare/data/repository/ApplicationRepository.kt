package dev.dimension.flare.data.repository

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbApplication
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.MastodonApplicationCredential
import dev.dimension.flare.ui.model.UiApplication
import kotlinx.coroutines.flow.firstOrNull

public class ApplicationRepository(
    private val database: AppDatabase,
) {
    public suspend fun findByHost(host: String): UiApplication? =
        database
            .applicationDao()
            .get(host)
            .firstOrNull()
            ?.toUi()

    public suspend fun addApplication(
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

    public suspend fun setPendingOAuth(
        host: String,
        pendingOAuth: Boolean,
    ) {
        database.applicationDao().updatePending(host, if (pendingOAuth) 1L else 0L)
    }

    public suspend fun getPendingOAuth(): UiApplication? =
        database
            .applicationDao()
            .getPending()
            .firstOrNull()
            ?.firstOrNull()
            ?.toUi()

    public suspend fun clearPendingOAuth() {
        database.applicationDao().clearPending()
    }

    private fun DbApplication.toUi(): UiApplication =
        when (platform_type) {
            PlatformType.Nostr -> {
                UiApplication.Nostr(
                    host = host,
                )
            }

            PlatformType.Mastodon -> {
                UiApplication.Mastodon(
                    host = host,
                    application = credential_json.decodeJson<MastodonApplicationCredential>(),
                )
            }

            PlatformType.Misskey -> {
                UiApplication.Misskey(
                    host = host,
                    session = credential_json,
                )
            }

            PlatformType.Bluesky -> {
                UiApplication.Bluesky(
                    host = host,
                )
            }

            PlatformType.xQt -> {
                UiApplication.XQT
            }

            PlatformType.VVo -> {
                UiApplication.VVo
            }
        }
}
