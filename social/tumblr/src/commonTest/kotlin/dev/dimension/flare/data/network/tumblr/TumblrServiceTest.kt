package dev.dimension.flare.data.network.tumblr

import de.jensklingenberg.ktorfit.converter.ResponseConverterFactory
import de.jensklingenberg.ktorfit.ktorfit
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.nullableFallbackJson
import dev.dimension.flare.data.platform.TumblrCredential
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TumblrServiceTest {
    @Test
    fun createPostUsesNpfPostsEndpointForJsonBody() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val service =
                TumblrService(
                    credentialFlow = MutableStateFlow(credential()),
                    authResources = unusedAuthResources,
                    resources =
                        mockResources { scope, request ->
                            requests += request
                            scope.ok()
                        },
                )

            service.createPost(
                blogIdentifier = "mtlaster",
                request =
                    TumblrCreatePostRequest(
                        content =
                            listOf(
                                TumblrNpfBlock(
                                    type = "text",
                                    text = "tst",
                                ),
                            ),
                        state = "private",
                    ),
                media = emptyList(),
            )

            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/v2/blog/mtlaster/posts", request.url.encodedPath)
            assertEquals(ContentType.Application.Json, request.body.contentType)
        }

    @Test
    fun createPostUsesNpfPostsEndpointForMultipartBody() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val service =
                TumblrService(
                    credentialFlow = MutableStateFlow(credential()),
                    authResources = unusedAuthResources,
                    resources =
                        mockResources { scope, request ->
                            requests += request
                            scope.ok()
                        },
                )

            service.createPost(
                blogIdentifier = "mtlaster",
                request =
                    TumblrCreatePostRequest(
                        content =
                            listOf(
                                TumblrNpfBlock(
                                    type = "image",
                                    media = listOf(TumblrNpfMedia(identifier = "media0", type = "image/jpeg")),
                                ),
                            ),
                        state = "published",
                    ),
                media =
                    listOf(
                        TumblrNpfMedia(identifier = "media0", type = "image/jpeg") to
                            ComposeMediaFile(
                                bytes = byteArrayOf(1, 2, 3),
                                fileName = "image.jpg",
                                mimeType = "image/jpeg",
                            ),
                    ),
            )

            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/v2/blog/mtlaster/posts", request.url.encodedPath)
            assertNotNull(request.body.contentType)
            assertTrue(request.body.contentType?.match(ContentType.MultiPart.FormData) == true)
        }

    @Test
    fun createPostSerializesImageAndVideoMediaShape() {
        val image =
            JSON.encodeToString(
                TumblrCreatePostRequest(
                    content =
                        listOf(
                            TumblrNpfBlock(
                                type = "image",
                                media = listOf(TumblrNpfMedia(identifier = "image0", type = "image/jpeg")),
                            ),
                        ),
                ),
            )
        val video =
            JSON.encodeToString(
                TumblrCreatePostRequest(
                    content =
                        listOf(
                            TumblrNpfBlock(
                                type = "video",
                                media = listOf(TumblrNpfMedia(identifier = "video0", type = "video/mp4")),
                                poster = listOf(TumblrNpfMedia(url = "https://example.com/poster.jpg", type = "image/jpeg")),
                            ),
                        ),
                ),
            )

        assertTrue("\"media\":[{\"identifier\":\"image0\",\"type\":\"image/jpeg\"}]" in image)
        assertTrue("\"media\":{\"identifier\":\"video0\",\"type\":\"video/mp4\"}" in video)
        assertTrue("\"poster\":[{\"type\":\"image/jpeg\",\"url\":\"https://example.com/poster.jpg\"}]" in video)
    }

    @Test
    fun blogInfoDecodesFollowedAndUuidOnlyBlog() {
        val followed = JSON.decodeFromString<TumblrBlog>("""{"name":"staff","followed":true}""")
        val uuidOnly = JSON.decodeFromString<TumblrBlog>("""{"uuid":"t:abc123"}""")

        assertEquals(true, followed.followed)
        assertEquals(null, uuidOnly.name)
        assertEquals("t:abc123", uuidOnly.uuid)
    }

    @Test
    fun timelineRequestsIncludeReblogInfo() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val service =
                TumblrService(
                    credentialFlow = MutableStateFlow(credential()),
                    authResources = unusedAuthResources,
                    resources =
                        mockResources { scope, request ->
                            requests += request
                            scope.respondJson("""{"meta":{"status":200,"msg":"OK"},"response":{"posts":[]}}""")
                        },
                )

            service.dashboard(limit = 20, offset = 40)
            service.blogPosts(blogIdentifier = "mtlaster", limit = 20, offset = 40)

            assertEquals("true", requests[0].url.parameters["reblog_info"])
            assertEquals("true", requests[1].url.parameters["reblog_info"])
        }

    @Test
    fun followingUsesBlogScopedEndpoint() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val service =
                TumblrService(
                    credentialFlow = MutableStateFlow(credential()),
                    authResources = unusedAuthResources,
                    resources =
                        mockResources { scope, request ->
                            requests += request
                            scope.respondJson(
                                """{"meta":{"status":200,"msg":"OK"},"response":{"blogs":[]}}""",
                            )
                        },
                )

            service.following(
                blogIdentifier = "mtlaster",
                limit = 20,
                offset = 40,
            )

            val request = requests.single()
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/v2/blog/mtlaster/following", request.url.encodedPath)
            assertEquals("20", request.url.parameters["limit"])
            assertEquals("40", request.url.parameters["offset"])
        }

    @Test
    fun createPostDecodesNpfPostMutationResponse() =
        runTest {
            val resources =
                mockResources { scope, _ ->
                    scope.respondJson(
                        """
                        {
                          "meta": {"status": 201, "msg": "Created"},
                          "response": {
                            "id": "123",
                            "id_string": "123",
                            "state": "private",
                            "display_text": "Posted to Tumblr"
                          }
                        }
                        """.trimIndent(),
                        status = HttpStatusCode.Created,
                    )
                }

            val envelope =
                resources.createPost(
                    authorization = "Bearer token",
                    blogIdentifier = "mtlaster",
                    request =
                        TumblrCreatePostRequest(
                            content =
                                listOf(
                                    TumblrNpfBlock(
                                        type = "text",
                                        text = "test",
                                    ),
                                ),
                            state = "private",
                        ),
                )

            assertEquals("123", envelope.response?.id)
            assertEquals("123", envelope.response?.idString)
            assertEquals("private", envelope.response?.state)
            assertEquals("Posted to Tumblr", envelope.response?.displayText)
        }

    @Test
    fun followDecodesBlogResponse() =
        runTest {
            val resources =
                mockResources { scope, _ ->
                    scope.respondJson(
                        """
                        {
                          "meta": {"status": 200, "msg": "OK"},
                          "response": {
                            "blog": {
                              "name": "staff",
                              "title": "Tumblr Staff",
                              "url": "https://staff.tumblr.com/",
                              "uuid": "t:staff"
                            }
                          }
                        }
                        """.trimIndent(),
                    )
                }

            val envelope =
                resources.follow(
                    authorization = "Bearer token",
                    blogUrl = "https://staff.tumblr.com/",
                )

            assertEquals("staff", envelope.response?.blog?.name)
            assertEquals("Tumblr Staff", envelope.response?.blog?.title)
        }

    @Test
    fun actionResponseAllowsEmptyArrayBody() =
        runTest {
            val service =
                TumblrService(
                    credentialFlow = MutableStateFlow(credential()),
                    authResources = unusedAuthResources,
                    resources =
                        mockResources { scope, _ ->
                            scope.respondJson("""{"meta":{"status":200,"msg":"OK"},"response":[]}""")
                        },
                )

            service.like(
                postId = "123",
                reblogKey = "abc123",
            )
        }

    @Test
    fun reblogWithCommentUsesReblogEndpoint() =
        runTest {
            val requests = mutableListOf<HttpRequestData>()
            val service =
                TumblrService(
                    credentialFlow = MutableStateFlow(credential()),
                    authResources = unusedAuthResources,
                    resources =
                        mockResources { scope, request ->
                            requests += request
                            scope.ok()
                        },
                )

            service.reblog(
                blogIdentifier = "mtlaster",
                postId = "123",
                reblogKey = "abc123",
                comment = "quote text",
                state = "private",
            )

            val request = requests.single()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/v2/blog/mtlaster/post/reblog", request.url.encodedPath)
            assertNotNull(request.body.contentType)
            assertTrue(request.body.contentType?.match(ContentType.Application.FormUrlEncoded) == true)
        }

    private fun credential(): TumblrCredential =
        TumblrCredential(
            accessToken = "token",
            blogIdentifier = "mtlaster",
            blogName = "mtlaster",
            blogUrl = "https://mtlaster.tumblr.com/",
        )

    private fun mockResources(handler: TumblrRequestHandler): TumblrResources {
        val client =
            HttpClient(
                MockEngine { request ->
                    handler.handle(this, request)
                },
            ) {
                install(ContentNegotiation) {
                    nullableFallbackJson(JSON)
                }
            }
        return ktorfit {
            baseUrl("https://api.tumblr.com/v2/")
            httpClient(client)
            converterFactories(ResponseConverterFactory())
        }.createTumblrResources()
    }

    private val unusedAuthResources =
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

    private fun MockRequestHandleScope.ok() =
        respondJson(
            content = """{"meta":{"status":201,"msg":"Created"},"response":{}}""",
            status = HttpStatusCode.Created,
        )

    private fun MockRequestHandleScope.respondJson(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = content,
        status = status,
        headers =
            Headers.build {
                append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            },
    )
}

private fun interface TumblrRequestHandler {
    suspend fun handle(
        scope: MockRequestHandleScope,
        request: HttpRequestData,
    ): HttpResponseData
}
