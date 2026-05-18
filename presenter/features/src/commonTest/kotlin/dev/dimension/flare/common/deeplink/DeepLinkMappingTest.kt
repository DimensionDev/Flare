package dev.dimension.flare.common.deeplink

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.spec
import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.route.DeeplinkRoute
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeepLinkMappingTest {
    @Test
    fun mastodonPatternsAreGeneratedInOrder() {
        val host = "mastodon.social"

        val patterns = PlatformType.Mastodon.spec.deepLinkPatterns(host)

        assertEquals(2, patterns.size)

        val profile = patterns[0]
        assertEquals(DeepLinkMapping.Type.Profile.serializer(), profile.serializer)
        assertEquals(Url("https://$host/@{handle}"), profile.uriPattern)
        assertEquals(
            listOf("handle" to true),
            profile.pathSegments
                .filter { it.stringValue.isNotEmpty() }
                .map { it.stringValue to it.isParamArg },
        )

        val post = patterns[1]
        assertEquals(DeepLinkMapping.Type.Post.serializer(), post.serializer)
        assertEquals(Url("https://$host/@{handle}/{id}"), post.uriPattern)
        assertEquals(
            listOf("handle" to true, "id" to true),
            post.pathSegments
                .filter { it.stringValue.isNotEmpty() }
                .map { it.stringValue to it.isParamArg },
        )
    }

    @Test
    fun misskeyPatternsUseNotesRoute() {
        val host = "misskey.example"

        val patterns = PlatformType.Misskey.spec.deepLinkPatterns(host)

        assertEquals(2, patterns.size)

        val profile = patterns[0]
        assertEquals(DeepLinkMapping.Type.Profile.serializer(), profile.serializer)
        assertEquals(Url("https://$host/@{handle}"), profile.uriPattern)
        assertEquals(
            listOf("handle" to true),
            profile.pathSegments
                .filter { it.stringValue.isNotEmpty() }
                .map { it.stringValue to it.isParamArg },
        )

        val post = patterns[1]
        assertEquals(DeepLinkMapping.Type.Post.serializer(), post.serializer)
        assertEquals(Url("https://$host/notes/{id}"), post.uriPattern)
        assertEquals(
            listOf("notes" to false, "id" to true),
            post.pathSegments
                .filter { it.stringValue.isNotEmpty() }
                .map { it.stringValue to it.isParamArg },
        )
    }

    @Test
    fun blueskyPatternsIncludeProfileAndPost() {
        val host = "bsky.example"

        val patterns = PlatformType.Bluesky.spec.deepLinkPatterns(host)

        assertEquals(2, patterns.size)

        val profile = patterns[0]
        assertEquals(DeepLinkMapping.Type.Profile.serializer(), profile.serializer)
        assertEquals(Url("https://$host/profile/{handle}"), profile.uriPattern)
        assertEquals(
            listOf("profile" to false, "handle" to true),
            profile.pathSegments
                .filter { it.stringValue.isNotEmpty() }
                .map { it.stringValue to it.isParamArg },
        )

        val post = patterns[1]
        assertEquals(DeepLinkMapping.Type.BlueskyPost.serializer(), post.serializer)
        assertEquals(Url("https://$host/profile/{handle}/post/{id}"), post.uriPattern)
        assertEquals(
            listOf("profile" to false, "handle" to true, "post" to false, "id" to true),
            post.pathSegments
                .filter { it.stringValue.isNotEmpty() }
                .map { it.stringValue to it.isParamArg },
        )
    }

    @Test
    fun xqtPatternsUseStatusRoute() {
        val host = xqtHost

        val patterns = PlatformType.xQt.spec.deepLinkPatterns(host)

        assertEquals(12, patterns.size)

        val profile = patterns[0]
        assertEquals(DeepLinkMapping.Type.Profile.serializer(), profile.serializer)
        assertEquals(Url("https://$host/{handle}"), profile.uriPattern)
        assertEquals(
            listOf("handle" to true),
            profile.pathSegments
                .filter { it.stringValue.isNotEmpty() }
                .map { it.stringValue to it.isParamArg },
        )

        val post = patterns[4]
        assertEquals(DeepLinkMapping.Type.Post.serializer(), post.serializer)
        assertEquals(Url("https://$host/{handle}/status/{id}"), post.uriPattern)
        assertEquals(
            listOf("handle" to true, "status" to false, "id" to true),
            post.pathSegments
                .filter { it.stringValue.isNotEmpty() }
                .map { it.stringValue to it.isParamArg },
        )

        val media = patterns[8]
        assertEquals(DeepLinkMapping.Type.PostMedia.serializer(), media.serializer)
        assertEquals(Url("https://$host/{handle}/status/{id}/photo/{index}"), media.uriPattern)
        assertEquals(
            listOf(
                "handle" to true,
                "status" to false,
                "id" to true,
                "photo" to false,
                "index" to true,
            ),
            media.pathSegments
                .filter { it.stringValue.isNotEmpty() }
                .map { it.stringValue to it.isParamArg },
        )
    }

    @Test
    fun vvoHasNoPatterns() {
        val patterns = PlatformType.VVo.spec.deepLinkPatterns("irrelevant")

        assertTrue(patterns.isEmpty())
    }

    @Test
    fun matchesReturnsAccountProfile() {
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
        val mapping =
            persistentMapOf(
                mastodonAccount to
                    PlatformType.Mastodon.spec.deepLinkPatterns(mastodonAccount.accountKey.host),
                misskeyAccount to
                    PlatformType.Misskey.spec.deepLinkPatterns(misskeyAccount.accountKey.host),
            )

        val matches = DeepLinkMapping.matches("https://mastodon.social/@alice", mapping)

        assertEquals(1, matches.size)
        assertEquals(DeepLinkMapping.Type.Profile("alice"), matches[mastodonAccount])
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
        val mapping:
            ImmutableMap<UiAccount, ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>>> =
            persistentMapOf(
                account1 to
                    PlatformType.Mastodon.spec.deepLinkPatterns(account1.accountKey.host),
                account2 to
                    PlatformType.Mastodon.spec.deepLinkPatterns(account2.accountKey.host),
            )

        val matches = DeepLinkMapping.matches("https://mastodon.social/@alice", mapping)

        assertEquals(2, matches.size)
        assertEquals(DeepLinkMapping.Type.Profile("alice"), matches[account1])
        assertEquals(DeepLinkMapping.Type.Profile("alice"), matches[account2])
    }

    @Test
    fun matchesReturnsEmptyForNonMatchingUrl() {
        val account =
            UiAccount.Mastodon(
                accountKey = MicroBlogKey(id = "1", host = "mastodon.social"),
                instance = "mastodon.social",
            )
        val mapping:
            ImmutableMap<UiAccount, ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>>> =
            persistentMapOf(
                account to
                    PlatformType.Mastodon.spec.deepLinkPatterns(account.accountKey.host),
            )

        // URL containing none of the valid hosts
        assertTrue(DeepLinkMapping.matches("https://google.com", mapping).isEmpty())

        // URL containing a valid host but invalid path
        assertTrue(DeepLinkMapping.matches("https://mastodon.social/about", mapping).isEmpty())
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

        val mapping:
            ImmutableMap<UiAccount, ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>>> =
            persistentMapOf(
                mastodonAccount to
                    PlatformType.Mastodon.spec.deepLinkPatterns(mastodonAccount.accountKey.host),
                misskeyAccount to
                    PlatformType.Misskey.spec.deepLinkPatterns(misskeyAccount.accountKey.host),
                bskyAccount to
                    PlatformType.Bluesky.spec.deepLinkPatterns(bskyAccount.accountKey.host),
                xAccount to
                    PlatformType.xQt.spec.deepLinkPatterns(xAccount.accountKey.host),
            )

        // https://mastodon.example/@alice
        val mastodonProfileMatch =
            DeepLinkMapping.matches("https://mastodon.example/@alice", mapping)
        assertEquals(DeepLinkMapping.Type.Profile("alice"), mastodonProfileMatch[mastodonAccount])

        // https://mastodon.example/@alice/12345
        val mastodonPostMatch =
            DeepLinkMapping.matches("https://mastodon.example/@alice/12345", mapping)
        assertEquals(
            DeepLinkMapping.Type.Post("alice", "12345"),
            mastodonPostMatch[mastodonAccount],
        )

        // https://misskey.example/@bob
        val misskeyProfileMatch = DeepLinkMapping.matches("https://misskey.example/@bob", mapping)
        assertEquals(DeepLinkMapping.Type.Profile("bob"), misskeyProfileMatch[misskeyAccount])

        // https://misskey.example/notes/12345
        val misskeyPostMatch =
            DeepLinkMapping.matches("https://misskey.example/notes/12345", mapping)
        assertEquals(DeepLinkMapping.Type.Post(null, "12345"), misskeyPostMatch[misskeyAccount])

        // https://bsky.example/profile/alice.bsky.social
        val bskyProfileMatch =
            DeepLinkMapping.matches("https://bsky.example/profile/alice.bsky.social", mapping)
        assertEquals(
            DeepLinkMapping.Type.Profile("alice.bsky.social"),
            bskyProfileMatch[bskyAccount],
        )

        // https://bsky.example/profile/alice.bsky.social/post/12345
        val bskyPostMatch =
            DeepLinkMapping.matches(
                "https://bsky.example/profile/alice.bsky.social/post/12345",
                mapping,
            )
        assertEquals(
            DeepLinkMapping.Type.BlueskyPost("alice.bsky.social", "12345"),
            bskyPostMatch[bskyAccount],
        )

        // https://x.example/alice
        val xProfileMatch = DeepLinkMapping.matches("https://$xqtHost/alice", mapping)
        assertEquals(DeepLinkMapping.Type.Profile("alice"), xProfileMatch[xAccount])

        // https://x.example/alice/status/12345
        val xPostMatch = DeepLinkMapping.matches("https://$xqtHost/alice/status/12345", mapping)
        assertEquals(DeepLinkMapping.Type.Post("alice", "12345"), xPostMatch[xAccount])

        // https://x.example/alice/status/12345/photo/1
        val xPostPhotoMatch =
            DeepLinkMapping.matches("https://$xqtHost/alice/status/12345/photo/1", mapping)
        assertEquals(DeepLinkMapping.Type.PostMedia("alice", "12345", 1), xPostPhotoMatch[xAccount])
    }

    @Test
    fun typeDeepLinkGeneratesCorrectUrl() {
        val accountKey = MicroBlogKey(id = "1", host = "mastodon.social")

        // Profile with simple handle
        val simpleProfile = DeepLinkMapping.Type.Profile("alice")
        assertEquals(
            DeeplinkRoute.Profile.UserNameWithHost(
                accountType = AccountType.Specific(accountKey),
                userName = "alice",
                host = "mastodon.social",
            ),
            simpleProfile.deepLink(accountKey),
        )

        // Profile with full handle
        val fullProfile = DeepLinkMapping.Type.Profile("bob@misskey.io")
        assertEquals(
            DeeplinkRoute.Profile.UserNameWithHost(
                accountType = AccountType.Specific(accountKey),
                userName = "bob",
                host = "misskey.io",
            ),
            fullProfile.deepLink(accountKey),
        )

        // Post
        val post = DeepLinkMapping.Type.Post(id = "12345")
        assertEquals(
            DeeplinkRoute.Status.Detail(
                accountType = AccountType.Specific(accountKey),
                statusKey = MicroBlogKey("12345", "mastodon.social"),
            ),
            post.deepLink(accountKey),
        )
    }
}
