package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.database.app.model.DbAccount
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.datasource.mastodon.MastodonDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.datasource.nostr.NostrDataSource
import dev.dimension.flare.data.datasource.pleroma.PleromaDataSource
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.data.datasource.xqt.XQTDataSource
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.christian.ozone.oauth.OAuthToken

@Immutable
@Serializable
internal sealed interface NostrSignerCredential {
    val stableId: String

    @Immutable
    @Serializable
    @SerialName("NostrSignerLocalKey")
    data class LocalKey(
        val nsec: String,
    ) : NostrSignerCredential {
        override val stableId: String
            get() = "local:$nsec"
    }

    @Immutable
    @Serializable
    @SerialName("NostrSignerBunker")
    data class Bunker(
        val uri: String,
        val userPubkeyHex: String? = null,
        val signerRelay: String? = null,
        val secret: String? = null,
    ) : NostrSignerCredential {
        override val stableId: String
            get() = "bunker:$uri"
    }

    @Immutable
    @Serializable
    @SerialName("NostrSignerAmber")
    data class Amber(
        val userPubkeyHex: String,
        val packageName: String? = null,
        val approvedSignerPubkey: String? = null,
    ) : NostrSignerCredential {
        override val stableId: String
            get() = "amber:$userPubkeyHex:${packageName.orEmpty()}:${approvedSignerPubkey.orEmpty()}"
    }
}

@Immutable
public sealed class UiAccount {
    public abstract val accountKey: MicroBlogKey
    public abstract val platformType: PlatformType

    @Immutable
    @Serializable
    internal sealed interface Credential

    @Immutable
    internal data class Nostr(
        override val accountKey: MicroBlogKey,
    ) : UiAccount() {
        override val platformType: PlatformType
            get() = PlatformType.Nostr

        @Immutable
        @Serializable
        @SerialName("NostrCredential")
        data class Credential(
            val pubkeyHex: String = "",
            val relays: List<String> = emptyList(),
            val mediaServerUrl: String = "https://blossom.nostr.build/",
            val signer: NostrSignerCredential? = null,
            @SerialName("nsec")
            internal val legacyNsec: String? = null,
        ) : UiAccount.Credential
    }

    @Immutable
    internal data class Mastodon(
        override val accountKey: MicroBlogKey,
        internal val forkType: Credential.ForkType = Credential.ForkType.Mastodon,
        internal val instance: String,
        val nodeType: String? = null,
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
            // to support more forks in the future
            val nodeType: String? = null,
        ) : UiAccount.Credential {
            enum class ForkType {
                Mastodon,
                Pleroma,
            }
        }
    }

    @Immutable
    internal data class Misskey(
        override val accountKey: MicroBlogKey,
        internal val host: String,
        // to support more forks in the future
        val nodeType: String? = null,
    ) : UiAccount() {
        override val platformType: PlatformType
            get() = PlatformType.Misskey

        @Immutable
        @Serializable
        @SerialName("MisskeyCredential")
        data class Credential(
            val host: String,
            val accessToken: String,
            val nodeType: String? = null,
        ) : UiAccount.Credential
    }

    @Immutable
    internal data class Bluesky(
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

                override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (other !is OAuthCredential) return false

                    if (baseUrl != other.baseUrl) return false
                    if (oAuthToken.accessToken != other.oAuthToken.accessToken) return false
                    if (oAuthToken.refreshToken != other.oAuthToken.refreshToken) return false
                    if (oAuthToken.nonce != other.oAuthToken.nonce) return false
                    if (oAuthToken.expiresIn != other.oAuthToken.expiresIn) return false

                    return true
                }

                override fun hashCode(): Int {
                    var result = baseUrl.hashCode()
                    result = 31 * result + oAuthToken.hashCode()
                    return result
                }
            }
        }
    }

    @Immutable
    internal data class XQT(
        override val accountKey: MicroBlogKey,
    ) : UiAccount() {
        override val platformType: PlatformType
            get() = PlatformType.xQt

        @Immutable
        @Serializable
        @SerialName("XQTCredential")
        data class Credential(
            val chocolate: String,
        ) : UiAccount.Credential
    }

    @Immutable
    internal data class VVo(
        override val accountKey: MicroBlogKey,
    ) : UiAccount() {
        override val platformType: PlatformType
            get() = PlatformType.VVo

        @Immutable
        @Serializable
        @SerialName("VVoCredential")
        data class Credential(
            val chocolate: String,
        ) : UiAccount.Credential
    }

    internal companion object {
        fun UiAccount.createDataSource(): MicroblogDataSource =
            when (this) {
                is Nostr -> {
                    NostrDataSource(
                        accountKey = accountKey,
                    )
                }

                is Mastodon -> {
                    when (forkType) {
                        Mastodon.Credential.ForkType.Mastodon -> {
                            MastodonDataSource(
                                accountKey = accountKey,
                                instance = instance,
                            )
                        }

                        Mastodon.Credential.ForkType.Pleroma -> {
                            PleromaDataSource(
                                accountKey = accountKey,
                                instance = instance,
                            )
                        }
                    }
                }

                is Misskey -> {
                    MisskeyDataSource(
                        accountKey = accountKey,
                        host = host,
                    )
                }

                is Bluesky -> {
                    BlueskyDataSource(
                        accountKey = accountKey,
                    )
                }

                is XQT -> {
                    XQTDataSource(
                        accountKey = accountKey,
                    )
                }

                is VVo -> {
                    VVODataSource(
                        accountKey = accountKey,
                    )
                }
            }

        fun DbAccount.toUi(): UiAccount =
            when (platform_type) {
                PlatformType.Nostr -> {
                    Nostr(
                        accountKey = account_key,
                    )
                }

                PlatformType.Mastodon -> {
                    val credential = credential_json.decodeJson<Mastodon.Credential>()
                    Mastodon(
                        accountKey = account_key,
                        forkType = credential.forkType,
                        instance = credential.instance,
                        nodeType = credential.nodeType,
                    )
                }

                PlatformType.Misskey -> {
                    val credential = credential_json.decodeJson<Misskey.Credential>()
                    Misskey(
                        accountKey = account_key,
                        host = credential.host,
                        nodeType = credential.nodeType,
                    )
                }

                PlatformType.Bluesky -> {
                    Bluesky(
                        accountKey = account_key,
                    )
                }

                PlatformType.xQt -> {
                    XQT(
                        accountKey = account_key,
                    )
                }

                PlatformType.VVo -> {
                    VVo(
                        accountKey = account_key,
                    )
                }
            }
    }
}

internal val UiAccount.Nostr.Credential.effectiveSigner: NostrSignerCredential?
    get() = signer ?: legacyNsec?.let(NostrSignerCredential::LocalKey)

internal fun UiAccount.Nostr.Credential.effectivePubkeyHex(accountKey: MicroBlogKey): String = pubkeyHex.ifBlank { accountKey.id }

internal fun UiAccount.Nostr.Credential.normalized(accountKey: MicroBlogKey): UiAccount.Nostr.Credential =
    copy(
        pubkeyHex = effectivePubkeyHex(accountKey),
        signer = effectiveSigner,
    )

internal fun UiAccount.Nostr.Credential.signerStableId(accountKey: MicroBlogKey): String =
    effectiveSigner?.stableId ?: "readonly:${effectivePubkeyHex(accountKey)}"
