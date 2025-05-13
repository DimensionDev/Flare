package dev.dimension.flare.data.datasource.flare

import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.server.Api
import io.ktor.client.call.body
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

internal class FlareDataSource(
    private val baseUrl: String,
) {
    private val client =
        ktorClient {
            install(Resources)
            install(ContentNegotiation) {
                json(JSON)
            }
            install(DefaultRequest) {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                url(baseUrl)
            }
        }

    suspend fun translate(
        text: String,
        targetLanguage: String,
    ): String {
        val response =
            client
                .post(Api.V1.Translate()) {
                    setBody(Api.V1.Translate.Request(text, targetLanguage))
                }.body<Api.V1.Translate.Response>()
        return response.result
    }

    suspend fun tldr(
        text: String,
        targetLanguage: String,
    ): String {
        val response =
            client
                .post(Api.V1.Tldr()) {
                    setBody(Api.V1.Tldr.Request(text, targetLanguage))
                }.body<Api.V1.Tldr.Response>()
        return response.result
    }

    suspend fun about(): Api.About.Response = client.get(Api.About()).body()
}
