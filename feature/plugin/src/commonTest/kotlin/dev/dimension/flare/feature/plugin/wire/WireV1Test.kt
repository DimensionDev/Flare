package dev.dimension.flare.feature.plugin.wire

import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import kotlinx.serialization.encodeToString
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
}
