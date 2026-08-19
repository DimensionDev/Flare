package dev.dimension.flare.feature.plugin.wire

import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WireV1Test {
    @Test
    fun mutationResultHasStableDiscriminator() {
        val encoded =
            PluginJsonV1.encodeToString<MutationResultV1>(
                MutationResultV1.Invalidate(listOf(EntityKeyV1("42", "example.social"))),
            )

        assertContains(encoded, "\"type\":\"invalidate\"")
    }

    @Test
    fun rejectsOversizedCursor() {
        assertFailsWith<IllegalArgumentException> {
            PageRequestV1(
                direction = PageDirectionV1.Older,
                limit = 20,
                cursor = "x".repeat(WireLimitsV1.MAX_CURSOR_LENGTH + 1),
            ).requireValid()
        }
    }

    @Test
    fun rejectsDefaultVisibilityOutsideAllowedSet() {
        assertFailsWith<IllegalArgumentException> {
            ComposeConfigV1(
                visibility =
                    ComposeConfigV1.VisibilityConfigV1(
                        allowed = setOf(VisibilityV1.Followers),
                        default = VisibilityV1.Public,
                    ),
            ).requireValid()
        }
    }

    @Test
    fun rejectsInvalidNestedPageItem() {
        assertFailsWith<IllegalArgumentException> {
            PageV1(
                items = listOf(validPost().copy(createdAt = "not-a-timestamp")),
            ).requireValid(PostV1::requireValid)
        }
    }

    @Test
    fun rejectsInsecureUrlsAndNegativeCounts() {
        assertFailsWith<IllegalArgumentException> {
            validProfile().copy(avatarUrl = "http://example.social/avatar.png").requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            validPost().copy(favouritesCount = -1).requireValid()
        }
    }

    @Test
    fun rejectsInvalidNestedMutationEntity() {
        assertFailsWith<IllegalArgumentException> {
            MutationResultV1
                .UpdatedPost(validPost().copy(url = "https://user:password@example.social/post/1"))
                .requireValid()
        }
    }

    @Test
    fun mutationValidationIsScopedToTheRequestedEntityAndOperation() {
        val expectedPost = EntityKeyV1("post-1", "example.social")
        assertFailsWith<IllegalArgumentException> {
            MutationResultV1
                .UpdatedPost(validPost().copy(key = EntityKeyV1("post-2", "example.social")))
                .requirePostMutationResult(expectedPost)
        }
        assertFailsWith<IllegalArgumentException> {
            MutationResultV1
                .Invalidate(listOf(EntityKeyV1("post-2", "example.social")))
                .requirePostMutationResult(expectedPost)
        }
        assertFailsWith<IllegalArgumentException> {
            MutationResultV1.UpdatedPost(validPost()).requireDeleteResult()
        }
        assertFailsWith<IllegalArgumentException> {
            MutationResultV1
                .UpdatedRelation(RelationV1(EntityKeyV1("profile-2", "example.social")))
                .requireRelationMutationResult(EntityKeyV1("profile-1", "example.social"))
        }
    }

    @Test
    fun rejectsInvalidNotificationAndDirectMessageTimestamps() {
        assertFailsWith<IllegalArgumentException> {
            NotificationV1(
                id = "notification-1",
                createdAt = "yesterday",
                kind = NotificationKindV1.Mention,
            ).requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            DirectMessageV1(
                key = EntityKeyV1("message-1", "example.social"),
                roomKey = EntityKeyV1("room-1", "example.social"),
                sender = validProfile(),
                createdAt = "tomorrow",
                content = RichTextV1(value = "Hello"),
            ).requireValid()
        }
    }

    @Test
    fun accountCredentialEnvelopeRoundTrips() {
        val value =
            PluginAccountCredentialV1(
                snapshot =
                    AccountPluginSnapshotV1(
                        pluginId = "dev.dimension.flare.test",
                        platformId = "Test",
                        packageHash = "a".repeat(64),
                        origin = "https://example.social",
                        accountId = "me",
                        manifestCapabilities = mapOf("flare.datasource.timeline/v1" to setOf("page")),
                        negotiatedCapabilities = mapOf("flare.datasource.timeline/v1" to setOf("page")),
                        credentialSchemaVersion = 1,
                        timelineSchemaVersion = 1,
                    ),
                credential = kotlinx.serialization.json.JsonObject(emptyMap()),
            )

        assertEquals(
            value,
            PluginJsonV1.decodeFromString(
                PluginAccountCredentialV1.serializer(),
                PluginJsonV1.encodeToString(PluginAccountCredentialV1.serializer(), value),
            ),
        )
    }

    @Test
    fun errorTextArgumentsUseJsonPrimitives() {
        val value =
            PluginJsonV1.decodeFromString<PluginErrorV1>(
                """{"code":"Validation","message":{"key":"error.detail","fallback":"Missing {item}","args":{"item":"photo","count":2,"active":true}}}""",
            )

        value.message.requireValid()
        assertContains(PluginJsonV1.encodeToString(value), "\"count\":2")
        assertFailsWith<IllegalArgumentException> {
            value.message.copy(args = mapOf("item" to JsonNull)).requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            WireTextV1(value = "   ").requireValid()
        }
    }

    private fun validProfile(): ProfileV1 =
        ProfileV1(
            key = EntityKeyV1("profile-1", "example.social"),
            handle = "user@example.social",
            displayName = "User",
            avatarUrl = "https://example.social/avatar.png",
        )

    private fun validPost(): PostV1 =
        PostV1(
            key = EntityKeyV1("post-1", "example.social"),
            author = validProfile(),
            createdAt = "2026-08-14T00:00:00Z",
            content = RichTextV1(value = "Hello"),
            url = "https://example.social/p/post-1",
        )
}
