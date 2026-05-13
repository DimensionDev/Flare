package dev.dimension.flare.data.datastore.model

import kotlinx.serialization.Serializable

private const val DEFAULT_SERVER_URL = "https://api.flareapp.moe"

@Serializable
internal data class FlareConfig(
    val serverUrl: String = DEFAULT_SERVER_URL,
)
