package dev.dimension.flare.feature.plugin.wire

import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertContains
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
}
