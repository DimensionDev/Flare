package dev.dimension.flare.ui.model

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.database.app.DbAccount
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable



sealed interface UiAccount {
    val accountKey: MicroBlogKey
    val platformType: PlatformType
    val credential: Credential

    @Serializable
    sealed interface Credential

    data class Mastodon(
        override val credential: Credential,
        override val accountKey: MicroBlogKey,
    ) : UiAccount {
        override val platformType: PlatformType
            get() = PlatformType.Mastodon
        @Serializable
        @SerialName("MastodonCredential")
        data class Credential(
            val instance: String,
            val accessToken: String,
        ): UiAccount.Credential

        val dataSource by lazy {
            MastodonDataSource(this)
        }
    }

    data class Misskey(
        override val credential: Credential,
        override val accountKey: MicroBlogKey,
    ) : UiAccount {
        override val platformType: PlatformType
            get() = PlatformType.Misskey
        @Serializable
        @SerialName("MisskeyCredential")
        data class Credential(
            val host: String,
            val accessToken: String,
        ): UiAccount.Credential

        val dataSource by lazy {
            MisskeyDataSource(this)
        }
    }

    data class Bluesky(
        override val credential: Credential,
        override val accountKey: MicroBlogKey,
    ) : UiAccount {
        override val platformType: PlatformType
            get() = PlatformType.Bluesky
        @Serializable
        @SerialName("BlueskyCredential")
        data class Credential(
            val baseUrl: String,
            val accessToken: String,
            val refreshToken: String,
        ): UiAccount.Credential

        val dataSource by lazy {
            BlueskyDataSource(this)
        }
    }

    companion object {
        fun DbAccount.toUi(): UiAccount = when (platform_type) {
            PlatformType.Mastodon -> {
                val credential = credential_json.decodeJson<Mastodon.Credential>()
                Mastodon(
                    credential = credential,
                    accountKey = account_key,
                )
            }

            PlatformType.Misskey -> {
                val credential = credential_json.decodeJson<Misskey.Credential>()
                Misskey(
                    credential = credential,
                    accountKey = account_key,
                )
            }

            PlatformType.Bluesky -> {
                val credential = credential_json.decodeJson<Bluesky.Credential>()
                Bluesky(
                    credential = credential,
                    accountKey = account_key,
                )
            }
        }
    }
}
