package dev.dimension.flare.data.network.xqt

import dev.dimension.flare.data.network.authorization.BearerAuthorization
import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.xqt.api.DefaultApi
import dev.dimension.flare.data.network.xqt.api.OtherApi
import dev.dimension.flare.data.network.xqt.api.PostApi
import dev.dimension.flare.data.network.xqt.api.TweetApi
import dev.dimension.flare.data.network.xqt.api.UserApi
import dev.dimension.flare.data.network.xqt.api.UserListApi
import dev.dimension.flare.data.network.xqt.api.UsersApi
import dev.dimension.flare.data.network.xqt.api.V11GetApi
import dev.dimension.flare.data.network.xqt.api.V11PostApi
import dev.dimension.flare.data.network.xqt.api.V20GetApi
import dev.dimension.flare.model.xqtHost
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.headers
import io.ktor.util.AttributeKey
import io.ktor.util.KtorDsl

private val baseUrl = "https://$xqtHost/i/api"
private val token =
    "AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA"

private fun config(
    guestToken: String? = null,
    chocolate: String? = null,
    csrfToken: String? = null,
) = ktorfit(baseUrl, BearerAuthorization(token)) {
    install(XQTHeaderPlugin) {
        this.guestToken = guestToken
        this.chocolate = chocolate
        this.csrfToken = csrfToken
    }
}

class XQTService(
    private val guestToken: String? = null,
    private val chocolate: String? = null,
    private val csrfToken: String? = null,
) : DefaultApi by config(
        guestToken = guestToken,
        chocolate = chocolate,
        csrfToken = csrfToken,
    ).create(),
    OtherApi by config(
        guestToken = guestToken,
        chocolate = chocolate,
        csrfToken = csrfToken,
    ).create(),
    PostApi by config(
        guestToken = guestToken,
        chocolate = chocolate,
        csrfToken = csrfToken,
    ).create(),
    TweetApi by config(
        guestToken = guestToken,
        chocolate = chocolate,
        csrfToken = csrfToken,
    ).create(),
    UserApi by config(
        guestToken = guestToken,
        chocolate = chocolate,
        csrfToken = csrfToken,
    ).create(),
    UserListApi by config(
        guestToken = guestToken,
        chocolate = chocolate,
        csrfToken = csrfToken,
    ).create(),
    UsersApi by config(
        guestToken = guestToken,
        chocolate = chocolate,
        csrfToken = csrfToken,
    ).create(),
    V11GetApi by config(
        guestToken = guestToken,
        chocolate = chocolate,
        csrfToken = csrfToken,
    ).create(),
    V11PostApi by config(
        guestToken = guestToken,
        chocolate = chocolate,
        csrfToken = csrfToken,
    ).create(),
    V20GetApi by config(
        guestToken = guestToken,
        chocolate = chocolate,
        csrfToken = csrfToken,
    ).create()

private class XQTHeaderPlugin(
    private val guestToken: String? = null,
    private val chocolate: String? = null,
    private val csrfToken: String? = null,
) {
    @KtorDsl
    class Config(
        var guestToken: String? = null,
        var chocolate: String? = null,
        var csrfToken: String? = null,
    )

    @KtorDsl
    companion object Plugin : HttpClientPlugin<Config, XQTHeaderPlugin> {
        override val key: AttributeKey<XQTHeaderPlugin>
            get() = AttributeKey("XQTHeaderPlugin")

        override fun install(
            plugin: XQTHeaderPlugin,
            scope: HttpClient,
        ) {
            plugin.setHeader(scope)
        }

        override fun prepare(block: Config.() -> Unit): XQTHeaderPlugin {
            val config = Config().apply(block)
            return XQTHeaderPlugin(
                guestToken = config.guestToken,
                chocolate = config.chocolate,
                csrfToken = config.csrfToken,
            )
        }
    }

    private fun setHeader(client: HttpClient) {
        client.requestPipeline.intercept(HttpRequestPipeline.State) {
            context.headers {
                append("x-twitter-client-language", "en")
                append("Sec-Ch-Ua", "\"Chromium\";v=\"116\", \"Not)A;Brand\";v=\"24\", \"Google Chrome\";v=\"116\"")
                append("Sec-Ch-Ua-Mobile", "?0")
                append("Sec-Fetch-Dest", "empty")
                append("Sec-Fetch-Mode", "cors")
                append("Sec-Fetch-Site", "same-origin")
                append("Sec-Ch-Ua-Platform", "\"Windows\"")
                append(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36",
                )
                if (guestToken != null) {
                    append("x-guest-token", guestToken)
                }
                if (chocolate != null) {
                    append("Cookie", chocolate)
                    append("x-twitter-active-user", "yes")
                    append("x-twitter-auth-type", "OAuth2Session")
                }
                if (csrfToken != null) {
                    append("x-csrf-token", csrfToken)
                }
            }
        }
    }
}
