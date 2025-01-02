package dev.dimension.flare.ui.route

import androidx.core.bundle.Bundle
import androidx.navigation.NavType
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
