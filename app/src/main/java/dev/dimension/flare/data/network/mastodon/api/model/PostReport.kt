package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PostReport(

    @SerialName("account_id")
    val accountId: String,

    @SerialName("status_ids")
    val statusIds: List<String>? = null,

    val comment: String? = null

)
