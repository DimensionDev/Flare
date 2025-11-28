package dev.dimension.flare.data.network.mastodon.api.model

import de.jensklingenberg.ktorfit.Ktorfit
import de.jensklingenberg.ktorfit.converter.KtorfitResult
import de.jensklingenberg.ktorfit.converter.TypeData
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.util.reflect.typeInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MastodonPagingConverterFactoryTest {
    private val ktorfit =
        Ktorfit
            .Builder()
            .baseUrl("https://example.com/")
            .build()

    private val pagingType =
        TypeData.createTypeData(
            qualifiedTypename =
                MastodonPaging::class
                    .qualifiedName
                    ?.let { pagingName ->
                        Status::class.qualifiedName?.let { statusName -> "$pagingName<$statusName>" }
                    }
                    ?: "",
            typeInfo = typeInfo<MastodonPaging<Status>>(),
        )

    private val converter =
        MastodonPagingConverterFactory()
            .suspendResponseConverter(pagingType, ktorfit)
            ?: error("Expected MastodonPaging converter")

    @Test
    fun convert_extractsCursorValuesFromLinkHeader() =
        runTest {
            val response =
                createResponse(
                    body = statusesJson("1", "2"),
                    linkHeader =
                        "<https://example.com/api?max_id=42>; rel=\"next\", <https://example.com/api?min_id=21>; rel=\"prev\"",
                )

            val paging = converter.convert(KtorfitResult.Success(response))

            val ids = paging.map { (it as? Status)?.id }
            assertEquals(listOf("1", "2"), ids)
            assertEquals("42", paging.next)
            assertEquals("21", paging.prev)
        }

    @Test
    fun convert_fallsBackToLastItemIdWhenLinkMissing() =
        runTest {
            val response =
                createResponse(
                    body = statusesJson("alpha", "beta", "cursor"),
                )

            val paging = converter.convert(KtorfitResult.Success(response))

            assertEquals("cursor", paging.next)
            assertNull(paging.prev)
        }

    private suspend fun createResponse(
        body: String,
        linkHeader: String? = null,
    ): HttpResponse {
        val headers =
            Headers.build {
                if (linkHeader != null) {
                    append(HttpHeaders.Link, linkHeader)
                }
            }

        val client =
            HttpClient(MockEngine) {
                engine {
                    addHandler {
                        respond(
                            content = body,
                            status = HttpStatusCode.OK,
                            headers = headers,
                        )
                    }
                }
            }

        return client.get("https://example.com")
    }

    private fun statusesJson(vararg ids: String): String = ids.joinToString(prefix = "[", postfix = "]") { id -> "{\"id\":\"$id\"}" }
}
