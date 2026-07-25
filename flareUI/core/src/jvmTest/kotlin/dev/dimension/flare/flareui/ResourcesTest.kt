package dev.dimension.flare.flareui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ResourcesTest {
    @Test
    fun derivesStablePlatformNames() {
        val key =
            FlareResourceKey(
                namespace = "profile.header",
                name = "avatar_placeholder",
            )

        assertEquals("profile.header.avatar_placeholder", key.qualifiedName)
        assertEquals("profile_header", key.platformNamespace)
        assertEquals("profile_header__avatar_placeholder", key.platformName)
    }

    @Test
    fun textContainsExactlyOneSource() {
        val resource =
            FlareStringResource(
                FlareResourceKey(
                    namespace = "profile",
                    name = "title",
                ),
            )

        val literal = FlareText.literal("Dynamic")
        assertEquals("Dynamic", literal.literal)
        assertNull(literal.resource)

        val referenced = FlareText.resource(resource)
        assertNull(referenced.literal)
        assertEquals(resource, referenced.resource)
    }

    @Test
    fun rejectsInvalidResourceNames() {
        assertFailsWith<IllegalArgumentException> {
            FlareResourceKey(
                namespace = "profile",
                name = "Invalid Name",
            )
        }
    }
}
