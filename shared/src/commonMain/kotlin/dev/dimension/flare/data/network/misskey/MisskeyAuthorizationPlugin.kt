package dev.dimension.flare.data.network.misskey

import dev.dimension.flare.common.JSON
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.setBody
import io.ktor.http.content.TextContent
import io.ktor.util.AttributeKey
import io.ktor.utils.io.KtorDsl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

internal class MisskeyAuthorizationPlugin(
    private val accessTokenFlow: Flow<String>?,
) {
    @KtorDsl
    class Config(
        internal var accessTokenFlow: Flow<String>? = null,
    )

    @KtorDsl
    companion object Plugin : HttpClientPlugin<Config, MisskeyAuthorizationPlugin> {
        override val key: AttributeKey<MisskeyAuthorizationPlugin>
            get() = AttributeKey("MisskeyAuthorizationPlugin")

        override fun install(
            plugin: MisskeyAuthorizationPlugin,
            scope: HttpClient,
        ) {
            scope.plugin(HttpSend.Plugin).intercept { context ->
                val body = context.body
                if (body is TextContent) {
                    val element = JSON.decodeFromString(JsonElement.serializer(), body.text)
                    if (element is JsonObject) {
                        val token = plugin.accessTokenFlow?.firstOrNull()
                        val newJsonObject =
                            JsonObject(
                                element.jsonObject.toMutableMap().apply {
                                    put("i", JsonPrimitive(token))
                                },
                            )
                        context.setBody(
                            TextContent(
                                JSON.encodeToString(
                                    JsonElement.serializer(),
                                    newJsonObject,
                                ),
                                body.contentType,
                            ),
                        )
                    }
                }
                execute(context)
            }
        }

        override fun prepare(block: Config.() -> Unit): MisskeyAuthorizationPlugin {
            val config = Config().apply(block)
            return MisskeyAuthorizationPlugin(config.accessTokenFlow)
        }
    }
}
