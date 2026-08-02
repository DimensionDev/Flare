package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.TestFormatter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.network.xqt.model.CursorType
import dev.dimension.flare.data.network.xqt.model.NotificationsTimelineResponse
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.PlatformFormatter
import dev.dimension.flare.ui.model.UiTimelineV2
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class XQTNotificationMapperTest {
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
    fun graphqlNotificationsRenderTargetTweetsAndNormalizedUsers() {
        val response = GRAPHQL_NOTIFICATIONS.decodeJson<NotificationsTimelineResponse>()
        val instructions =
            response.data.viewerV2.userResults.result.notificationTimeline.timeline
                ?.instructions
                .orEmpty()

        assertEquals("top", instructions.cursor(CursorType.top))
        assertEquals("bottom", instructions.cursor())

        val items = instructions.renderNotifications(accountKey)
        assertEquals(2, items.size)

        val likedPost = assertIs<UiTimelineV2.TimelinePostItem>(items[0])
        assertEquals("post-1", likedPost.statusKey.id)
        assertEquals("Hello from target tweet", likedPost.post.content.original.innerText)
        assertEquals(
            "author",
            likedPost.post.user
                ?.key
                ?.id,
        )
        val likeMessage = assertIs<UiTimelineV2.Message.Type.Raw>(likedPost.presentation.message?.type)
        assertEquals("Actor liked your post", likeMessage.content)
        assertEquals(
            Instant.parse("2026-08-01T06:01:42.042Z"),
            likedPost.presentation.message
                ?.createdAt
                ?.value,
        )

        val followedUser = assertIs<UiTimelineV2.User>(items[1])
        assertEquals("actor", followedUser.value.key.id)
        assertEquals("Actor", followedUser.value.name.raw)
        assertEquals("actor_handle", followedUser.value.handle.raw)
    }
}

private val GRAPHQL_NOTIFICATIONS =
    """
    {
      "data": {
        "viewer_v2": {
          "user_results": {
            "result": {
              "__typename": "User",
              "notification_timeline": {
                "id": "timeline",
                "timeline": {
                  "instructions": [
                    {
                      "type": "TimelineAddEntries",
                      "entries": [
                        {
                          "content": {
                            "__typename": "TimelineTimelineCursor",
                            "entryType": "TimelineTimelineCursor",
                            "cursorType": "Top",
                            "value": "top"
                          },
                          "entryId": "cursor-top",
                          "sortIndex": "4"
                        },
                        {
                          "content": {
                            "__typename": "TimelineTimelineItem",
                            "entryType": "TimelineTimelineItem",
                            "itemContent": {
                              "__typename": "TimelineNotification",
                              "id": "like-notification",
                              "itemType": "TimelineNotification",
                              "notification_icon": "heart_icon",
                              "notification_url": {
                                "url": "/2/notifications/view/like-notification.json",
                                "urlType": "UrtEndpoint",
                                "urtEndpointOptions": {
                                  "cacheId": "like-notification",
                                  "title": "Liked"
                                }
                              },
                              "rich_message": {
                                "rtl": false,
                                "text": "Actor liked your post"
                              },
                              "template": {
                                "__typename": "TimelineNotificationAggregateUserActions",
                                "from_users": [
                                  {
                                    "__typename": "TimelineNotificationUserRef",
                                    "user_results": {
                                      "result": {
                                        "__typename": "User",
                                        "rest_id": "actor",
                                        "avatar": {"image_url": "https://example.com/actor.jpg"},
                                        "core": {
                                          "created_at": "Wed Oct 10 20:19:24 +0000 2018",
                                          "name": "Actor",
                                          "screen_name": "actor_handle"
                                        }
                                      }
                                    }
                                  }
                                ],
                                "target_objects": [
                                  {
                                    "__typename": "TimelineNotificationTweetRef",
                                    "tweet_results": {
                                      "result": {
                                        "__typename": "Tweet",
                                        "rest_id": "post-1",
                                        "core": {
                                          "user_results": {
                                            "result": {
                                              "__typename": "User",
                                              "rest_id": "author",
                                              "avatar": {"image_url": "https://example.com/author.jpg"},
                                              "core": {
                                                "created_at": "Wed Oct 10 20:19:24 +0000 2018",
                                                "name": "Author",
                                                "screen_name": "author_handle"
                                              }
                                            }
                                          }
                                        },
                                        "legacy": {
                                          "created_at": "Wed Oct 10 20:19:24 +0000 2018",
                                          "display_text_range": [0, 23],
                                          "entities": {},
                                          "favorite_count": 0,
                                          "favorited": false,
                                          "full_text": "Hello from target tweet",
                                          "id_str": "post-1",
                                          "is_quote_status": false,
                                          "lang": "en",
                                          "quote_count": 0,
                                          "reply_count": 0,
                                          "retweet_count": 0,
                                          "retweeted": false,
                                          "user_id_str": "author"
                                        }
                                      }
                                    }
                                  }
                                ]
                              },
                              "timestamp_ms": "2026-08-01T06:01:42.042Z"
                            }
                          },
                          "entryId": "notification-like",
                          "sortIndex": "3"
                        },
                        {
                          "content": {
                            "__typename": "TimelineTimelineItem",
                            "entryType": "TimelineTimelineItem",
                            "itemContent": {
                              "__typename": "TimelineNotification",
                              "id": "follow-notification",
                              "itemType": "TimelineNotification",
                              "notification_icon": "person_icon",
                              "notification_url": {
                                "url": "https://x.com/actor_handle",
                                "urlType": "ExternalUrl"
                              },
                              "rich_message": {
                                "rtl": false,
                                "text": "Actor followed you"
                              },
                              "template": {
                                "__typename": "TimelineNotificationAggregateUserActions",
                                "from_users": [
                                  {
                                    "__typename": "TimelineNotificationUserRef",
                                    "user_results": {
                                      "result": {
                                        "__typename": "User",
                                        "rest_id": "actor",
                                        "avatar": {"image_url": "https://example.com/actor.jpg"},
                                        "core": {
                                          "created_at": "Wed Oct 10 20:19:24 +0000 2018",
                                          "name": "Actor",
                                          "screen_name": "actor_handle"
                                        }
                                      }
                                    }
                                  }
                                ],
                                "target_objects": []
                              },
                              "timestamp_ms": "2026-08-01T05:01:42.042Z"
                            }
                          },
                          "entryId": "notification-follow",
                          "sortIndex": "2"
                        },
                        {
                          "content": {
                            "__typename": "TimelineTimelineCursor",
                            "entryType": "TimelineTimelineCursor",
                            "cursorType": "Bottom",
                            "value": "bottom"
                          },
                          "entryId": "cursor-bottom",
                          "sortIndex": "1"
                        }
                      ]
                    }
                  ]
                }
              },
              "rest_id": "me"
            }
          }
        }
      }
    }
    """.trimIndent()
