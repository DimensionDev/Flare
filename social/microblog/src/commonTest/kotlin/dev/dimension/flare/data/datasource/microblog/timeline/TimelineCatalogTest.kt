package dev.dimension.flare.data.datasource.microblog.timeline

import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asType
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class TimelineCatalogTest {
    @Test
    fun encodeAndDecodeRoundTripTypedData() {
        val spec = FakeTimelineSpec(id = "fake.timeline")
        val catalog = TimelineCatalog(listOf(spec))
        val ref = spec.ref(FakeData("home"))

        val encoded = catalog.encode(ref)
        val decoded = catalog.decode(encoded.specId, encoded.data)

        assertEquals("fake.timeline", encoded.specId)
        assertEquals("home", encoded.stableKey)
        assertSame(spec, decoded.spec)
        assertEquals(FakeData("home"), decoded.data)
    }

    @Test
    fun duplicateSpecIdsKeepFirstRegistration() {
        val first = FakeTimelineSpec(id = "duplicate")
        val second = FakeTimelineSpec(id = "duplicate")
        val catalog = TimelineCatalog(listOf(first, second))

        assertSame(first, catalog.requireSpec("duplicate"))
        assertEquals(1, catalog.specs.size)
    }

    @Test
    fun unknownSpecIdFailsClearly() {
        val catalog = TimelineCatalog(emptyList())

        assertFailsWith<IllegalArgumentException> {
            catalog.requireSpec("missing")
        }
    }

    private class FakeTimelineSpec(
        override val id: String,
    ) : TimelineSpec<FakeData> {
        override val title: UiStrings = UiStrings.Home
        override val icon: IconType = UiIcon.Home.asType()
        override val serializer = FakeData.serializer()

        override fun stableKey(data: FakeData): String = data.value
    }

    @Serializable
    private data class FakeData(
        val value: String,
    ) : TimelineSpec.Data
}
