package dev.dimension.flare.data.network.misskey.api.model

import kotlinx.serialization.Serializable

@Serializable
internal data class PinnedUsersRequest(val limit: Int? = null)
