package dev.dimension.flare.data.datasource.tumblr

import de.jensklingenberg.ktorfit.converter.ResponseConverterFactory
import de.jensklingenberg.ktorfit.ktorfit
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.network.nullableFallbackJson
import dev.dimension.flare.data.network.tumblr.TumblrAuthResources
import dev.dimension.flare.data.network.tumblr.TumblrResources
import dev.dimension.flare.data.network.tumblr.TumblrService
import dev.dimension.flare.data.network.tumblr.TumblrTokenResponse
import dev.dimension.flare.data.network.tumblr.createTumblrResources
import dev.dimension.flare.data.platform.TumblrCredential
import dev.dimension.flare.model.MicroBlogKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TumblrLoadersTest {
    @Test
    fun postsAndMediaTimelinesUseDifferentPagingKeys() {
        val service = serviceReturning("""{"meta":{"status":200},"response":{"posts":[]}}""")

        val posts = TumblrBlogTimelineLoader(service, accountKey, blogKey, mediaOnly = false)
        val media = TumblrBlogTimelineLoader(service, accountKey, blogKey, mediaOnly = true)

        assertNotEquals(posts.pagingKey, media.pagingKey)
    }

    @Test
    fun mediaTimelineKeepsNextPageWhenUnfilteredResponseIsFull() =
        runTest {
            val service = serviceReturning(fullPostsPageJson())
            val loader = TumblrBlogTimelineLoader(service, accountKey, blogKey, mediaOnly = true)

            val result = loader.load(pageSize = 20, request = PagingRequest.Refresh)

            assertEquals(1, result.data.size)
            assertEquals("20", result.nextKey)
        }

    @Test
    fun followingPagingKeyIncludesRequestedBlog() {
        val service = serviceReturning("""{"meta":{"status":200},"response":{"blogs":[]}}""")
        val staff = TumblrFollowingLoader(service, accountKey, tumblrUserKey("staff"))
        val engineering = TumblrFollowingLoader(service, accountKey, tumblrUserKey("engineering"))

        assertNotEquals(staff.pagingKey, engineering.pagingKey)
        assertTrue(staff.pagingKey.contains("staff"))
    }

    @Test
    fun relationUsesFollowedField() =
        runTest {
            val service =
                serviceReturning(
                    """
                    {
                      "meta": {"status": 200, "msg": "OK"},
                      "response": {
                        "blog": {
                          "name": "staff",
                          "following": false,
                          "followed": true
                        }
                      }
                    }
                    """.trimIndent(),
                )
            val loader = TumblrLoader(service, accountKey)

            val relation = loader.relation(blogKey)

            assertTrue(relation.following)
            assertFalse(relation.blocking)
        }

    private fun serviceReturning(responseJson: String): TumblrService {
        val client =
            HttpClient(
                MockEngine {
                    respond(
                        content = responseJson,
                        status = HttpStatusCode.OK,
                        headers = Headers.build { append(HttpHeaders.ContentType, ContentType.Application.Json.toString()) },
                    )
                },
            ) {
                install(ContentNegotiation) {
                    nullableFallbackJson(JSON)
                }
            }
        val resources =
            ktorfit {
                baseUrl("https://api.tumblr.com/v2/")
                httpClient(client)
                converterFactories(ResponseConverterFactory())
            }.createTumblrResources()
        return TumblrService(
            credentialFlow = MutableStateFlow(credential),
            authResources = unusedAuthResources,
            resources = resources,
        )
    }

    private fun fullPostsPageJson(): String {
        val posts =
            (1..20).joinToString(separator = ",") { id ->
                if (id == 1) {
                    """{"id_string":"1","blog_name":"staff","content":[{"type":"image","media":[{"url":"https://64.media.tumblr.com/1.jpg","width":800,"height":600}]}]}"""
                } else {
                    """{"id_string":"$id","blog_name":"staff","content":[{"type":"text","text":"post $id"}]}"""
                }
            }
        return """{"meta":{"status":200,"msg":"OK"},"response":{"posts":[$posts]}}"""
    }

    private companion object {
        val accountKey = MicroBlogKey(id = "me", host = "tumblr.com")
        val blogKey = tumblrUserKey("staff")
        val credential =
            TumblrCredential(
                accessToken = "token",
                blogIdentifier = "me",
                blogName = "me",
                blogUrl = "https://me.tumblr.com/",
            )
        val unusedAuthResources =
            object : TumblrAuthResources {
                override suspend fun requestToken(
                    grantType: String,
                    clientId: String,
                    clientSecret: String,
                    redirectUri: String,
                    code: String,
                ): TumblrTokenResponse = error("Not used")

                override suspend fun refreshToken(
                    grantType: String,
                    clientId: String,
                    clientSecret: String,
                    refreshToken: String,
                ): TumblrTokenResponse = error("Not used")
            }
    }
}
