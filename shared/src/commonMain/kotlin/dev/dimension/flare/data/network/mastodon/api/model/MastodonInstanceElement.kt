package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class MastodonInstanceElement(
    val domain: String,
    val version: String,
    val description: String,
    val languages: List<String>,
    val region: String,
    val categories: List<String>,
    @SerialName("proxied_thumbnail")
    val proxiedThumbnail: String,
    val blurhash: String? = null,
    @SerialName("total_users")
    val totalUsers: Long,
    @SerialName("last_week_users")
    val lastWeekUsers: Long,
    @SerialName("approval_required")
    val approvalRequired: Boolean,
    val language: String,
    val category: String,
)

@Serializable
internal data class InstanceData(
    val domain: String? = null,
    val title: String? = null,
    val version: String? = null,
    @SerialName("source_url")
    val sourceURL: String? = null,
    val description: String? = null,
    val usage: Usage? = null,
    val thumbnail: Thumbnail? = null,
    val languages: List<String>? = null,
    val configuration: Configuration? = null,
    val registrations: Registrations? = null,
    val contact: Contact? = null,
    val icon: List<Icon>? = null,
    @SerialName("api_versions")
    val apiVersions: APIVersions? = null,
    val rules: List<Rule>? = null,
)

@Serializable
internal data class Rule(
    val id: String? = null,
    val text: String? = null,
    val hint: String? = null,
)

@Serializable
internal data class Icon(
    val src: String? = null,
    val size: String? = null,
)

@Serializable
internal data class APIVersions(
    val mastodon: Long? = null,
)

@Serializable
internal data class Configuration(
    val urls: Urls? = null,
    val vapid: Vapid? = null,
    val accounts: Accounts? = null,
    val statuses: Statuses? = null,
    @SerialName("media_attachments")
    val mediaAttachments: MediaAttachments? = null,
    val polls: Polls? = null,
    val translation: Translation? = null,
)

@Serializable
internal data class MediaAttachments(
    @SerialName("description_limit")
    val descriptionLimit: Long? = null,
    @SerialName("image_matrix_limit")
    val imageMatrixLimit: Long? = null,
    @SerialName("image_size_limit")
    val imageSizeLimit: Long? = null,
    @SerialName("supported_mime_types")
    val supportedMIMETypes: List<String>? = null,
    @SerialName("video_frame_rate_limit")
    val videoFrameRateLimit: Long? = null,
    @SerialName("video_matrix_limit")
    val videoMatrixLimit: Long? = null,
    @SerialName("video_size_limit")
    val videoSizeLimit: Long? = null,
)

@Serializable
internal data class Polls(
    @SerialName("max_options")
    val maxOptions: Long? = null,
    @SerialName("max_characters_per_option")
    val maxCharactersPerOption: Long? = null,
    @SerialName("min_expiration")
    val minExpiration: Long? = null,
    @SerialName("max_expiration")
    val maxExpiration: Long? = null,
)

@Serializable
internal data class Vapid(
    @SerialName("public_key")
    val publicKey: String? = null,
)

@Serializable
internal data class Accounts(
    @SerialName("max_featured_tags")
    val maxFeaturedTags: Long? = null,
    @SerialName("max_pinned_statuses")
    val maxPinnedStatuses: Long? = null,
)

@Serializable
internal data class Thumbnail(
    val url: String? = null,
    val blurhash: String? = null,
    val versions: Map<String, String>? = null,
)

@Serializable
internal data class Users(
    @SerialName("active_month")
    val activeMonth: Long? = null,
)

@Serializable
internal data class Translation(
    val enabled: Boolean? = null,
)

@Serializable
internal data class Urls(
    val streaming: String? = null,
    val status: String? = null,
    val about: String? = null,
    @SerialName("privacy_policy")
    val privacyPolicy: String? = null,
    @SerialName("terms_of_service")
    val termsOfService: String? = null,
)

@Serializable
internal data class Statuses(
    @SerialName("max_characters")
    val maxCharacters: Long? = null,
    @SerialName("max_media_attachments")
    val maxMediaAttachments: Long? = null,
    @SerialName("characters_reserved_per_url")
    val charactersReservedPerURL: Long? = null,
)

@Serializable
internal data class Contact(
    val email: String? = null,
    val account: Account? = null,
)

@Serializable
internal data class Registrations(
    val enabled: Boolean? = null,
    @SerialName("approval_required")
    val approvalRequired: Boolean? = null,
    @SerialName("reason_required")
    val reasonRequired: Boolean? = null,
    val message: String? = null,
    @SerialName("min_age")
    val minAge: String? = null,
    val url: String? = null,
)

@Serializable
internal data class Usage(
    val users: Users? = null,
)
