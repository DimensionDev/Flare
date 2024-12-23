package dev.dimension.flare.model

import kotlinx.serialization.Serializable

@Serializable
public sealed interface AccountType {
    @Serializable
    public data class Specific(
        val accountKey: MicroBlogKey,
    ) : AccountType {
        override fun toString(): String = "specific_$accountKey"
    }

    @Serializable
    public data object Active : AccountType {
        override fun toString(): String = "active"
    }

    @Serializable
    public data object Guest : AccountType {
        override fun toString(): String = "guest"
    }
}
