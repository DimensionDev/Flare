package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.datasource.pleroma.PleromaDataSource
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.data.datasource.xqt.XQTDataSource
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.christian.ozone.oauth.OAuthToken

@Immutable
public sealed class UiAccount {
    public abstract val accountKey: MicroBlogKey
    public abstract val platformType: PlatformType
    internal abstract val credential: Credential
    internal abstract val dataSource: AuthenticatedMicroblogDataSource

    @Immutable
    @Serializable
    internal sealed interface Credential

    @Immutable
    internal data class Mastodon(
        override val credential: Credential,
        override val accountKey: MicroBlogKey,
    ) : UiAccount() {
        override val platformType: PlatformType
            get() = PlatformType.Mastodon

        @Immutable
        @Serializable
        @SerialName("MastodonCredential")
        data class Credential(
            val instance: String,
            val accessToken: String,
            val forkType: ForkType = ForkType.Mastodon,
        ) : UiAccount.Credential {
            enum class ForkType {
                Mastodon,
                Pleroma,
            }
        }

        override val dataSource by lazy {
            when (credential.forkType) {
                Credential.ForkType.Mastodon ->
                    MastodonDataSource(
                        accountKey = accountKey,
                        instance = credential.instance,
                        accessToken = credential.accessToken,
                    )
                Credential.ForkType.Pleroma ->
                    PleromaDataSource(
                        accountKey = accountKey,
                        instance = credential.instance,
                        accessToken = credential.accessToken,
                    )
            }
        }
    }

    @Immutable
    internal data class Misskey(
        override val credential: Credential,
        override val accountKey: MicroBlogKey,
    ) : UiAccount() {
        override val platformType: PlatformType
            get() = PlatformType.Misskey

        @Immutable
        @Serializable
        @SerialName("MisskeyCredential")
        data class Credential(
            val host: String,
            val accessToken: String,
        ) : UiAccount.Credential

        override val dataSource by lazy {
            MisskeyDataSource(accountKey = accountKey, credential = credential)
        }
    }

    @Immutable
    internal data class Bluesky(
        override val credential: Credential,
        override val accountKey: MicroBlogKey,
    ) : UiAccount() {
        override val platformType: PlatformType
            get() = PlatformType.Bluesky

        @Serializable
        sealed interface Credential : UiAccount.Credential {
            val baseUrl: String
            val accessToken: String
            val refreshToken: String

            @Immutable
            @Serializable
            @SerialName("BlueskyCredential")
            data class BlueskyCredential(
                override val baseUrl: String,
                override val accessToken: String,
                override val refreshToken: String,
            ) : Bluesky.Credential

            @Immutable
            @Serializable
            @SerialName("BlueskyOAuthCredential")
            data class OAuthCredential(
                override val baseUrl: String,
                val oAuthToken: OAuthToken,
            ) : Bluesky.Credential {
                override val accessToken: String
                    get() = oAuthToken.accessToken

                override val refreshToken: String
                    get() = oAuthToken.refreshToken
            }
        }

        override val dataSource by lazy {
            BlueskyDataSource(accountKey = accountKey, credential = credential)
        }
    }

    @Immutable
    internal data class XQT(
        override val accountKey: MicroBlogKey,
        override val credential: Credential,
    ) : UiAccount() {
        override val platformType: PlatformType
            get() = PlatformType.xQt

        @Immutable
        @Serializable
        @SerialName("XQTCredential")
        data class Credential(
            val chocolate: String,
        ) : UiAccount.Credential

        override val dataSource by lazy {
            XQTDataSource(accountKey = accountKey, credential = credential)
        }
    }

    @Immutable
    internal data class VVo(
        override val accountKey: MicroBlogKey,
        override val credential: Credential,
    ) : UiAccount() {
        override val platformType: PlatformType
            get() = PlatformType.VVo

        @Immutable
        @Serializable
        @SerialName("VVoCredential")
        data class Credential(
            val chocolate: String,
        ) : UiAccount.Credential

        override val dataSource by lazy {
            VVODataSource(accountKey = accountKey, credential = credential)
        }
    }

    internal companion object {
        fun DbAccount.toUi(): UiAccount =
            when (platform_type) {
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

                PlatformType.xQt -> {
                    val credential = credential_json.decodeJson<XQT.Credential>()
                    XQT(
                        credential = credential,
                        accountKey = account_key,
                    )
                }

                PlatformType.VVo -> {
                    val credential = credential_json.decodeJson<VVo.Credential>()
                    VVo(
                        credential = credential,
                        accountKey = account_key,
                    )
                }
            }
    }
}
