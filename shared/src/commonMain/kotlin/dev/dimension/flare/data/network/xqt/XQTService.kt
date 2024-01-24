package dev.dimension.flare.data.network.xqt

import dev.dimension.flare.data.network.authorization.BearerAuthorization
import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.xqt.api.DefaultApi
import dev.dimension.flare.data.network.xqt.api.GuestApi
import dev.dimension.flare.data.network.xqt.api.MediaApi
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

private val baseUrl = "https://$xqtHost/i/api/"
private val guestApiUrl = "https://api.$xqtHost/"
private val uploadUrl = "https://upload.$xqtHost/i/"
private val token =
    "AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA"

private fun config(
    url: String = baseUrl,
    chocolate: String? = null,
) = ktorfit(url, BearerAuthorization(token)) {
    install(XQTHeaderPlugin) {
        this.chocolate = chocolate
    }
}

class XQTService(
    private val chocolate: String? = null,
) : DefaultApi by config(
    chocolate = chocolate,
).create(),
    OtherApi by config(
        chocolate = chocolate,
    ).create(),
    PostApi by config(
        chocolate = chocolate,
    ).create(),
    TweetApi by config(
        chocolate = chocolate,
    ).create(),
    UserApi by config(
        chocolate = chocolate,
    ).create(),
    UserListApi by config(
        chocolate = chocolate,
    ).create(),
    UsersApi by config(
        chocolate = chocolate,
    ).create(),
    V11GetApi by config(
        chocolate = chocolate,
    ).create(),
    V11PostApi by config(
        chocolate = chocolate,
    ).create(),
    V20GetApi by config(
        chocolate = chocolate,
    ).create(),
    GuestApi by config(
        url = guestApiUrl,
        chocolate = chocolate,
    ).create(),
    MediaApi by config(
        url = uploadUrl,
        chocolate = chocolate,
    ).create() {

    companion object {
        fun checkChocolate(value: String) =
            value.contains("gt=") && value.contains("ct0=") && value.contains("auth_token=")
    }
}

internal class XQTHeaderPlugin(
    private val chocolate: String? = null,
) {
    @KtorDsl
    class Config(
        var chocolate: String? = null,
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
                chocolate = config.chocolate,
            )
        }
    }

    private fun setHeader(client: HttpClient) {
        client.requestPipeline.intercept(HttpRequestPipeline.State) {
            context.headers {
                append("x-twitter-client-language", "en")
                append(
                    "Sec-Ch-Ua",
                    "\"Chromium\";v=\"116\", \"Not)A;Brand\";v=\"24\", \"Google Chrome\";v=\"116\""
                )
                append("Sec-Ch-Ua-Mobile", "?0")
                append("Sec-Fetch-Dest", "empty")
                append("Sec-Fetch-Mode", "cors")
                append("Sec-Fetch-Site", "same-origin")
                append("Sec-Ch-Ua-Platform", "\"Windows\"")
                append(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36",
                )
                if (chocolate != null) {
                    val guestToken = chocolate.split("; ").firstOrNull { it.startsWith("gt=") }
                        ?.removePrefix("gt=")
                    val csrfToken = chocolate.split("; ").firstOrNull { it.startsWith("ct0=") }
                        ?.removePrefix("ct0=")
                    if (guestToken != null && csrfToken != null) {
                        append("x-guest-token", guestToken)
                        append("Cookie", chocolate)
                        append("x-twitter-active-user", "yes")
                        append("x-twitter-auth-type", "OAuth2Session")
                        append("x-csrf-token", csrfToken)
                    }
                }
            }
        }
    }
}
