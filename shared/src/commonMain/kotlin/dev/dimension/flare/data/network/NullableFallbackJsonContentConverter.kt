package dev.dimension.flare.data.network

import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.decodeFromStringWithNullableFallback
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.Configuration
import io.ktor.serialization.ContentConverter
import io.ktor.serialization.JsonConvertException
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.serialization.kotlinx.serializerForTypeInfo
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.json.Json
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
public fun Configuration.nullableFallbackJson(
    json: Json = JSON,
    contentType: ContentType = ContentType.Application.Json,
) {
    register(contentType, NullableFallbackJsonContentConverter(json))
}

@OptIn(ExperimentalSerializationApi::class, InternalSerializationApi::class)
private class NullableFallbackJsonContentConverter(
    private val json: Json,
) : ContentConverter {
    private val delegate = KotlinxSerializationConverter(json)

    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?,
    ): OutgoingContent? = delegate.serialize(contentType, charset, typeInfo, value)

    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel,
    ): Any? {
        val serializer = json.serializersModule.serializerForTypeInfo(typeInfo)
        val body = content.readRemaining().readText(charset)
        return try {
            json.decodeFromStringWithNullableFallback(serializer, body)
        } catch (cause: Throwable) {
            throw JsonConvertException("Illegal input: ${cause.message}", cause)
        }
    }
}
