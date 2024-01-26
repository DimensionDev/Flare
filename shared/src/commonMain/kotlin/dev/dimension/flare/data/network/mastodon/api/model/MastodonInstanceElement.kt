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
