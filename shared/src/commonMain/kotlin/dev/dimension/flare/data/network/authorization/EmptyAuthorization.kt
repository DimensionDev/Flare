package dev.dimension.flare.data.network.authorization

internal object EmptyAuthorization : Authorization {
    override val hasAuthorization: Boolean
        get() = false
}
