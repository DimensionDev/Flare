package dev.dimension.flare.data.network.vvo

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.vvo.api.ConfigApi
import dev.dimension.flare.data.network.vvo.api.StatusApi
import dev.dimension.flare.data.network.vvo.api.TimelineApi
import dev.dimension.flare.data.network.vvo.api.UserApi
import dev.dimension.flare.data.network.vvo.api.createConfigApi
import dev.dimension.flare.data.network.vvo.api.createStatusApi
import dev.dimension.flare.data.network.vvo.api.createTimelineApi
import dev.dimension.flare.data.network.vvo.api.createUserApi
import dev.dimension.flare.data.network.vvo.model.UploadResponse
import dev.dimension.flare.model.vvoHost
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.forms.append
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.util.AttributeKey
import io.ktor.utils.io.KtorDsl
import io.ktor.utils.io.core.writeFully
import kotlin.time.Duration.Companion.minutes

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
) : TimelineApi by config(chocolate = chocolate).createTimelineApi(),
    UserApi by config(chocolate = chocolate).createUserApi(),
    ConfigApi by config(chocolate = chocolate).createConfigApi(),
    StatusApi by config(chocolate = chocolate).createStatusApi() {
    companion object {
        fun checkChocolates(chocolate: String): Boolean =
            chocolate
                .split(';')
                .mapNotNull {
                    val res = it.split('=')
                    val key = res.getOrNull(0)?.trim()
                    val value = res.getOrNull(1)?.trim()
                    if (key != null && value != null) {
                        key to value
                    } else {
                        null
                    }
                }.toMap()
                .let {
                    it.containsKey("MLOGIN") && it["MLOGIN"] == "1"
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

    suspend fun uploadPic(
        st: String,
        filename: String,
        bytes: ByteArray,
        xsrfToken: String = st,
        type: String = "json",
    ): UploadResponse =
        ktorClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 2.minutes.inWholeMilliseconds
                requestTimeoutMillis = 2.minutes.inWholeMilliseconds
                socketTimeoutMillis = 2.minutes.inWholeMilliseconds
            }
            install(VVOHeaderPlugin) {
                this.chocolate = this@VVOService.chocolate
            }
        }.submitFormWithBinaryData(
            url = "https://$vvoHost/api/statuses/uploadPic",
            formData =
                formData {
                    append("type", type)
                    append(
                        "pic",
                        filename,
                        bodyBuilder = {
                            writeFully(bytes)
                        },
                        size = bytes.size.toLong(),
                        contentType = ContentType.Image.JPEG,
                    )

                    append("st", st)
                },
            block = {
                header("X-Xsrf-Token", xsrfToken)
            },
        ).bodyAsText()
            .decodeJson<UploadResponse>()
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
