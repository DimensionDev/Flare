package dev.dimension.flare.data.model

public data class PluginPermission(
    val type: PluginPermissionType,
    val origin: String,
    val cookieName: String? = null,
)

public enum class PluginPermissionType {
    NetworkOrigin,
    MediaNetwork,
    Cookie,
}
