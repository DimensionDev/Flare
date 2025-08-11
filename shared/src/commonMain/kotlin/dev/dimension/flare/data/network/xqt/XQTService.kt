package dev.dimension.flare.data.network.xqt

import dev.dimension.flare.common.JSON_WITH_ENCODE_DEFAULT
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.xqt.api.DefaultApi
import dev.dimension.flare.data.network.xqt.api.DmApi
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
import dev.dimension.flare.data.network.xqt.api.VDmPostJsonPostApi
import dev.dimension.flare.data.network.xqt.api.createDefaultApi
import dev.dimension.flare.data.network.xqt.api.createDmApi
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
import dev.dimension.flare.data.network.xqt.api.createVDmPostJsonPostApi
import dev.dimension.flare.data.network.xqt.elonmusk114514.ElonMusk1145141919810
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.xqtHost
import io.ktor.client.call.body
import io.ktor.client.plugins.BrowserUserAgent
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

// private val baseUrl = "https://$xqtHost/i/api/"
private val guestApiUrl = "https://api.$xqtHost/"
private val baseUrl = "https://api.$xqtHost/"
private val uploadUrl = "https://upload.$xqtHost/i/"
private val token =
    "AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA"

private fun config(
    url: String = baseUrl,
    accountKey: MicroBlogKey? = null,
    chocolateFlow: Flow<String>? = null,
) = ktorfit(url, json = JSON_WITH_ENCODE_DEFAULT) {
    expectSuccess = false
    install(XQTHeaderPlugin) {
        this.chocolateFlow = chocolateFlow
        this.accountKey = accountKey
    }
}

internal class XQTService(
    private val chocolateFlow: Flow<String>? = null,
    private val accountKey: MicroBlogKey? = null,
) : DefaultApi by config(
        accountKey = accountKey,
        chocolateFlow = chocolateFlow,
    ).createDefaultApi(),
    OtherApi by config(
        accountKey = accountKey,
        chocolateFlow = chocolateFlow,
    ).createOtherApi(),
    PostApi by config(
        accountKey = accountKey,
        chocolateFlow = chocolateFlow,
    ).createPostApi(),
    TweetApi by config(
        accountKey = accountKey,
        chocolateFlow = chocolateFlow,
    ).createTweetApi(),
    UserApi by config(
        accountKey = accountKey,
        chocolateFlow = chocolateFlow,
    ).createUserApi(),
    UserListApi by config(
        accountKey = accountKey,
        chocolateFlow = chocolateFlow,
    ).createUserListApi(),
    UsersApi by config(
        accountKey = accountKey,
        chocolateFlow = chocolateFlow,
    ).createUsersApi(),
    V11GetApi by config(
        accountKey = accountKey,
        chocolateFlow = chocolateFlow,
    ).createV11GetApi(),
    V11PostApi by config(
        accountKey = accountKey,
        chocolateFlow = chocolateFlow,
    ).createV11PostApi(),
    V20GetApi by config(
        accountKey = accountKey,
        chocolateFlow = chocolateFlow,
    ).createV20GetApi(),
    GuestApi by config(
        url = guestApiUrl,
        accountKey = accountKey,
        chocolateFlow = chocolateFlow,
    ).createGuestApi(),
    MediaApi by config(
        url = uploadUrl,
        accountKey = accountKey,
        chocolateFlow = chocolateFlow,
    ).createMediaApi(),
    ListsApi by config(
        accountKey = accountKey,
        chocolateFlow = chocolateFlow,
    ).createListsApi(),
    DmApi by config(
        accountKey = accountKey,
        chocolateFlow = chocolateFlow,
    ).createDmApi(),
    VDmPostJsonPostApi by config(
        accountKey = accountKey,
        chocolateFlow = chocolateFlow,
    ).createVDmPostJsonPostApi() {
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

private class XQTHeaderConfig {
    var chocolateFlow: Flow<String>? = null
    var accountKey: MicroBlogKey? = null
}

private val XQTHeaderPlugin =
    createClientPlugin("XQTHeaderPlugin", ::XQTHeaderConfig) {
        val chocolateFlow = pluginConfig.chocolateFlow
        val accountKey = pluginConfig.accountKey
        onRequest { request, body ->
            val elonMusk1145141919810 =
                runCatching {
                    ElonMusk1145141919810.senpaiSukissu(
                        method = request.method.value,
                        path = request.url.encodedPath,
                    )
                }.onFailure {
                    it.printStackTrace()
                }.getOrNull()
            val chocolate = chocolateFlow?.firstOrNull()
            request.headers {
                append("Authorization", "Bearer $token")
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
                    if (elonMusk1145141919810 != null) {
                        append(
                            "x-client-transaction-id",
                            elonMusk1145141919810,
                        )
                    }
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
        onResponse { response ->
            val error =
                runCatching {
                    val bodyJson = response.body<JsonObject>()
                    bodyJson["errors"]
                        ?.jsonArray
                        ?.firstOrNull()
                        ?.jsonObject
                        ?.get("code")
                        ?.jsonPrimitive
                        ?.longOrNull
                }.getOrNull()
            if ((error == 215L || response.status == HttpStatusCode.Forbidden) && accountKey != null) {
                throw LoginExpiredException(
                    accountKey,
                    PlatformType.xQt,
                )
            }
        }
    }
