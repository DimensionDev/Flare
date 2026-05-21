package dev.dimension.flare.common.deeplink

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.testPlatformRegistry
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformDeepLinkMatcherTest {
    @Test
    fun mastodonDeepLinksAreGeneratedInOrder() {
        val accountKey = MicroBlogKey(id = "1", host = "mastodon.social")

        val deepLinks = PlatformType.Mastodon.deepLinks(accountKey)

        assertEquals(2, deepLinks.size)
        assertEquals("https://mastodon.social/@{handle}", deepLinks[0].uriPattern)
        assertEquals("https://mastodon.social/@{handle}/{id}", deepLinks[1].uriPattern)
    }

    @Test
    fun misskeyDeepLinksUseNotesRoute() {
        val accountKey = MicroBlogKey(id = "1", host = "misskey.example")

        val deepLinks = PlatformType.Misskey.deepLinks(accountKey)

        assertEquals(2, deepLinks.size)
        assertEquals("https://misskey.example/@{handle}", deepLinks[0].uriPattern)
        assertEquals("https://misskey.example/notes/{id}", deepLinks[1].uriPattern)
    }

    @Test
    fun blueskyDeepLinksIncludeProfileAndPost() {
        val accountKey = MicroBlogKey(id = "1", host = "bsky.example")

        val deepLinks = PlatformType.Bluesky.deepLinks(accountKey)

        assertEquals(2, deepLinks.size)
        assertEquals("https://bsky.example/profile/{handle}", deepLinks[0].uriPattern)
        assertEquals("https://bsky.example/profile/{handle}/post/{id}", deepLinks[1].uriPattern)
    }

    @Test
    fun blueskySocialAccountIncludesBskyAppAlias() {
        val accountKey = MicroBlogKey(id = "1", host = "bsky.social")

        val deepLinks = PlatformType.Bluesky.deepLinks(accountKey)

        assertEquals(4, deepLinks.size)
        assertEquals("https://bsky.app/profile/{handle}", deepLinks[2].uriPattern)
        assertEquals("https://bsky.app/profile/{handle}/post/{id}", deepLinks[3].uriPattern)
    }

    @Test
    fun xqtDeepLinksUseStatusRoute() {
        val accountKey = MicroBlogKey(id = "1", host = xqtHost)

        val deepLinks = PlatformType.xQt.deepLinks(accountKey)

        assertEquals(12, deepLinks.size)
        assertEquals("https://$xqtHost/{handle}", deepLinks[0].uriPattern)
        assertEquals("https://$xqtHost/{handle}/status/{id}", deepLinks[4].uriPattern)
        assertEquals("https://$xqtHost/{handle}/status/{id}/photo/{index}", deepLinks[8].uriPattern)
    }

    @Test
    fun vvoHasNoDeepLinks() {
        val deepLinks = PlatformType.VVo.deepLinks(MicroBlogKey(id = "1", host = "irrelevant"))

        assertTrue(deepLinks.isEmpty())
    }

    @Test
    fun matchesReturnsAccountProfileRoute() {
        val mastodonAccount =
            UiAccount.Mastodon(
                accountKey = MicroBlogKey(id = "1", host = "mastodon.social"),
                instance = "mastodon.social",
            )
        val misskeyAccount =
            UiAccount.Misskey(
                accountKey = MicroBlogKey(id = "2", host = "misskey.example"),
                host = "misskey.example",
            )
        val mapping = deepLinkMapping(mastodonAccount, misskeyAccount)

        val matches = PlatformDeepLinkMatcher.matches("https://mastodon.social/@alice", mapping)

        assertEquals(1, matches.size)
        assertEquals(
            DeeplinkRoute.Profile.UserNameWithHost(
                accountType = AccountType.Specific(mastodonAccount.accountKey),
                userName = "alice",
                host = "mastodon.social",
            ),
            matches[mastodonAccount],
        )
    }

    @Test
    fun matchesMultipleAccountsWithSameHost() {
        val account1 =
            UiAccount.Mastodon(
                accountKey = MicroBlogKey(id = "1", host = "mastodon.social"),
                instance = "mastodon.social",
            )
        val account2 =
            UiAccount.Mastodon(
                accountKey = MicroBlogKey(id = "2", host = "mastodon.social"),
                instance = "mastodon.social",
            )
        val mapping = deepLinkMapping(account1, account2)

        val matches = PlatformDeepLinkMatcher.matches("https://mastodon.social/@alice", mapping)

        assertEquals(2, matches.size)
        assertEquals(
            DeeplinkRoute.Profile.UserNameWithHost(
                accountType = AccountType.Specific(account1.accountKey),
                userName = "alice",
                host = "mastodon.social",
            ),
            matches[account1],
        )
        assertEquals(
            DeeplinkRoute.Profile.UserNameWithHost(
                accountType = AccountType.Specific(account2.accountKey),
                userName = "alice",
                host = "mastodon.social",
            ),
            matches[account2],
        )
    }

    @Test
    fun matchesReturnsEmptyForNonMatchingUrl() {
        val account =
            UiAccount.Mastodon(
                accountKey = MicroBlogKey(id = "1", host = "mastodon.social"),
                instance = "mastodon.social",
            )
        val mapping = deepLinkMapping(account)

        assertTrue(PlatformDeepLinkMatcher.matches("https://google.com", mapping).isEmpty())
        assertTrue(PlatformDeepLinkMatcher.matches("https://mastodon.social/about", mapping).isEmpty())
    }

    @Test
    fun matchesRealWorldLinks() {
        val mastodonAccount =
            UiAccount.Mastodon(
                accountKey = MicroBlogKey(id = "1", host = "mastodon.example"),
                instance = "mastodon.example",
            )
        val misskeyAccount =
            UiAccount.Misskey(
                accountKey = MicroBlogKey(id = "2", host = "misskey.example"),
                host = "misskey.example",
            )
        val bskyAccount =
            UiAccount.Bluesky(
                accountKey = MicroBlogKey(id = "3", host = "bsky.example"),
            )
        val xAccount =
            UiAccount.XQT(
                accountKey = MicroBlogKey(id = "4", host = xqtHost),
            )

        val mapping = deepLinkMapping(mastodonAccount, misskeyAccount, bskyAccount, xAccount)

        assertEquals(
            DeeplinkRoute.Profile.UserNameWithHost(
                accountType = AccountType.Specific(mastodonAccount.accountKey),
                userName = "alice",
                host = "mastodon.example",
            ),
            PlatformDeepLinkMatcher.matches("https://mastodon.example/@alice", mapping)[mastodonAccount],
        )

        assertEquals(
            DeeplinkRoute.Status.Detail(
                accountType = AccountType.Specific(mastodonAccount.accountKey),
                statusKey = MicroBlogKey("12345", "mastodon.example"),
            ),
            PlatformDeepLinkMatcher.matches("https://mastodon.example/@alice/12345", mapping)[mastodonAccount],
        )

        assertEquals(
            DeeplinkRoute.Profile.UserNameWithHost(
                accountType = AccountType.Specific(misskeyAccount.accountKey),
                userName = "bob",
                host = "misskey.example",
            ),
            PlatformDeepLinkMatcher.matches("https://misskey.example/@bob", mapping)[misskeyAccount],
        )

        assertEquals(
            DeeplinkRoute.Status.Detail(
                accountType = AccountType.Specific(misskeyAccount.accountKey),
                statusKey = MicroBlogKey("12345", "misskey.example"),
            ),
            PlatformDeepLinkMatcher.matches("https://misskey.example/notes/12345", mapping)[misskeyAccount],
        )

        assertEquals(
            DeeplinkRoute.Profile.UserNameWithHost(
                accountType = AccountType.Specific(bskyAccount.accountKey),
                userName = "alice.bsky.social",
                host = "bsky.example",
            ),
            PlatformDeepLinkMatcher.matches("https://bsky.example/profile/alice.bsky.social", mapping)[bskyAccount],
        )

        assertEquals(
            DeeplinkRoute.Status.Detail(
                accountType = AccountType.Specific(bskyAccount.accountKey),
                statusKey =
                    MicroBlogKey(
                        id = "at://alice.bsky.social/app.bsky.feed.post/12345",
                        host = "bsky.example",
                    ),
            ),
            PlatformDeepLinkMatcher.matches(
                "https://bsky.example/profile/alice.bsky.social/post/12345",
                mapping,
            )[bskyAccount],
        )

        assertEquals(
            DeeplinkRoute.Profile.UserNameWithHost(
                accountType = AccountType.Specific(xAccount.accountKey),
                userName = "alice",
                host = xqtHost,
            ),
            PlatformDeepLinkMatcher.matches("https://$xqtHost/alice", mapping)[xAccount],
        )

        assertEquals(
            DeeplinkRoute.Status.Detail(
                accountType = AccountType.Specific(xAccount.accountKey),
                statusKey = MicroBlogKey("12345", xqtHost),
            ),
            PlatformDeepLinkMatcher.matches("https://$xqtHost/alice/status/12345", mapping)[xAccount],
        )

        assertEquals(
            DeeplinkRoute.Media.StatusMedia(
                accountType = AccountType.Specific(xAccount.accountKey),
                statusKey = MicroBlogKey("12345", xqtHost),
                index = 1,
                preview = null,
            ),
            PlatformDeepLinkMatcher.matches("https://$xqtHost/alice/status/12345/photo/1", mapping)[xAccount],
        )
    }

    @Test
    fun profileWithFullHandleUsesHandleHost() {
        val account =
            UiAccount.Mastodon(
                accountKey = MicroBlogKey(id = "1", host = "mastodon.social"),
                instance = "mastodon.social",
            )
        val mapping = deepLinkMapping(account)

        val matches = PlatformDeepLinkMatcher.matches("https://mastodon.social/@bob@misskey.io", mapping)

        assertEquals(
            DeeplinkRoute.Profile.UserNameWithHost(
                accountType = AccountType.Specific(account.accountKey),
                userName = "bob",
                host = "misskey.io",
            ),
            matches[account],
        )
    }
}

private fun PlatformType.deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> =
    platformRegistry.require(this).deepLinks(accountKey)

private fun UiAccount.deepLinks(): ImmutableList<PlatformDeepLink<*>> =
    platformRegistry.require(platformType).deepLinks(accountKey)

private fun deepLinkMapping(vararg accounts: UiAccount): Map<UiAccount, List<PlatformDeepLink<*>>> =
    persistentMapOf<UiAccount, List<PlatformDeepLink<*>>>()
        .builder()
        .apply {
            accounts.forEach { account ->
                put(account, account.deepLinks())
            }
        }.build()

private val platformRegistry = testPlatformRegistry()
