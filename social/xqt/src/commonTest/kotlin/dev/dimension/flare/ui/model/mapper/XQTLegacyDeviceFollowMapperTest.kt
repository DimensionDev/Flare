package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.data.database.cache.mapper.tweets
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.network.nullableFallbackJson
import dev.dimension.flare.data.network.xqt.model.Tweet
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.network.xqt.model.legacy.TopLevel
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.postEventOrNull
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class XQTLegacyDeviceFollowMapperTest {
    private val accountKey = MicroBlogKey(id = "me", host = "x.com")

    @BeforeTest
    fun setup() {
        startKoin {
            modules(
                module {
                    single<PlatformFormatter> { TestFormatter() }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun normalizedRetweetResolvesOriginalTweetUserAndActionState() =
        runTest {
            val response = decodeLikeProduction(DEVICE_FOLLOW_RESPONSE)
            assertEquals(3, response.globalObjects?.users?.size)
            val timeline = response.tweets().single()
            val wrapper = assertIs<Tweet>(timeline.tweets.tweetResults.result)
            val wrapperUser = assertIs<User>(wrapper.core?.userResults?.result)
            val original = assertIs<Tweet>(assertNotNull(wrapper.legacy).retweetedStatusResult?.result)
            val originalUser = assertIs<User>(original.core?.userResults?.result)

            assertEquals("reposter", wrapperUser.restId)
            assertEquals("original-author", originalUser.restId)
            assertEquals(3_060, original.legacy?.retweetCount)
            assertEquals(true, original.legacy?.retweeted)

            val rendered = assertIs<UiTimelineV2.TimelinePostItem>(timeline.render(accountKey))
            assertEquals(
                "reposter",
                rendered.presentation.message
                    ?.user
                    ?.key
                    ?.id,
            )
            assertEquals("original-tweet", rendered.displayPost.statusKey.id)
            assertEquals(
                "original-author",
                rendered.displayPost.user
                    ?.key
                    ?.id,
            )
            assertTrue(
                UiProfile.Mark.Verified in
                    rendered.displayPost.user
                        ?.mark
                        .orEmpty(),
            )

            val retweetAction =
                rendered.displayPost.actions
                    .filterIsInstance<ActionMenu.Group>()
                    .first { it.displayItem.actionFamily == PostActionFamily.Repost }
                    .displayItem
            val retweetEvent = assertIs<PostEvent.XQT.Retweet>(retweetAction.clickEvent.postEventOrNull()?.postEvent)
            assertEquals(UiIcon.Unretweet, retweetAction.icon)
            assertEquals(3_060L, retweetAction.count?.value)
            assertEquals("original-tweet", retweetEvent.postKey.id)
            assertEquals(true, retweetEvent.retweeted)
            assertEquals(3_060L, retweetEvent.count)
        }

    private suspend fun decodeLikeProduction(body: String): TopLevel {
        val client =
            HttpClient(MockEngine) {
                engine {
                    addHandler {
                        respond(
                            content = body,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                }
                install(ContentNegotiation) {
                    nullableFallbackJson()
                }
            }
        return client.get("https://example.com").body()
    }
}

private val DEVICE_FOLLOW_RESPONSE =
    """
    {
      "globalObjects": {
        "users": {
          "reposter": {
            "name": "Reposter",
            "screen_name": "reposter",
            "verified": false,
            "ext_is_blue_verified": true
          },
          "original-author": {
            "name": "Original Author",
            "screen_name": "original_author",
            "verified": false,
            "ext_is_blue_verified": true
          },
          "viewer": {
            "name": "Viewer",
            "screen_name": "viewer",
            "blocking": null,
            "profile_banner_url": {"invalid": true}
          }
        },
        "tweets": {
          "retweet-wrapper": {
            "created_at": "Wed Jul 29 00:00:00 +0000 2026",
            "display_text_range": [0, 31],
            "entities": {},
            "favorite_count": 0,
            "favorited": false,
            "full_text": "RT @original_author: original",
            "id_str": "retweet-wrapper",
            "is_quote_status": false,
            "lang": "en",
            "quote_count": 0,
            "reply_count": 0,
            "retweet_count": 0,
            "retweeted": false,
            "retweeted_status_id_str": "original-tweet",
            "user_id_str": "reposter"
          },
          "original-tweet": {
            "created_at": "Wed Jul 29 00:00:00 +0000 2026",
            "display_text_range": [0, 16],
            "entities": {},
            "favorite_count": 10,
            "favorited": false,
            "full_text": "original content",
            "id_str": "original-tweet",
            "is_quote_status": false,
            "lang": "en",
            "quote_count": 2,
            "reply_count": 3,
            "retweet_count": 3060,
            "retweeted": true,
            "user_id_str": "original-author"
          }
        }
      },
      "timeline": {
        "instructions": [
          {
            "addEntries": {
              "entries": [
                {
                  "entryId": "tweet-retweet-wrapper",
                  "sortIndex": "100",
                  "content": {
                    "item": {
                      "content": {
                        "tweet": {
                          "id": "retweet-wrapper",
                          "displayType": "Tweet"
                        }
                      }
                    }
                  }
                }
              ]
            }
          }
        ]
      }
    }
    """.trimIndent()
