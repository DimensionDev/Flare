package dev.dimension.flare.data.model.tab

import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.datasource.microblog.timeline.CommonTimelineSpecs
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineCatalog
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec
import dev.dimension.flare.data.datasource.rss.RssTimelineSpecs
import dev.dimension.flare.data.platform.BlueskyTimelineSpecs
import dev.dimension.flare.data.platform.MastodonTimelineSpecs
import dev.dimension.flare.data.platform.MisskeyTimelineSpecs
import dev.dimension.flare.data.platform.VvoTimelineSpecs
import dev.dimension.flare.data.platform.XqtTimelineSpecs
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.defaultSocialPlatformRegistry
import dev.dimension.flare.ui.model.asText
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineCatalogMigrationCompatibilityTest {
    private val accountKey = MicroBlogKey(id = "alice", host = "example.com")
    private val catalog =
        TimelineCatalog(
            defaultSocialPlatformRegistry.specs.flatMap { it.timelineSpecs } + RssTimelineSpecs.timelineSpecs,
        )
    private val mapper = TimelinePersistenceMapper(catalog)

    @Test
    fun legacyMigrationTimelineRefsDecodeWithDefaultCatalog() {
        val refs =
            listOf(
                sourceRef(CommonTimelineSpecs.home, TimelineSpec.AccountBasedData(accountKey)),
                sourceRef(CommonTimelineSpecs.discover, TimelineSpec.AccountBasedData(accountKey)),
                sourceRef(CommonTimelineSpecs.list, TimelineSpec.AccountResourceData(accountKey, "list-1")),
                sourceRef(MastodonTimelineSpecs.local, TimelineSpec.AccountBasedData(accountKey)),
                sourceRef(MastodonTimelineSpecs.federated, TimelineSpec.AccountBasedData(accountKey)),
                sourceRef(MastodonTimelineSpecs.bookmark, TimelineSpec.AccountBasedData(accountKey)),
                sourceRef(MastodonTimelineSpecs.favourite, TimelineSpec.AccountBasedData(accountKey)),
                sourceRef(MisskeyTimelineSpecs.local, TimelineSpec.AccountBasedData(accountKey)),
                sourceRef(MisskeyTimelineSpecs.global, TimelineSpec.AccountBasedData(accountKey)),
                sourceRef(MisskeyTimelineSpecs.hybrid, TimelineSpec.AccountBasedData(accountKey)),
                sourceRef(MisskeyTimelineSpecs.favourite, TimelineSpec.AccountBasedData(accountKey)),
                sourceRef(MisskeyTimelineSpecs.antenna, TimelineSpec.AccountResourceData(accountKey, "antenna-1")),
                sourceRef(MisskeyTimelineSpecs.channel, TimelineSpec.AccountResourceData(accountKey, "channel-1")),
                sourceRef(XqtTimelineSpecs.featured, TimelineSpec.AccountBasedData(accountKey)),
                sourceRef(XqtTimelineSpecs.bookmark, TimelineSpec.AccountBasedData(accountKey)),
                sourceRef(XqtTimelineSpecs.deviceFollow, TimelineSpec.AccountBasedData(accountKey)),
                sourceRef(BlueskyTimelineSpecs.feed, TimelineSpec.AccountResourceData(accountKey, "at://feed")),
                sourceRef(BlueskyTimelineSpecs.bookmark, TimelineSpec.AccountBasedData(accountKey)),
                sourceRef(VvoTimelineSpecs.favorite, TimelineSpec.AccountBasedData(accountKey)),
                sourceRef(VvoTimelineSpecs.liked, TimelineSpec.AccountBasedData(accountKey)),
                sourceRef(RssTimelineSpecs.rss, RssTimelineSpecs.RssData("https://example.com/feed.xml")),
                sourceRef(RssTimelineSpecs.allRss, RssTimelineSpecs.AllRssData),
                sourceRef(
                    RssTimelineSpecs.subscription,
                    RssTimelineSpecs.SubscriptionData(
                        subscriptionUrl = "https://mastodon.example/public",
                        subscriptionType = SubscriptionType.MASTODON_PUBLIC,
                    ),
                ),
            )

        assertEquals(
            listOf(
                "common.home",
                "common.discover",
                "common.list",
                "mastodon.local",
                "mastodon.public",
                "mastodon.bookmark",
                "mastodon.favourite",
                "misskey.local",
                "misskey.global",
                "misskey.hybrid",
                "misskey.favourite",
                "misskey.antenna",
                "misskey.channel",
                "xqt.featured",
                "xqt.bookmark",
                "xqt.device_follow",
                "bluesky.feed",
                "bluesky.bookmark",
                "vvo.favorite",
                "vvo.liked",
                "rss.feed",
                "rss.all",
                "rss.subscription",
            ),
            refs.map { it.specId },
        )
        refs.forEach { ref ->
            assertEquals(ref.specId, mapper.decode(ref).spec.id)
        }
    }

    private fun <T : TimelineSpec.Data> sourceRef(
        spec: TimelineSpec<T>,
        data: T,
    ): TimelineSourceRef {
        val encoded = catalog.encode(spec.ref(data))
        return TimelineSourceRef(
            id = "${encoded.specId}:${encoded.stableKey}",
            specId = encoded.specId,
            title = spec.title.asText(),
            icon = spec.icon,
            data = encoded.data,
        )
    }
}
