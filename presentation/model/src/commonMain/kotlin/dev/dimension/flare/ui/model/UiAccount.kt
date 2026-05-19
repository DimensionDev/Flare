package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.christian.ozone.oauth.OAuthToken

@Immutable
@Serializable
public sealed interface NostrSignerCredential {
    public val stableId: String

    @Immutable
    @Serializable
    @SerialName("NostrSignerLocalKey")
    public data class LocalKey(
        public val nsec: String,
    ) : NostrSignerCredential {
        public override val stableId: String
            get() = "local:$nsec"
    }

    @Immutable
    @Serializable
    @SerialName("NostrSignerBunker")
    public data class Bunker(
        public val uri: String,
        public val userPubkeyHex: String? = null,
        public val signerRelay: String? = null,
        public val secret: String? = null,
    ) : NostrSignerCredential {
        public override val stableId: String
            get() = "bunker:$uri"
    }

    @Immutable
    @Serializable
    @SerialName("NostrSignerAmber")
    public data class Amber(
        public val userPubkeyHex: String,
        public val packageName: String? = null,
        public val approvedSignerPubkey: String? = null,
    ) : NostrSignerCredential {
        public override val stableId: String
            get() = "amber:$userPubkeyHex:${packageName.orEmpty()}:${approvedSignerPubkey.orEmpty()}"
    }
}

@Immutable
public sealed class UiAccount {
    public abstract val accountKey: MicroBlogKey
    public abstract val platformType: PlatformType

    @Immutable
    @Serializable
    public sealed interface Credential

    @Immutable
    public data class Nostr(
        public override val accountKey: MicroBlogKey,
    ) : UiAccount() {
        public override val platformType: PlatformType
            get() = PlatformType.Nostr

        @Immutable
        @Serializable
        @SerialName("NostrCredential")
        public data class Credential(
            public val pubkeyHex: String = "",
            public val relays: List<String> = emptyList(),
            public val mediaServerUrl: String = "https://blossom.nostr.build/",
            public val signer: NostrSignerCredential? = null,
            @SerialName("nsec")
            internal val legacyNsec: String? = null,
        ) : UiAccount.Credential
    }

    @Immutable
    public data class Mastodon(
        public override val accountKey: MicroBlogKey,
        public val forkType: Credential.ForkType = Credential.ForkType.Mastodon,
        public val instance: String,
        public val nodeType: String? = null,
    ) : UiAccount() {
        public override val platformType: PlatformType
            get() = PlatformType.Mastodon

        @Immutable
        @Serializable
        @SerialName("MastodonCredential")
        public data class Credential(
            public val instance: String,
            public val accessToken: String,
            public val forkType: ForkType = ForkType.Mastodon,
            // to support more forks in the future
            public val nodeType: String? = null,
        ) : UiAccount.Credential {
            public enum class ForkType {
                Mastodon,
                Pleroma,
            }
        }
    }

    @Immutable
    public data class Misskey(
        public override val accountKey: MicroBlogKey,
        public val host: String,
        // to support more forks in the future
        public val nodeType: String? = null,
    ) : UiAccount() {
        public override val platformType: PlatformType
            get() = PlatformType.Misskey

        @Immutable
        @Serializable
        @SerialName("MisskeyCredential")
        public data class Credential(
            public val host: String,
            public val accessToken: String,
            public val nodeType: String? = null,
        ) : UiAccount.Credential
    }

    @Immutable
    public data class Bluesky(
        public override val accountKey: MicroBlogKey,
    ) : UiAccount() {
        public override val platformType: PlatformType
            get() = PlatformType.Bluesky

        @Serializable
        public sealed interface Credential : UiAccount.Credential {
            public val baseUrl: String
            public val accessToken: String
            public val refreshToken: String

            @Immutable
            @Serializable
            @SerialName("BlueskyCredential")
            public data class BlueskyCredential(
                public override val baseUrl: String,
                public override val accessToken: String,
                public override val refreshToken: String,
            ) : Bluesky.Credential

            @Immutable
            @Serializable
            @SerialName("BlueskyOAuthCredential")
            public data class OAuthCredential(
                public override val baseUrl: String,
                public val oAuthToken: OAuthToken,
            ) : Bluesky.Credential {
                public override val accessToken: String
                    get() = oAuthToken.accessToken

                public override val refreshToken: String
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
    public data class XQT(
        public override val accountKey: MicroBlogKey,
    ) : UiAccount() {
        public override val platformType: PlatformType
            get() = PlatformType.xQt

        @Immutable
        @Serializable
        @SerialName("XQTCredential")
        public data class Credential(
            public val chocolate: String,
        ) : UiAccount.Credential
    }

    @Immutable
    public data class VVo(
        public override val accountKey: MicroBlogKey,
    ) : UiAccount() {
        public override val platformType: PlatformType
            get() = PlatformType.VVo

        @Immutable
        @Serializable
        @SerialName("VVoCredential")
        public data class Credential(
            public val chocolate: String,
        ) : UiAccount.Credential
    }
}

public val UiAccount.Nostr.Credential.effectiveSigner: NostrSignerCredential?
    get() = signer ?: legacyNsec?.let(NostrSignerCredential::LocalKey)

public fun UiAccount.Nostr.Credential.effectivePubkeyHex(accountKey: MicroBlogKey): String = pubkeyHex.ifBlank { accountKey.id }

public fun UiAccount.Nostr.Credential.normalized(accountKey: MicroBlogKey): UiAccount.Nostr.Credential =
    copy(
        pubkeyHex = effectivePubkeyHex(accountKey),
        signer = effectiveSigner,
    )

public fun UiAccount.Nostr.Credential.signerStableId(accountKey: MicroBlogKey): String =
    effectiveSigner?.stableId ?: "readonly:${effectivePubkeyHex(accountKey)}"
