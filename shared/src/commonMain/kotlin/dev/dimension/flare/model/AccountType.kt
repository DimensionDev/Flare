package dev.dimension.flare.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface AccountType {
    @Serializable
    data class Specific(
        val accountKey: MicroBlogKey,
    ) : AccountType {
        override fun toString(): String = "specific_$accountKey"
    }

    @Serializable
    data object Active : AccountType {
        override fun toString(): String = "active"
    }

    @Serializable
    data object Guest : AccountType {
        override fun toString(): String = "guest"
    }
}
