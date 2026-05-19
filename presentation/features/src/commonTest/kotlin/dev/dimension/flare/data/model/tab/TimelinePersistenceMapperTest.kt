package dev.dimension.flare.data.model.tab

import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.datasource.microblog.timeline.AccountTimelineSpec
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineCatalog
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineDisplay
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineTabDescriptor
import dev.dimension.flare.data.datasource.microblog.timeline.toTimelineTabDescriptor
import dev.dimension.flare.data.datasource.rss.RssTimelineSpecs
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.defaultSocialPlatformRegistry
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TimelinePersistenceMapperTest {
    private val homeSpec =
        AccountTimelineSpec(
            id = "common.home",
            title = UiStrings.Home,
            icon = UiIcon.Home.asType(),
            serializer = dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec.AccountBasedData.serializer(),
            stableKeyFactory = { it.accountKey.toString() },
            loaderFactory = { _, _ -> notSupported() },
        )

    private val mapper =
        TimelinePersistenceMapper(
            catalog = TimelineCatalog(listOf(homeSpec) + RssTimelineSpecs.timelineSpecs),
            timelineResolver = TimelineResolver(defaultSocialPlatformRegistry),
        )

    @Test
    fun sourceDescriptorProjectsToStorageSlotWithoutDatasourceStorageTypes() {
        val accountKey = MicroBlogKey("home", "example.com")
        val descriptor =
            TimelineTabDescriptor.Source(
                ref =
                    homeSpec.ref(
                        dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec.AccountBasedData(accountKey),
                    ),
                display =
                    TimelineDisplay(
                        title = UiText.Raw("Home"),
                        icon = IconType.FavIcon(accountKey.host),
                    ),
            )

        val slot = mapper.toSlot(descriptor)
        val source = assertIs<TimelineSlotContent.Source>(slot.content).source

        assertEquals("common.home:$accountKey", slot.id)
        assertEquals(slot.id, source.id)
        assertEquals("common.home", source.specId)
        assertEquals(UiText.Raw("Home"), source.title)
        assertEquals(IconType.FavIcon(accountKey.host), source.icon)
    }

    @Test
    fun sourceSlotRoundTripsThroughCurrentTimelineItemBridge() {
        val accountKey = MicroBlogKey("home", "example.com")
        val descriptor =
            TimelineTabDescriptor.Source(
                ref =
                    homeSpec.ref(
                        dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec.AccountBasedData(accountKey),
                    ),
                display =
                    TimelineDisplay(
                        title = UiText.Raw("Home"),
                        icon = IconType.FavIcon(accountKey.host),
                    ),
            )
        val slot = mapper.toSlot(descriptor)

        val item = mapper.toTabItem(slot)
        val persistedAgain = mapper.toSlot(item)

        assertIs<SourceTimelineTabItemV2>(item)
        assertEquals(slot, persistedAgain)
    }

    @Test
    fun rssSourceSlotDecodesToTypedRuntimeRef() {
        val descriptor =
            RssTimelineSpecs.rss.toTimelineTabDescriptor(
                data = RssTimelineSpecs.RssData("https://example.com/feed.xml"),
                title = UiText.Raw("Example"),
                icon = IconType.Material(UiIcon.Rss),
            )
        val slot = mapper.toSlot(descriptor)

        val item = assertIs<SourceTimelineTabItemV2>(mapper.toTabItem(slot))

        assertEquals(descriptor.ref, item.ref)
    }
}
