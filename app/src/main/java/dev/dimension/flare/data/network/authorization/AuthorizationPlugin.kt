package dev.dimension.flare.data.network.authorization

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.header
import io.ktor.util.AttributeKey
import io.ktor.util.KtorDsl

class AuthorizationPlugin private constructor(
    private val authorization: Authorization,
    private val header: String
) {
    @KtorDsl
    class Config(internal var authorization: Authorization = EmptyAuthorization, internal var header: String? = null)

    @KtorDsl
    companion object Plugin : HttpClientPlugin<Config, AuthorizationPlugin> {
        override val key: AttributeKey<AuthorizationPlugin>
            get() = AttributeKey("AuthorizationPlugin")

        override fun install(plugin: AuthorizationPlugin, scope: HttpClient) {
            plugin.setupRequestAuthorization(scope)
        }

        override fun prepare(block: Config.() -> Unit): AuthorizationPlugin {
            val config = Config().apply(block)
            return AuthorizationPlugin(config.authorization, config.header ?: "Authorization")
        }
    }

    private fun setupRequestAuthorization(client: HttpClient) {
        client.requestPipeline.intercept(HttpRequestPipeline.State) {
            if (authorization.hasAuthorization) {
                context.header(header, authorization.getAuthorizationHeader(context))
            }
        }
    }
}
