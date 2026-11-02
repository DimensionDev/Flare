package dev.dimension.flare.feature.plugin.wire

import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class WireCompatibilityV1Test {
    private val post =
        PostV1(
            key = EntityKeyV1("post", "example.social"),
            createdAt = "2026-11-02T00:00:00Z",
            content = RichTextV1(value = "Preview"),
        )
    private val payload get() = PluginJsonV1.encodeToJsonElement(post).jsonObject

    @Test
    fun capabilityOperationsKeepSetSemanticsWhileLimitingDistinctNames() {
        val success =
            LoginSuccessV1(
                accountId = "me",
                origin = "https://example.social",
                credential = JsonObject(emptyMap()),
                profile = ProfileV1(EntityKeyV1("me", "example.social"), "me", "Me"),
                capabilities = mapOf(PluginAbiV1.Capabilities.POST to setOf("detail")),
            )
        val value = PluginJsonV1.encodeToJsonElement(success).jsonObject
        val repeated =
            JsonObject(
                value + (
                    "capabilities" to
                        JsonObject(
                            mapOf(
                                PluginAbiV1.Capabilities.POST to JsonArray(List(33) { JsonPrimitive("detail") }),
                            ),
                        )
                ),
            )
        val decoded = PluginJsonV1.decodeFromJsonElement<LoginSuccessV1>(repeated)
        decoded.requireValid()
        assertEquals(success, decoded)
        val excessive =
            JsonObject(
                value + (
                    "capabilities" to
                        JsonObject(
                            mapOf(
                                PluginAbiV1.Capabilities.POST to JsonArray(List(33) { JsonPrimitive("future$it") }),
                            ),
                        )
                ),
            )
        assertFailsWith<IllegalArgumentException> { PluginJsonV1.decodeFromJsonElement<LoginSuccessV1>(excessive) }
    }

    @Test
    fun unknownFieldsAreIgnoredAtEveryKnownObject() {
        val value =
            JsonObject(
                payload +
                    mapOf(
                        "future" to JsonObject(mapOf("nested" to JsonNull)),
                        "content" to JsonObject(mapOf("value" to JsonPrimitive("Preview"), "future" to JsonPrimitive(true))),
                    ),
            )
        assertEquals(post, PluginJsonV1.decodeFromJsonElement<PostV1>(value))
    }

    @Test
    fun unknownTimelineAndNavigationTypesDoNotRequireTheirPayloads() {
        val future = PluginJsonV1.parseToJsonElement("""{"type":"novel","future":{"content":[1,2]}}""")
        assertEquals(TimelineItemV1.Unknown, PluginJsonV1.decodeFromJsonElement<TimelineItemV1>(future))
        assertEquals(NavigationTargetV1.Unknown, PluginJsonV1.decodeFromJsonElement<NavigationTargetV1>(future))
    }

    @Test
    fun missingOrMalformedDiscriminatorsAndMalformedKnownVariantsStillFail() {
        listOf("{}", "[]", "null", """{"type":null}""", """{"type":12}""", """{"type":"post"}""").forEach { value ->
            val element = PluginJsonV1.parseToJsonElement(value)
            assertFailsWith<SerializationException>(value) { PluginJsonV1.decodeFromJsonElement<TimelineItemV1>(element) }
            assertFailsWith<SerializationException>(value) { PluginJsonV1.decodeFromJsonElement<NavigationTargetV1>(element) }
        }
    }

    @Test
    fun unknownMediaAndActionsKeepKnownSiblings() {
        val value =
            PluginJsonV1.decodeFromJsonElement<PostV1>(
                JsonObject(
                    payload +
                        mapOf(
                            "media" to
                                PluginJsonV1.parseToJsonElement(
                                    """[{"type":"spatial","url":{},"id":[]},{"id":"cover","type":"image","url":"https://example.social/cover.jpg"}]""",
                                ),
                            "actions" to
                                PluginJsonV1.parseToJsonElement(
                                    """[{"action":"ReadNovel","enabled":{},"count":-1},{"action":"Favourite","count":2}]""",
                                ),
                        ),
                ),
            )
        value.requireValid()
        assertEquals(listOf(MediaTypeV1.Unknown, MediaTypeV1.Image), value.media.map { it.type })
        assertEquals(listOf(ActionDescriptorV1(SemanticActionV1.Favourite, count = 2)), value.actions)
    }

    @Test
    fun optionalMediaAndActionTokensIgnoreUnknownTypes() {
        val feed =
            PluginJsonV1.decodeFromString<FeedV1>(
                """{"url":"https://example.social/feed","createdAt":"2026-11-02T00:00:00Z","source":{"name":"Feed"},"media":{"type":"spatial"}}""",
            )
        feed.requireValid()
        assertEquals(MediaTypeV1.Unknown, feed.media?.type)
        val card = PluginJsonV1.decodeFromString<CardV1>("""{"title":"Card","url":"https://example.social/","media":null}""")
        assertEquals(null, card.media)
        val relation =
            PluginJsonV1.decodeFromString<RelationV1>(
                """{"profileKey":{"id":"user","host":"example.social"},"actionTokens":{"Follow":"token","ReadNovel":{}}}""",
            )
        relation.requireValid()
        assertEquals(mapOf(SemanticActionV1.Follow to "token"), relation.actionTokens)
    }

    @Test
    fun compatibilityDoesNotHideKnownValidationFailures() {
        val insecure =
            PluginJsonV1.decodeFromJsonElement<PostV1>(
                JsonObject(
                    payload +
                        ("media" to PluginJsonV1.parseToJsonElement("""[{"type":"image","id":"cover","url":"http://example.social/"}]""")),
                ),
            )
        assertFailsWith<IllegalArgumentException> { insecure.requireValid() }
        val negativeCount =
            PluginJsonV1.decodeFromJsonElement<PostV1>(
                JsonObject(payload + ("actions" to PluginJsonV1.parseToJsonElement("""[{"action":"Favourite","count":-1}]"""))),
            )
        assertFailsWith<IllegalArgumentException> { negativeCount.requireValid() }
        listOf("{}", """{"type":null}""", """{"type":true}""", """{"type":"image"}""").forEach { media ->
            assertFailsWith<SerializationException>(media) {
                PluginJsonV1.decodeFromJsonElement<PostV1>(
                    JsonObject(payload + ("media" to JsonArray(listOf(PluginJsonV1.parseToJsonElement(media))))),
                )
            }
        }
    }

    @Test
    fun ignoredItemsStillCountTowardWireLimits() {
        val unknown = PluginJsonV1.parseToJsonElement("""{"type":"spatial"}""")
        val value = PluginJsonV1.decodeFromJsonElement<PostV1>(JsonObject(payload + ("media" to JsonArray(List(101) { unknown }))))
        assertFailsWith<IllegalArgumentException> { value.requireValid() }
        assertFailsWith<IllegalArgumentException> {
            PageV1(items = List(WireLimitsV1.MAX_PAGE_ITEMS + 1) { TimelineItemV1.Unknown }).requireValid { it.requireValid() }
        }
    }

    @Test
    fun unknownOperationResultsAreNeverTreatedAsSuccess() {
        val future = PluginJsonV1.parseToJsonElement("""{"type":"futureSuccess"}""")
        assertFailsWith<SerializationException> { PluginJsonV1.decodeFromJsonElement<MutationResultV1>(future) }
        assertFailsWith<SerializationException> { PluginJsonV1.decodeFromJsonElement<LoginTransitionV1>(future) }
    }

    @Test
    fun knownTimelineAndNavigationDiscriminatorsStillRoundTrip() {
        val timeline = TimelineItemV1.Post(post)
        assertEquals(
            timeline,
            PluginJsonV1.decodeFromJsonElement<TimelineItemV1>(PluginJsonV1.encodeToJsonElement<TimelineItemV1>(timeline)),
        )
        val target = NavigationTargetV1.Link("https://example.social/post")
        assertIs<NavigationTargetV1.Link>(
            PluginJsonV1.decodeFromJsonElement<NavigationTargetV1>(PluginJsonV1.encodeToJsonElement<NavigationTargetV1>(target)),
        )
    }
}
