package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.database.cache.mapper.cursor
import dev.dimension.flare.data.network.xqt.model.CursorType
import dev.dimension.flare.data.network.xqt.model.InstructionUnion
import dev.dimension.flare.data.network.xqt.model.ItemResult
import dev.dimension.flare.data.network.xqt.model.ModuleEntry
import dev.dimension.flare.data.network.xqt.model.ModuleItem
import dev.dimension.flare.data.network.xqt.model.NotificationsTimelineResponse
import dev.dimension.flare.data.network.xqt.model.TimelineAddEntries
import dev.dimension.flare.data.network.xqt.model.TimelineAddEntry
import dev.dimension.flare.data.network.xqt.model.TimelineNotification
import dev.dimension.flare.data.network.xqt.model.TimelineNotificationTweetRef
import dev.dimension.flare.data.network.xqt.model.TimelineTimelineItem
import dev.dimension.flare.data.network.xqt.model.TimelineTimelineModule
import dev.dimension.flare.data.network.xqt.model.TimelineTweet
import dev.dimension.flare.data.network.xqt.model.Tweet
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.asTimelinePostItem
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.plugin.module.dsl.modules
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class XQTNotificationMapperTest {
    private val accountKey = MicroBlogKey(id = "me", host = "x.com")

    @BeforeTest
    fun setup() {
        startKoin {
            modules(XqtRenderTestModule::class)
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
        assertEquals("follow-notification", followedUser.statusKey.id)
        assertEquals("like-notification", likedPost.presentation.notificationKey?.id)
    }

    @Test
    fun sampleKeepsSixteenNotificationEntriesInApiOrder() {
        // Same entry types, repeated targets and reply relationships as the supplied response.
        val specification =
            listOf(
                Triple("bell_icon", null, null),
                Triple("person_icon", null, null),
                Triple(null, "reply-1", "liked-reply"),
                Triple("heart_icon", "liked-reply", "reply-2"),
                Triple(null, "reply-2", "reply-3"),
                Triple(null, "reply-3", "missing-parent"),
                Triple(null, "reply-4", "reply-5"),
                Triple(null, "reply-5", "root"),
                Triple("person_icon", null, null),
                Triple("heart_icon", "root", null),
                Triple("retweet_icon", "same-post", null),
                Triple("heart_icon", "same-post", null),
                Triple("milestone_icon", null, null),
                Triple("person_icon", null, null),
                Triple("person_icon", null, null),
                Triple("heart_icon", "other-post", null),
            )
        val base = baseNotification()
        val target =
            assertIs<Tweet>(
                base.template.targetObjects!!
                    .first()
                    .tweetResults!!
                    .result,
            )
        val entries =
            specification.mapIndexed { index, (icon, postId, parentId) ->
                val id = "notification-$index"
                val tweet =
                    postId?.let {
                        target.copy(restId = it, legacy = target.legacy!!.copy(idStr = it, in_reply_to_status_id_str = parentId))
                    }
                val content =
                    if (icon == null) {
                        TimelineTweet(tweetResults = ItemResult(result = tweet))
                    } else {
                        base.copy(
                            id = id,
                            notificationIcon = icon,
                            template =
                                base.template.copy(
                                    targetObjects = listOfNotNull(tweet?.let { TimelineNotificationTweetRef(ItemResult(result = it)) }),
                                ),
                        )
                    }
                TimelineAddEntry(TimelineTimelineItem(content), id, (100 - index).toString())
            }
        val items = listOf<InstructionUnion>(TimelineAddEntries(entries)).renderNotifications(accountKey)

        assertEquals(16, items.size)
        assertEquals(
            entries.map { it.entryId },
            items.map {
                it
                    .asTimelinePostItem()
                    ?.presentation
                    ?.notificationKey
                    ?.id ?: it.statusKey.id
            },
        )
        assertTrue(items.mapNotNull { it.asTimelinePostItem() }.all { it.presentation.inlineParents.isEmpty() })
        assertEquals(items[10].statusKey, items[11].statusKey)
        assertNotEquals(
            items[10].asTimelinePostItem()?.presentation?.notificationKey,
            items[11].asTimelinePostItem()?.presentation?.notificationKey,
        )
    }

    @Test
    fun notificationKeepsQuoteEmbeddedInApiResponse() {
        val base = baseNotification()
        val target =
            assertIs<Tweet>(
                base.template.targetObjects!!
                    .first()
                    .tweetResults!!
                    .result,
            )
        val quote = target.copy(restId = "quote", legacy = target.legacy!!.copy(idStr = "quote"))
        val item =
            base.copy(
                template =
                    base.template.copy(
                        targetObjects =
                            listOf(
                                TimelineNotificationTweetRef(
                                    ItemResult(result = target.copy(quotedStatusResult = ItemResult(result = quote))),
                                ),
                            ),
                    ),
            )
        val rendered =
            listOf<InstructionUnion>(
                TimelineAddEntries(
                    listOf(
                        TimelineAddEntry(TimelineTimelineItem(item), "like-notification", "1"),
                    ),
                ),
            ).renderNotifications(accountKey).single()

        assertEquals(listOf("quote"), assertIs<UiTimelineV2.TimelinePostItem>(rendered).presentation.quotes.map { it.statusKey.id })
    }

    private fun baseNotification(): TimelineNotification {
        val instructions =
            GRAPHQL_NOTIFICATIONS
                .decodeJson<NotificationsTimelineResponse>()
                .data.viewerV2.userResults.result.notificationTimeline.timeline!!
                .instructions
        val entry = assertIs<TimelineAddEntries>(instructions.single()).propertyEntries[1]
        return assertIs<TimelineNotification>(assertIs<TimelineTimelineItem>(entry.content).itemContent)
    }

    @Test
    fun notificationKeepsAnExplicitApiConversationModule() {
        val target =
            assertIs<Tweet>(
                baseNotification()
                    .template.targetObjects!!
                    .first()
                    .tweetResults!!
                    .result,
            )
        val parent = target.copy(restId = "parent", legacy = target.legacy!!.copy(idStr = "parent", conversationIdStr = "parent"))
        val reply =
            target.copy(
                restId = "reply",
                legacy =
                    target.legacy.copy(
                        idStr = "reply",
                        conversationIdStr = "parent",
                        in_reply_to_status_id_str = "parent",
                    ),
            )
        val module =
            TimelineTimelineModule(
                items =
                    listOf(parent, reply).map {
                        ModuleItem("module-${it.restId}", ModuleEntry(TimelineTweet(tweetResults = ItemResult(result = it))))
                    },
            )
        val items =
            listOf<InstructionUnion>(
                TimelineAddEntries(
                    listOf(
                        TimelineAddEntry(module, "notification-module", "1"),
                    ),
                ),
            ).renderNotifications(accountKey)

        val rendered = assertIs<UiTimelineV2.TimelinePostItem>(items.single())
        assertEquals("reply", rendered.statusKey.id)
        assertEquals(listOf("parent"), rendered.presentation.inlineParents.map { it.statusKey.id })
        assertEquals("notification-module:reply", rendered.presentation.notificationKey?.id)
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
