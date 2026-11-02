package dev.dimension.flare.data.model

import kotlinx.serialization.Serializable

@Serializable
public data class PluginPermission(
    val type: PluginPermissionType,
    val origin: String,
    val cookieName: String? = null,
)

@Serializable
public enum class PluginPermissionType {
    NetworkOrigin,
    MediaNetwork,
    Cookie,
}
