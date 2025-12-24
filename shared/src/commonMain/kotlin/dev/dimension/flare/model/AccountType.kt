package dev.dimension.flare.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface DbAccountType

@Immutable
@Serializable
public sealed class AccountType {
    @Serializable
    @Immutable
    public data class Specific(
        val accountKey: MicroBlogKey,
    ) : AccountType(),
        DbAccountType {
        override fun toString(): String = "specific_$accountKey"
    }

    @Serializable
    @Immutable
    public data object Active : AccountType() {
        override fun toString(): String = "active"
    }

    @Serializable
    @Immutable
    public data object Guest : AccountType(), DbAccountType {
        override fun toString(): String = "guest"
    }
}
