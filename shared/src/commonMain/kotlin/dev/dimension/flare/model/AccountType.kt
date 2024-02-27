package dev.dimension.flare.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@Serializable
sealed interface AccountType {
    @JvmInline
    @Serializable
    value class Specific(val accountKey: MicroBlogKey) : AccountType {
        override fun toString(): String {
            return "specific_$accountKey"
        }
    }

    @Serializable
    data object Active : AccountType {
        override fun toString(): String {
            return "active"
        }
    }
}
