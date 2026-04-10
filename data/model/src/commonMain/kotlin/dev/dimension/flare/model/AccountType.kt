package dev.dimension.flare.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
public sealed interface DbAccountType

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
    public data object Guest : AccountType(), DbAccountType {
        override fun toString(): String = "guest"
    }

    @Serializable
    @Immutable
    public data class GuestHost(
        val host: String,
    ) : AccountType(),
        DbAccountType {
        override fun toString(): String = "guest_$host"
    }
}

public fun MicroBlogKey?.toAccountType(): AccountType =
    if (this == null) {
        AccountType.Guest
    } else {
        AccountType.Specific(this)
    }

public fun MicroBlogKey?.toAccountType(guestHost: String): AccountType =
    if (this == null) {
        AccountType.GuestHost(guestHost)
    } else {
        AccountType.Specific(this)
    }
