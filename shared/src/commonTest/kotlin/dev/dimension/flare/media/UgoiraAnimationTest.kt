package dev.dimension.flare.media

import dev.dimension.flare.common.decodeProtobuf
import dev.dimension.flare.common.encodeProtobuf
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiMedia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class UgoiraAnimationTest {
    @Test
    fun variableTimingIncludesLastFrameAndWrapsAtDuration() {
        val animation =
            UgoiraAnimation("test", listOf(UgoiraFrame("0.jpg", 40), UgoiraFrame("1.jpg", 80), UgoiraFrame("2.jpg", 100)), "original")
        assertEquals(220L, animation.durationMillis)
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 0, 1), listOf(0L, 39, 40, 119, 120, 219, 220, 260).map(animation::frameIndex))
        assertEquals(listOf(0L, 40, 120), (0..2).map(animation::frameStartMillis))
    }

    @Test
    fun rejectsUnsafeNamesDurationsAndDuplicateFrames() {
        for (frames in listOf(
            emptyList(),
            listOf(UgoiraFrame("../0.jpg", 20)),
            listOf(UgoiraFrame("0.jpg", 0)),
            listOf(UgoiraFrame("0.jpg", 20), UgoiraFrame("0.jpg", 40)),
        )) {
            assertFailsWith<IllegalArgumentException> { UgoiraMetadata("zip", frames).validate() }
        }
    }

    @Test
    fun descriptorRoundTripsWithoutLosingAccountOrOriginalSource() {
        val media: UiMedia =
            UiMedia.Ugoira(
                MicroBlogKey("123", "pixiv.net"),
                MicroBlogKey("reader", "pixiv.net"),
                "cover",
                "original",
                "Animation",
                300f,
                400f,
                false,
            )
        assertEquals(media, media.encodeProtobuf<UiMedia>().decodeProtobuf<UiMedia>())
        assertFailsWith<IllegalStateException> { media.urlForDownload }
    }
}
