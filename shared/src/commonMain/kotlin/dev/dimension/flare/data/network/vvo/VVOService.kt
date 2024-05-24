package dev.dimension.flare.data.network.vvo

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.vvo.api.ConfigApi
import dev.dimension.flare.data.network.vvo.api.StatusApi
import dev.dimension.flare.data.network.vvo.api.TimelineApi
import dev.dimension.flare.data.network.vvo.api.UserApi
import dev.dimension.flare.model.vvoHost
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.util.AttributeKey
import io.ktor.util.KtorDsl

private val baseUrl = "https://$vvoHost/"

private fun config(
    url: String = baseUrl,
    chocolate: String,
) = ktorfit(url) {
    install(VVOHeaderPlugin) {
        this.chocolate = chocolate
    }
}

internal class VVOService(
    private val chocolate: String,
) : TimelineApi by config(chocolate = chocolate).create(),
    UserApi by config(chocolate = chocolate).create(),
    ConfigApi by config(chocolate = chocolate).create(),
    StatusApi by config(chocolate = chocolate).create() {
    companion object {
        fun checkChocolates(chocolate: String): Boolean {
            return chocolate.split(';').map {
                val res = it.split('=')
                res[0].trim() to res[1].trim()
            }.toMap().let {
                it.containsKey("MLOGIN") && it["MLOGIN"] == "1"
            }
        }
    }

    suspend fun getUid(screenName: String): String? {
        val response =
            ktorClient {
                followRedirects = false
            }.get("https://$vvoHost/n/$screenName")
        return response.headers["Location"]?.let {
            return it.split('/').last()
        }
    }
}

private class VVOHeaderPlugin(
    private val chocolate: String,
) {
    @KtorDsl
    class Config {
        var chocolate: String = ""
    }

    @KtorDsl
    companion object Plugin : HttpClientPlugin<Config, VVOHeaderPlugin> {
        override val key: AttributeKey<VVOHeaderPlugin>
            get() = AttributeKey("VVOHeaderPlugin")

        override fun prepare(block: Config.() -> Unit): VVOHeaderPlugin {
            val config = Config().apply(block)
            return VVOHeaderPlugin(config.chocolate)
        }

        override fun install(
            plugin: VVOHeaderPlugin,
            scope: HttpClient,
        ) {
            plugin.setHeader(scope)
        }
    }

    private fun setHeader(client: HttpClient) {
        client.requestPipeline.intercept(HttpRequestPipeline.State) {
            context.header("Cookie", chocolate)
            context.header("Referer", "https://$vvoHost/")
        }
    }
}
