package dev.dimension.flare.data.network.authorization

object EmptyAuthorization : Authorization {
    override val hasAuthorization: Boolean
        get() = false
}
