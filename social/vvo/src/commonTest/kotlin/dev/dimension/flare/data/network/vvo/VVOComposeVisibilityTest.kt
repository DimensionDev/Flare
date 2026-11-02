package dev.dimension.flare.data.network.vvo

import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.vvo.toVvoVisible
import dev.dimension.flare.data.datasource.vvo.vvoVisibility
import dev.dimension.flare.data.platform.VVoCredential
import dev.dimension.flare.ui.model.UiTimelineV2.Post.Visibility
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class VVOComposeVisibilityTest {
    @Test
    fun originalAndRepostUseTheOfficialVisibilityFormFields() =
        runTest {
            for (type in listOf(ComposeType.New, ComposeType.Quote)) {
                for ((visibility, expected) in listOf(Visibility.Public to null, Visibility.Friends to "6", Visibility.SelfOnly to "1")) {
                    val expectedPath = if (type == ComposeType.New) "/api/statuses/update" else "/api/statuses/repost"
                    val service =
                        VVOService(
                            credentialFlow = flowOf(VVoCredential("MLOGIN=1; SUB=test")),
                            httpClientFactory = { config ->
                                HttpClient(
                                    MockEngine { request ->
                                        assertEquals(HttpMethod.Post, request.method)
                                        assertEquals(expectedPath, request.url.encodedPath)
                                        val form =
                                            parseQueryString(
                                                assertIs<OutgoingContent.ByteArrayContent>(request.body).bytes().decodeToString(),
                                            )
                                        assertEquals(expected, form["visible"])
                                        assertEquals("post", form["content"])
                                        assertEquals("token", form["st"])
                                        respond(
                                            """{"ok":1,"data":{"id":"posted"}}""",
                                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                                        )
                                    },
                                ) { config(this) }
                            },
                        )
                    if (type == ComposeType.New) {
                        service.updateStatus(content = "post", st = "token", visible = visibility.toVvoVisible())
                    } else {
                        service.repostStatus(id = "quoted", content = "post", st = "token", visible = visibility.toVvoVisible())
                    }
                }
            }
        }

    @Test
    fun onlyOriginalAndRepostOfferTheThreeAudiences() {
        for (type in listOf(ComposeType.New, ComposeType.Quote)) {
            assertEquals(
                listOf(Visibility.Public, Visibility.Friends, Visibility.SelfOnly),
                type.vvoVisibility()?.allVisibilities?.toList(),
            )
            assertEquals(Visibility.Public, type.vvoVisibility()?.defaultVisibility)
        }
        assertNull(ComposeType.Reply.vvoVisibility())
        assertFailsWith<IllegalStateException> { Visibility.Specified.toVvoVisible() }
    }
}
