package dev.dimension.flare.data.repository

import com.moriatsushi.koject.inject
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.app.AppDatabase
import dev.dimension.flare.data.database.app.model.DbApplication
import dev.dimension.flare.data.network.mastodon.api.model.CreateApplicationResponse
import dev.dimension.flare.data.repository.UiApplication.Companion.toUi
import dev.dimension.flare.model.PlatformType

suspend fun findApplicationUseCase(
    host: String,
    appDatabase: AppDatabase = inject(),
): UiApplication? {
    return appDatabase.applicationDao().getApplication(host)?.toUi()
}

suspend fun addMastodonApplicationUseCase(
    host: String,
    application: CreateApplicationResponse,
    appDatabase: AppDatabase = inject(),
) {
    appDatabase.applicationDao().addApplication(
        DbApplication(
            host = host,
            credential_json = application.encodeJson(),
            platform_type = PlatformType.Mastodon,
            hasPendingOAuth = false,
        )
    )
}

suspend fun setPendingOAuthUseCase(
    host: String,
    pendingOAuth: Boolean,
    appDatabase: AppDatabase = inject(),
) {
    val application = appDatabase.applicationDao().getApplication(host)
    if (application != null) {
        appDatabase.applicationDao().updateApplication(
            application.copy(
                hasPendingOAuth = pendingOAuth,
            )
        )
    }
}

suspend fun getPendingOAuthUseCase(
    appDatabase: AppDatabase = inject(),
): List<UiApplication> {
    return appDatabase.applicationDao().getPendingOAuthApplication().map { it.toUi() }
}

sealed interface UiApplication {
    val host: String

    data class Mastodon(
        override val host: String,
        val application: CreateApplicationResponse,
    ) : UiApplication

    companion object {
        fun DbApplication.toUi(): UiApplication {
            return when (platform_type) {
                PlatformType.Mastodon -> Mastodon(
                    host = host,
                    application = credential_json.decodeJson(),
                )

                PlatformType.Misskey -> TODO()
            }
        }
    }
}