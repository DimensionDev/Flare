package dev.dimension.flare.data.network.vvo

import dev.dimension.flare.data.platform.VVoCredential
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VVOEmptyResponseTest {
    @Test
    fun textHtmlNoDataPageReturnsEmptyVvoResponse() =
        runTest {
            val service = service("/message/cmt", VVO_NO_DATA_HTML, "text/html; charset=UTF-8")

            val response = service.getComments(page = 1)

            assertNull(response.data)
            assertNull(response.ok)
            assertNull(response.httpCode)
        }

    @Test
    fun jsonMislabeledNoDataPageReturnsEmptyVvoResponse() =
        runTest {
            val service = service("/message/cmt", VVO_NO_DATA_HTML, "application/json")

            val response = service.getComments(page = 1)

            assertNull(response.data)
            assertNull(response.ok)
            assertNull(response.httpCode)
        }

    @Test
    fun noDataPageReturnsEmptyVvoResponseForMutationAndNonSuccessStatus() =
        runTest {
            val service =
                service(
                    expectedPath = "/api/attitudes/create",
                    body = VVO_NO_DATA_HTML,
                    contentType = "text/html; charset=UTF-8",
                    status = HttpStatusCode.NotFound,
                    expectedMethod = HttpMethod.Post,
                )

            val response = service.likeStatus(id = "status-id", st = "st-value")

            assertNull(response.data)
            assertNull(response.ok)
            assertNull(response.httpCode)
        }

    @Test
    fun textHtmlNoDataPageReturnsEmptyHotflowChildData() =
        runTest {
            val service = service("/comments/hotFlowChild", VVO_NO_DATA_HTML, "text/html; charset=UTF-8")

            val response = service.getHotFlowChild(cid = "comment-id")

            assertNull(response.data)
            assertNull(response.rootComment)
            assertNull(response.maxID)
            assertNull(response.ok)
        }

    @Test
    fun jsonMislabeledNoDataPageReturnsEmptyHotflowChildData() =
        runTest {
            val service = service("/comments/hotFlowChild", VVO_NO_DATA_HTML, "application/json")

            val response = service.getHotFlowChild(cid = "comment-id")

            assertNull(response.data)
            assertNull(response.rootComment)
            assertNull(response.maxID)
            assertNull(response.ok)
        }

    @Test
    fun normalVvoResponseJsonIsUnchanged() =
        runTest {
            val service =
                service(
                    expectedPath = "/message/cmt",
                    body = """{"ok":1,"http_code":200,"data":[]}""",
                    contentType = "application/json",
                )

            val response = service.getComments(page = 1)

            assertEquals(1L, response.ok)
            assertEquals(200L, response.httpCode)
            assertTrue(response.data.orEmpty().isEmpty())
        }

    @Test
    fun normalHotflowChildJsonIsUnchanged() =
        runTest {
            val service =
                service(
                    expectedPath = "/comments/hotFlowChild",
                    body =
                        """
                        {
                          "ok": 1,
                          "data": [],
                          "rootComment": [],
                          "total_number": 7,
                          "max_id": 42,
                          "max_id_type": 1
                        }
                        """.trimIndent(),
                    contentType = "application/json",
                )

            val response = service.getHotFlowChild(cid = "comment-id")

            assertEquals(1L, response.ok)
            assertEquals(7L, response.totalNumber)
            assertEquals(42L, response.maxID)
            assertEquals(1L, response.maxIDType)
            assertTrue(response.data.orEmpty().isEmpty())
            assertTrue(response.rootComment.orEmpty().isEmpty())
        }

    @Test
    fun unrelatedTextHtmlStillFails() =
        runTest {
            val service = service("/message/cmt", VVO_LOGIN_HTML, "text/html; charset=UTF-8")

            assertFails {
                service.getComments(page = 1)
            }
        }

    @Test
    fun unrelatedHtmlMislabeledAsJsonStillFails() =
        runTest {
            val service = service("/comments/hotFlowChild", VVO_LOGIN_HTML, "application/json")

            assertFails {
                service.getHotFlowChild(cid = "comment-id")
            }
        }

    @Test
    fun normalStatusDetailHtmlIsReturnedUnchanged() =
        runTest {
            val service = service("/detail/status-id", VVO_STATUS_DETAIL_HTML, "text/html; charset=UTF-8")

            val response = service.getStatusDetail(id = "status-id")

            assertEquals(VVO_STATUS_DETAIL_HTML, response)
        }

    private fun service(
        expectedPath: String,
        body: String,
        contentType: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        expectedMethod: HttpMethod? = null,
    ): VVOService =
        VVOService(
            credentialFlow = flowOf(VVoCredential(chocolate = "MLOGIN=1; SUB=test-sub")),
            httpClientFactory =
                mockHttpClientFactory { request ->
                    assertEquals(expectedPath, request.url.encodedPath)
                    expectedMethod?.let { assertEquals(it, request.method) }
                    response(body = body, contentType = contentType, status = status)
                },
        )

    private fun mockHttpClientFactory(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): (HttpClientConfig<*>.() -> Unit) -> HttpClient =
        { config ->
            HttpClient(
                MockEngine { request ->
                    handler(request)
                },
            ) {
                config.invoke(this)
            }
        }

    private fun MockRequestHandleScope.response(
        body: String,
        contentType: String,
        status: HttpStatusCode,
    ): HttpResponseData =
        respond(
            content = body,
            status = status,
            headers =
                Headers.build {
                    append(HttpHeaders.ContentType, contentType)
                },
        )
}

