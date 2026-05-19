package dev.dimension.flare.data.model.tab

import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.appearance.AppearanceBag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiText
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalSerializationApi::class)
class TimelineFilterConfigSerializationTest {
    @Test
    fun timelinePresentationRoundTripsFilterConfig() {
        val slot =
            TimelineSlot(
                id = "test",
                content =
                    TimelineSlotContent.Source(
                        TimelineSourceRef(
                            id = "source",
                            specId = "spec",
                            title = UiText.Raw("Timeline"),
                            icon = IconType.Material(UiIcon.Rss),
                            data = "encoded",
                        ),
                    ),
                presentation =
                    TimelinePresentation(
                        titleOverride = "Filtered timeline",
                        iconOverride = IconType.Material(UiIcon.Retweet),
                        enabled = false,
                        filterConfig =
                            TimelineFilterConfig(
                                excludedKinds = listOf(TimelinePostKind.Reply, TimelinePostKind.Repost),
                                excludedContents = listOf(TimelinePostContent.Image, TimelinePostContent.Video),
                            ),
                    ),
            )

        val decoded =
            ProtoBuf.decodeFromByteArray(
                TimelineSlot.serializer(),
                ProtoBuf.encodeToByteArray(TimelineSlot.serializer(), slot),
            )

        assertEquals(slot.presentation.filterConfig, decoded.presentation.filterConfig)
    }

    @Test
    fun timelinePresentationDecodesMissingFilterConfigAsDefault() {
        val bytes =
            ProtoBuf.encodeToByteArray(
                LegacyTimelinePresentation.serializer(),
                LegacyTimelinePresentation(
                    titleOverride = "Legacy timeline",
                    iconOverride = IconType.Material(UiIcon.Rss),
                    enabled = false,
                ),
            )

        val decoded = ProtoBuf.decodeFromByteArray(TimelinePresentation.serializer(), bytes)

        assertEquals(TimelineFilterConfig(), decoded.filterConfig)
        assertEquals("Legacy timeline", decoded.titleOverride)
        assertEquals(false, decoded.enabled)
    }
}

@Serializable
private data class LegacyTimelinePresentation(
    val titleOverride: String? = null,
    val iconOverride: IconType? = null,
    val appearanceOverride: AppearanceBag? = null,
    val enabled: Boolean = true,
)
