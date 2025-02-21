package dev.dimension.flare.ui.route

import androidx.core.bundle.Bundle
import androidx.navigation.NavType
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.model.MicroBlogKey
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.KSerializer

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

internal data class JsonSerializableNavType<T : Any>(
    private val serializer: KSerializer<T>,
) : NavType<T>(isNullableAllowed = false) {
    override fun put(
        bundle: Bundle,
        key: String,
        value: T,
    ) {
        bundle.putString(key, value.encodeJson(serializer))
    }

    override fun get(
        bundle: Bundle,
        key: String,
    ): T = parseValue(bundle.getString(key)!!)

    override fun serializeAsValue(value: T): String = value.encodeJson(serializer).encodeURLPathPart()

    override fun parseValue(value: String): T = value.decodeJson(serializer)
}