private val VVO_NO_DATA_HTML =
    """
    <!DOCTYPE html>
    <html lang="zh">
    <head>
        <meta charset="UTF-8">
        <link rel="dns-prefetch" href="https://h5.sinaimg.cn">
        <meta id="viewport" name="viewport"
              content="width=device-width,initial-scale=1.0,minimum-scale=1.0,maximum-scale=1.0">
        <meta name="format-detection" content="telephone=no">
        <title>微博-出错了</title>
        <style>
            html {
                font-size: 2rem;
            }

            @media (max-width: 1024px) {
                html {
                    font-size: 1.25rem;
                }
            }

            @media (max-width: 414px) {
                html {
                    font-size: 1.06rem;
                }
            }

            @media (max-width: 375px) {
                html {
                    font-size: 1rem;
                }
            }
            html,body{
                height:100%;
            }
            body {
                margin: 0;
                padding: 0;
                background-color: #f2f2f2;
            }

            p {
                margin: 0;

            }

            .h5-4box {
                height:100%;
                display: -ms-flexbox;
                display: flex;
                -ms-flex-pack: center;
                justify-content: center;
                -ms-flex-align: center;
                align-items: center;
                -ms-flex-direction: column;
                flex-direction: column;
            }

            .h5-4img {
                /* display: inline-block; */

            }

            .h5-4img img {
                max-width: 100%;
            }

            .h5-4con {
                padding-top: 1.875rem;
                font-size: 0.875rem;
                line-height: 1.2;
                color: #636363;
                text-align: center;
            }

            .h5-5con {
                padding-top: 1.875rem;
                font-size: 0.5rem;
                line-height: 1.2;
                color: #636363;
                text-align: center;
            }

            .btn {
                display: inline-block;
                border: #e86b0f solid 1px;
                margin: 0 0 0 5px;
                padding: 0 10px;
                line-height: 25px;
                font-size: .75rem;
                vertical-align: middle;
                color: #FFF;
                border-radius: 3px;
                background-color: #ff8200;
            }
        </style>
    </head>
    <body>
    <div class="h5-4box">
            <div class="h5-4img">
                <img src="//h5.sinaimg.cn/upload/2016/04/11/319/h5-404.png">
            </div>
        <p class="h5-4con">暂无数据</p>
        <br/>
                <div class="h5-5con"><a href="/">返回首页</a></div>    </div>
    </body>
    </html>
    """.trimIndent()

private val VVO_LOGIN_HTML =
    """
    <!DOCTYPE html>
    <html lang="zh">
    <head><title>微博-登录</title></head>
    <body><p class="h5-4con">请先登录</p></body>
    </html>
    """.trimIndent()

private val VVO_STATUS_DETAIL_HTML =
    """
    <!DOCTYPE html>
    <html lang="zh">
    <head><title>微博正文</title></head>
    <body><script>window.${'$'}render_data = [{"status":{"id":"status-id"}}][0] || {};</script></body>
    </html>
    """.trimIndent()
