package dev.dimension.flare.data.network.mastodon.api

import de.jensklingenberg.ktorfit.converter.ResponseConverterFactory
import de.jensklingenberg.ktorfit.ktorfit
import dev.dimension.flare.data.network.mastodon.api.model.MastodonPagingConverterFactory
import dev.dimension.flare.data.network.mastodon.api.model.NotificationTypes
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineResourcesTest {
    @Test
    fun notificationSerializesExcludedTypesAsLowercaseQueryParameters() =
        runTest {
            var requestedUrl: Url? = null
            val client =
                HttpClient(MockEngine) {
                    engine {
                        addHandler { request ->
                            requestedUrl = request.url
                            respond(
                                content = "[]",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }
                    }
                }
            val resources =
                ktorfit {
                    baseUrl("https://example.com/")
                    httpClient(client)
                    converterFactories(
                        ResponseConverterFactory(),
                        MastodonPagingConverterFactory(),
                    )
                }.createTimelineResources()

            resources.notification(
                exclude_types =
                    NotificationTypes.entries
                        .filter { it != NotificationTypes.Mention }
                        .map { it.apiValue },
            )

            assertEquals(
                listOf("follow", "favourite", "reblog", "poll", "follow_request", "status", "update"),
                requestedUrl?.parameters?.getAll("exclude_types[]"),
            )
        }
}
