package dev.dimension.flare.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

@Immutable
@Serializable
public sealed class AccountType {
    @Serializable
    @Immutable
    public data class Specific(
        val accountKey: MicroBlogKey,
    ) : AccountType() {
        override fun toString(): String = "specific_$accountKey"
    }

    @Serializable
    @Immutable
    public data object Guest : AccountType() {
        override fun toString(): String = "guest"
    }

    @Serializable
    @Immutable
    public data class GuestHost(
        val host: String,
    ) : AccountType() {
        override fun toString(): String = "guest_$host"
    }
}

@HiddenFromObjC
public fun MicroBlogKey?.toAccountType(): AccountType =
    if (this == null) {
        AccountType.Guest
    } else {
        AccountType.Specific(this)
    }

@HiddenFromObjC
public fun MicroBlogKey?.toAccountType(guestHost: String): AccountType =
    if (this == null) {
        AccountType.GuestHost(guestHost)
    } else {
        AccountType.Specific(this)
    }
