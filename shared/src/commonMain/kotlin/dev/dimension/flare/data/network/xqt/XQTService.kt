package dev.dimension.flare.data.network.xqt

import dev.dimension.flare.common.JSON_WITH_ENCODE_DEFAULT
import dev.dimension.flare.data.network.authorization.BearerAuthorization
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.xqt.api.DefaultApi
import dev.dimension.flare.data.network.xqt.api.GuestApi
import dev.dimension.flare.data.network.xqt.api.ListsApi
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
import dev.dimension.flare.data.network.xqt.api.createDefaultApi
import dev.dimension.flare.data.network.xqt.api.createGuestApi
import dev.dimension.flare.data.network.xqt.api.createListsApi
import dev.dimension.flare.data.network.xqt.api.createMediaApi
import dev.dimension.flare.data.network.xqt.api.createOtherApi
import dev.dimension.flare.data.network.xqt.api.createPostApi
import dev.dimension.flare.data.network.xqt.api.createTweetApi
import dev.dimension.flare.data.network.xqt.api.createUserApi
import dev.dimension.flare.data.network.xqt.api.createUserListApi
import dev.dimension.flare.data.network.xqt.api.createUsersApi
import dev.dimension.flare.data.network.xqt.api.createV11GetApi
import dev.dimension.flare.data.network.xqt.api.createV11PostApi
import dev.dimension.flare.data.network.xqt.api.createV20GetApi
import dev.dimension.flare.model.xqtHost
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.util.AttributeKey
import io.ktor.utils.io.KtorDsl

private val baseUrl = "https://$xqtHost/i/api/"
private val guestApiUrl = "https://api.$xqtHost/"
private val uploadUrl = "https://upload.$xqtHost/i/"
private val token =
    "AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA"

private fun config(
    url: String = baseUrl,
    chocolate: String? = null,
) = ktorfit(url, BearerAuthorization(token), json = JSON_WITH_ENCODE_DEFAULT) {
    install(XQTHeaderPlugin) {
        this.chocolate = chocolate
    }
}

internal class XQTService(
    private val chocolate: String? = null,
) : DefaultApi by config(
        chocolate = chocolate,
    ).createDefaultApi(),
    OtherApi by config(
        chocolate = chocolate,
    ).createOtherApi(),
    PostApi by config(
        chocolate = chocolate,
    ).createPostApi(),
    TweetApi by config(
        chocolate = chocolate,
    ).createTweetApi(),
    UserApi by config(
        chocolate = chocolate,
    ).createUserApi(),
    UserListApi by config(
        chocolate = chocolate,
    ).createUserListApi(),
    UsersApi by config(
        chocolate = chocolate,
    ).createUsersApi(),
    V11GetApi by config(
        chocolate = chocolate,
    ).createV11GetApi(),
    V11PostApi by config(
        chocolate = chocolate,
    ).createV11PostApi(),
    V20GetApi by config(
        chocolate = chocolate,
    ).createV20GetApi(),
    GuestApi by config(
        url = guestApiUrl,
        chocolate = chocolate,
    ).createGuestApi(),
    MediaApi by config(
        url = uploadUrl,
        chocolate = chocolate,
    ).createMediaApi(),
    ListsApi by config(
        chocolate = chocolate,
    ).createListsApi() {
    companion object {
        fun checkChocolate(value: String) =
//            value.contains("gt=") &&
            value.contains("ct0=") && value.contains("auth_token=")
    }

    suspend fun getInitialUserId(chocolate: String): String? {
        val response =
            ktorClient {
                BrowserUserAgent()
                defaultRequest {
                    headers {
                        append("Cookie", chocolate)
                        append("sec-fetch-dest", "document")
                        append("sec-fetch-mode", "navigate")
                        append("sec-fetch-site", "cross-site")
                        append("sec-gpc", "1")
                        append("upgrade-insecure-requests", "1")
                        append("accept-language", "en-US,en;q=0.9")
                        append("dnt", "1")
                        append("host", "x" + ".com")
                    }
                    accept(ContentType.Text.Html)
                }
            }.get("https://" + "x" + ".com" + "/home").body<String>()
        val userIdRegex = "\"user_id\":\"([0-9]+)\"".toRegex()
        return userIdRegex.find(response)?.groupValues?.get(1)
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
                    "\"Chromium\";v=\"116\", \"Not)A;Brand\";v=\"24\", \"Google Chrome\";v=\"116\"",
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
                    val guestToken =
                        chocolate
                            .split("; ")
                            .firstOrNull { it.startsWith("gt=") }
                            ?.removePrefix("gt=")
                    val csrfToken =
                        chocolate
                            .split("; ")
                            .firstOrNull { it.startsWith("ct0=") }
                            ?.removePrefix("ct0=")
                    if (guestToken != null) {
                        append("x-guest-token", guestToken)
                    }
                    if (csrfToken != null) {
                        append("x-twitter-active-user", "yes")
                        append("x-twitter-auth-type", "OAuth2Session")
                        append("x-csrf-token", csrfToken)
                    }
                    append("Cookie", chocolate)
                }
            }
        }
    }
}
