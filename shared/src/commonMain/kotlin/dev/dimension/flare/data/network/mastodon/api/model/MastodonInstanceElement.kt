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
)

@Serializable
internal data class Configuration(
    val urls: Urls? = null,
    val accounts: Accounts? = null,
    val statuses: Statuses? = null,
    @SerialName("media_attachments")
    val translation: Translation? = null,
)

@Serializable
internal data class Accounts(
    @SerialName("max_featured_tags")
    val maxFeaturedTags: Long? = null,
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
)

@Serializable
internal data class Usage(
    val users: Users? = null,
)
