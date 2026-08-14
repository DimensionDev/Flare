package dev.dimension.flare.feature.plugin.manifest

import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.wire.ComposeConfigV1
import dev.dimension.flare.feature.plugin.wire.HostIconV1
import dev.dimension.flare.feature.plugin.wire.PageDirectionV1
import dev.dimension.flare.feature.plugin.wire.TimelineDisplayV1
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public data class PluginManifestV1(
    val schemaVersion: Int = PluginAbiV1.MANIFEST_SCHEMA_VERSION,
    val apiVersion: Int,
    val id: String,
    val version: String,
    val defaultLocale: String = "en",
    val name: PluginTextV1,
    val description: PluginTextV1? = null,
    val permissions: PluginPermissionsV1 = PluginPermissionsV1(),
    val platform: PluginPlatformManifestV1,
)

@Serializable
public data class PluginPermissionsV1(
    val authOrigins: Set<String> = emptySet(),
)

@Serializable
public data class PluginPlatformManifestV1(
    val id: String,
    val name: PluginTextV1,
    val description: PluginTextV1? = null,
    val detector: DetectorManifestV1? = null,
    val loginMethods: List<LoginMethodManifestV1> = emptyList(),
    val capabilities: Map<String, CapabilityManifestV1> = emptyMap(),
    val timelines: List<TimelineManifestV1> = emptyList(),
    val profileTabs: List<ProfileTabManifestV1> = emptyList(),
    val deepLinks: List<DeepLinkManifestV1> = emptyList(),
    val composeDefaults: ComposeConfigV1? = null,
    val guest: GuestManifestV1? = null,
    val credentialSchemaVersion: Int = 1,
    val timelineSchemaVersion: Int = 1,
)

@Serializable
public data class DetectorManifestV1(
    val priority: Int = 0,
)

@Serializable
public data class GuestManifestV1(
    val enabled: Boolean = true,
)

@Serializable
public data class CapabilityManifestV1(
    val operations: Map<String, CapabilityOperationManifestV1>,
    val notificationFilters: Set<NotificationFilterKindV1> = emptySet(),
    val relationActions: Set<RelationActionKindV1> = emptySet(),
)

@Serializable
public enum class NotificationFilterKindV1 {
    @SerialName("all")
    All,

    @SerialName("mention")
    Mention,

    @SerialName("comment")
    Comment,

    @SerialName("like")
    Like,
}

@Serializable
public enum class RelationActionKindV1 {
    @SerialName("follow")
    Follow,

    @SerialName("block")
    Block,

    @SerialName("mute")
    Mute,
}

@Serializable
public data class CapabilityOperationManifestV1(
    val directions: Set<PageDirectionV1> = emptySet(),
)

@Serializable
public data class TimelineManifestV1(
    val id: String,
    val title: PluginTextV1,
    val icon: HostIconV1 = HostIconV1.Home,
    val display: TimelineDisplayV1 = TimelineDisplayV1.List,
    val parameters: Map<String, String> = emptyMap(),
    val defaultForNewAccount: Boolean = false,
)

@Serializable
public data class ProfileTabManifestV1(
    val id: String,
    val title: PluginTextV1,
    val icon: HostIconV1 = HostIconV1.Profile,
    val display: TimelineDisplayV1 = TimelineDisplayV1.List,
    val parameters: Map<String, String> = emptyMap(),
)

@Serializable
public enum class LoginInteractionV1 {
    OAuth,
    Password,
    CredentialImport,
    WebCookie,
    Form,
}

@Serializable
public data class LoginMethodManifestV1(
    val id: String,
    val interaction: LoginInteractionV1,
    val title: PluginTextV1,
    val description: PluginTextV1? = null,
    val fields: List<LoginFieldManifestV1> = emptyList(),
    val cookie: WebCookieManifestV1? = null,
)

@Serializable
public enum class LoginFieldTypeV1 {
    Text,
    Username,
    Password,
    Otp,
    Secret,
}

@Serializable
public data class LoginFieldManifestV1(
    val id: String,
    val type: LoginFieldTypeV1,
    val label: PluginTextV1,
    val placeholder: PluginTextV1? = null,
    val required: Boolean = true,
)

@Serializable
public data class WebCookieManifestV1(
    val startUrl: String,
    val probes: List<CookieProbeManifestV1>,
)

@Serializable
public data class CookieProbeManifestV1(
    val url: String,
    val cookies: List<CookieRequirementManifestV1>,
)

@Serializable
public data class CookieRequirementManifestV1(
    val name: String,
    val required: Boolean = true,
)

@Serializable
public data class DeepLinkManifestV1(
    val origin: String = PluginAbiV1.ACCOUNT_ORIGIN,
    val path: List<DeepLinkPathSegmentV1>,
    val target: DeepLinkTargetV1,
)

@Serializable
public sealed interface DeepLinkPathSegmentV1 {
    @Serializable
    @SerialName("literal")
    public data class Literal(
        val value: String,
    ) : DeepLinkPathSegmentV1

    @Serializable
    @SerialName("capture")
    public data class Capture(
        val name: String,
    ) : DeepLinkPathSegmentV1
}

@Serializable
public enum class DeepLinkTargetTypeV1 {
    Profile,
    Post,
    Timeline,
    Browser,
}

@Serializable
public data class DeepLinkTargetV1(
    val type: DeepLinkTargetTypeV1,
    val value: String? = null,
)

@Serializable
public data class PluginMethodTableV1(
    val apiVersion: Int,
    val methods: Set<String>,
)
