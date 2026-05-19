package dev.dimension.flare.model

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.ui.model.UiAccount
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertIs

class DefaultSocialPlatformRegistryTest {
    @Test
    fun publicDefaultPluginsCreateDataSourcesForTheirAccounts() {
        val accounts =
            listOf(
                UiAccount.Mastodon(
                    accountKey = MicroBlogKey("mastodon-user", "mastodon.example"),
                    instance = "mastodon.example",
                ),
                UiAccount.Misskey(
                    accountKey = MicroBlogKey("misskey-user", "misskey.example"),
                    host = "misskey.example",
                ),
                UiAccount.Bluesky(
                    accountKey = MicroBlogKey("did:plc:test", "bsky.social"),
                ),
                UiAccount.XQT(
                    accountKey = MicroBlogKey("x-user", "x.com"),
                ),
                UiAccount.VVo(
                    accountKey = MicroBlogKey("weibo-user", "weibo.com"),
                ),
            )

        accounts.forEach { account ->
            assertIs<MicroblogDataSource>(defaultSocialPlatformRegistry.createDataSource(account))
        }
    }

    @Test
    fun publicDefaultPluginsExposeTimelineSpecsToCatalogAssembly() {
        val specIds = defaultSocialPlatformRegistry.specs.flatMap { it.timelineSpecs }.map { it.id }

        assertContains(specIds, "common.home")
        assertContains(specIds, "mastodon.local")
        assertContains(specIds, "misskey.hybrid")
        assertContains(specIds, "bluesky.feed")
        assertContains(specIds, "xqt.featured")
        assertContains(specIds, "vvo.favorite")
    }
}
