package dev.dimension.flare.data.network.misskey

import dev.dimension.flare.common.JSON
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.util.AttributeKey
import io.ktor.util.KtorDsl
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

internal class MisskeyAuthorizationPlugin(
    private val token: String,
) {
    @KtorDsl
    class Config(internal var token: String? = null)

    @KtorDsl
    companion object Plugin : HttpClientPlugin<Config, MisskeyAuthorizationPlugin> {
        override val key: AttributeKey<MisskeyAuthorizationPlugin>
            get() = AttributeKey("MisskeyAuthorizationPlugin")

        override fun install(plugin: MisskeyAuthorizationPlugin, scope: HttpClient) {
            plugin.setupRequestAuthorization(scope)
        }

        override fun prepare(block: Config.() -> Unit): MisskeyAuthorizationPlugin {
            val config = Config().apply(block)
            return MisskeyAuthorizationPlugin(config.token!!)
        }
    }

    private fun setupRequestAuthorization(client: HttpClient) {
        client.requestPipeline.intercept(HttpRequestPipeline.Transform) {
            val body = subject
            if (body is TextContent) {
                val element = JSON.decodeFromString(JsonElement.serializer(), body.text)
                if (element is JsonObject) {
                    val newJsonObject = JsonObject(element.jsonObject.toMutableMap().apply {
                        put("i", JsonPrimitive(token))
                    })
                    proceedWith(
                        TextContent(
                            JSON.encodeToString(
                                JsonElement.serializer(),
                                newJsonObject
                            ), body.contentType
                        )
                    )
                }
            } else if (body is ByteArrayContent) {
//                JSON.decodeFromByteArray(JsonElement.serializer(), body.bytes())
            }
//            if (authorization.hasAuthorization) {
//                context.header(header, authorization.getAuthorizationHeader(context))
//            }
        }
    }
}