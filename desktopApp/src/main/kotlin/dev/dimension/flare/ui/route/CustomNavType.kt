package dev.dimension.flare.ui.route

import androidx.core.bundle.Bundle
import androidx.navigation.NavType
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.model.TimelineTabItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey

internal val MicroblogKeyNavType =
    object : NavType<MicroBlogKey>(isNullableAllowed = true) {
        override fun get(
            bundle: Bundle,
            key: String,
        ): MicroBlogKey? = bundle.getString(key)?.let(MicroBlogKey::valueOf)

        override fun parseValue(value: String): MicroBlogKey = MicroBlogKey.valueOf(value)

        override fun put(
            bundle: Bundle,
            key: String,
            value: MicroBlogKey,
        ) {
            bundle.putString(key, value.toString())
        }
    }

internal val AccountTypeNavType =
    object : NavType<AccountType>(isNullableAllowed = true) {
        override fun get(
            bundle: Bundle,
            key: String,
        ): AccountType? = bundle.getString(key)?.decodeJson(AccountType.serializer())

        override fun parseValue(value: String): AccountType = value.decodeJson(AccountType.serializer())

        override fun put(
            bundle: Bundle,
            key: String,
            value: AccountType,
        ) {
            bundle.putString(key, value.encodeJson(AccountType.serializer()))
        }
    }

internal val TimelineTabItemNavType =
    object : NavType<TimelineTabItem>(isNullableAllowed = true) {
        override fun get(
            bundle: Bundle,
            key: String,
        ): TimelineTabItem? = bundle.getString(key)?.decodeJson(TimelineTabItem.serializer())

        override fun parseValue(value: String): TimelineTabItem = value.decodeJson(TimelineTabItem.serializer())

        override fun put(
            bundle: Bundle,
            key: String,
            value: TimelineTabItem,
        ) {
            bundle.putString(key, value.encodeJson(TimelineTabItem.serializer()))
        }
    }
