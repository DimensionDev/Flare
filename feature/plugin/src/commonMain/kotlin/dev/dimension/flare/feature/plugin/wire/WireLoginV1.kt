package dev.dimension.flare.feature.plugin.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
public data class DetectorRequestV1(
    val origin: String,
)

@Serializable
public enum class DetectorMatchV1 {
    @SerialName("none")
    None,

    @SerialName("compatible")
    Compatible,

    @SerialName("exact")
    Exact,
}

@Serializable
public data class DetectorResultV1(
    val match: DetectorMatchV1,
    val canonicalOrigin: String,
    val software: String? = null,
    val instance: InstanceMetadataV1? = null,
) {
    public val compatibleMode: Boolean
        get() = match == DetectorMatchV1.Compatible
}

@Serializable
public data class InstanceMetadataV1(
    val domain: String,
    val title: String = domain,
    val description: String? = null,
    val iconUrl: String? = null,
    val bannerUrl: String? = null,
    val usersCount: Long? = null,
    val registrationEnabled: Boolean? = null,
)

@Serializable
public data class LoginBeginRequestV1(
    val methodId: String,
    val origin: String,
    val flowId: String,
    val state: String? = null,
    val redirectUri: String? = null,
    val values: Map<String, String> = emptyMap(),
)

@Serializable
public data class LoginResumeRequestV1(
    val methodId: String,
    val origin: String,
    val flowId: String,
    val redirectUri: String? = null,
    val callbackParameters: Map<String, String> = emptyMap(),
    val pendingPayload: JsonElement? = null,
)

@Serializable
public data class CookieSnapshotV1(
    val cookies: List<CookieValueV1>,
)

@Serializable
public data class CookieValueV1(
    val sourceUrl: String,
    val name: String,
    val value: String,
)

@Serializable
public data class CookieCheckRequestV1(
    val methodId: String,
    val origin: String,
    val flowId: String,
    val cookies: CookieSnapshotV1,
)

@Serializable
public sealed interface LoginTransitionV1 {
    @Serializable
    @SerialName("pending")
    public data object Pending : LoginTransitionV1

    @Serializable
    @SerialName("externalBrowser")
    public data class ExternalBrowser(
        val url: String,
        val pendingPayload: JsonElement,
    ) : LoginTransitionV1

    @Serializable
    @SerialName("webCookie")
    public data class WebCookie(
        val startUrl: String,
    ) : LoginTransitionV1

    @Serializable
    @SerialName("success")
    public data class Success(
        val value: LoginSuccessV1,
    ) : LoginTransitionV1
}

@Serializable
public data class LoginSuccessV1(
    val accountId: String,
    val origin: String,
    val credential: JsonElement,
    val profile: ProfileV1,
    val capabilities: Map<String, Set<String>>,
    val composeConfig: ComposeConfigV1? = null,
)

@Serializable
public data class AccountPluginSnapshotV1(
    val pluginId: String,
    val platformId: String,
    val packageHash: String,
    val origin: String,
    val accountId: String,
    val manifestCapabilities: Map<String, Set<String>>,
    val negotiatedCapabilities: Map<String, Set<String>>,
    val composeConfig: ComposeConfigV1? = null,
    val credentialSchemaVersion: Int,
    val timelineSchemaVersion: Int,
)
