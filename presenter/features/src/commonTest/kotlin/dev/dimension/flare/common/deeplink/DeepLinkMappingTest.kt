package dev.dimension.flare.common.deeplink

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.defaultSocialPlatformRegistry
import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.ui.route.DeeplinkRoute
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeepLinkMappingTest {
    private fun PlatformType.deepLinkPatterns(host: String) =
        defaultSocialPlatformRegistry.deepLinkPatterns(this, host)

    private fun profileRoute(
        accountKey: MicroBlogKey,
        userName: String,
        host: String = accountKey.host,
    ) = DeeplinkRoute.Profile.UserNameWithHost(
        accountType = AccountType.Specific(accountKey),
        userName = userName,
        host = host,
    )

    private fun postRoute(
        accountKey: MicroBlogKey,
        id: String,
    ) = DeeplinkRoute.Status.Detail(
        accountType = AccountType.Specific(accountKey),
        statusKey = MicroBlogKey(id, accountKey.host),
    )

    private fun blueskyPostRoute(
        accountKey: MicroBlogKey,
        handle: String,
        id: String,
    ) = DeeplinkRoute.Status.Detail(
        accountType = AccountType.Specific(accountKey),
        statusKey = MicroBlogKey("at://$handle/app.bsky.feed.post/$id", accountKey.host),
    )

    private fun postMediaRoute(
        accountKey: MicroBlogKey,
        id: String,
        index: Int,
    ) = DeeplinkRoute.Media.StatusMedia(
        accountType = AccountType.Specific(accountKey),
        statusKey = MicroBlogKey(id, accountKey.host),
        index = index,
        preview = null,
    )

    @Test
    fun mastodonPatternsAreGeneratedInOrder() {
        val host = "mastodon.social"

        val patterns = PlatformType.Mastodon.deepLinkPatterns(host)

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

        val patterns = PlatformType.Misskey.deepLinkPatterns(host)

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

        val patterns = PlatformType.Bluesky.deepLinkPatterns(host)

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

        val patterns = PlatformType.xQt.deepLinkPatterns(host)

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
        val patterns = PlatformType.VVo.deepLinkPatterns("irrelevant")

        assertTrue(patterns.isEmpty())
    }

    @Test
    fun matchesReturnsAccountProfile() {
        val mastodonAccountKey = MicroBlogKey(id = "1", host = "mastodon.social")
        val misskeyAccountKey = MicroBlogKey(id = "2", host = "misskey.example")
        val mapping =
            persistentMapOf(
                mastodonAccountKey to
                    PlatformType.Mastodon.deepLinkPatterns(mastodonAccountKey.host),
                misskeyAccountKey to
                    PlatformType.Misskey.deepLinkPatterns(misskeyAccountKey.host),
            )

        val matches = DeepLinkMapping.matches("https://mastodon.social/@alice", mapping)

        assertEquals(1, matches.size)
        assertEquals(profileRoute(mastodonAccountKey, "alice"), matches[mastodonAccountKey])
    }

    @Test
    fun matchesMultipleAccountsWithSameHost() {
        val account1 = MicroBlogKey(id = "1", host = "mastodon.social")
        val account2 = MicroBlogKey(id = "2", host = "mastodon.social")
        val mapping:
            ImmutableMap<MicroBlogKey, ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>>> =
            persistentMapOf(
                account1 to
                    PlatformType.Mastodon.deepLinkPatterns(account1.host),
                account2 to
                    PlatformType.Mastodon.deepLinkPatterns(account2.host),
            )

        val matches = DeepLinkMapping.matches("https://mastodon.social/@alice", mapping)

        assertEquals(2, matches.size)
        assertEquals(profileRoute(account1, "alice"), matches[account1])
        assertEquals(profileRoute(account2, "alice"), matches[account2])
    }

    @Test
    fun matchesReturnsEmptyForNonMatchingUrl() {
        val accountKey = MicroBlogKey(id = "1", host = "mastodon.social")
        val mapping:
            ImmutableMap<MicroBlogKey, ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>>> =
            persistentMapOf(
                accountKey to
                    PlatformType.Mastodon.deepLinkPatterns(accountKey.host),
            )

        // URL containing none of the valid hosts
        assertTrue(DeepLinkMapping.matches("https://google.com", mapping).isEmpty())

        // URL containing a valid host but invalid path
        assertTrue(DeepLinkMapping.matches("https://mastodon.social/about", mapping).isEmpty())
    }

    @Test
    fun matchesRealWorldLinks() {
        val mastodonAccountKey = MicroBlogKey(id = "1", host = "mastodon.example")
        val misskeyAccountKey = MicroBlogKey(id = "2", host = "misskey.example")
        val bskyAccountKey = MicroBlogKey(id = "3", host = "bsky.example")
        val xAccountKey = MicroBlogKey(id = "4", host = xqtHost)

        val mapping:
            ImmutableMap<MicroBlogKey, ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>>> =
            persistentMapOf(
                mastodonAccountKey to
                    PlatformType.Mastodon.deepLinkPatterns(mastodonAccountKey.host),
                misskeyAccountKey to
                    PlatformType.Misskey.deepLinkPatterns(misskeyAccountKey.host),
                bskyAccountKey to
                    PlatformType.Bluesky.deepLinkPatterns(bskyAccountKey.host),
                xAccountKey to
                    PlatformType.xQt.deepLinkPatterns(xAccountKey.host),
            )

        // https://mastodon.example/@alice
        val mastodonProfileMatch =
            DeepLinkMapping.matches("https://mastodon.example/@alice", mapping)
        assertEquals(
            profileRoute(mastodonAccountKey, "alice"),
            mastodonProfileMatch[mastodonAccountKey],
        )

        // https://mastodon.example/@alice/12345
        val mastodonPostMatch =
            DeepLinkMapping.matches("https://mastodon.example/@alice/12345", mapping)
        assertEquals(
            postRoute(mastodonAccountKey, "12345"),
            mastodonPostMatch[mastodonAccountKey],
        )

        // https://misskey.example/@bob
        val misskeyProfileMatch = DeepLinkMapping.matches("https://misskey.example/@bob", mapping)
        assertEquals(
            profileRoute(misskeyAccountKey, "bob"),
            misskeyProfileMatch[misskeyAccountKey],
        )

        // https://misskey.example/notes/12345
        val misskeyPostMatch =
            DeepLinkMapping.matches("https://misskey.example/notes/12345", mapping)
        assertEquals(postRoute(misskeyAccountKey, "12345"), misskeyPostMatch[misskeyAccountKey])

        // https://bsky.example/profile/alice.bsky.social
        val bskyProfileMatch =
            DeepLinkMapping.matches("https://bsky.example/profile/alice.bsky.social", mapping)
        assertEquals(
            profileRoute(bskyAccountKey, "alice.bsky.social"),
            bskyProfileMatch[bskyAccountKey],
        )

        // https://bsky.example/profile/alice.bsky.social/post/12345
        val bskyPostMatch =
            DeepLinkMapping.matches(
                "https://bsky.example/profile/alice.bsky.social/post/12345",
                mapping,
            )
        assertEquals(
            blueskyPostRoute(bskyAccountKey, "alice.bsky.social", "12345"),
            bskyPostMatch[bskyAccountKey],
        )

        // https://x.example/alice
        val xProfileMatch = DeepLinkMapping.matches("https://$xqtHost/alice", mapping)
        assertEquals(profileRoute(xAccountKey, "alice"), xProfileMatch[xAccountKey])

        // https://x.example/alice/status/12345
        val xPostMatch = DeepLinkMapping.matches("https://$xqtHost/alice/status/12345", mapping)
        assertEquals(postRoute(xAccountKey, "12345"), xPostMatch[xAccountKey])

        // https://x.example/alice/status/12345/photo/1
        val xPostPhotoMatch =
            DeepLinkMapping.matches("https://$xqtHost/alice/status/12345/photo/1", mapping)
        assertEquals(postMediaRoute(xAccountKey, "12345", 1), xPostPhotoMatch[xAccountKey])
    }

    @Test
    fun matchesGeneratesRoutesForDecodedTypes() {
        val accountKey = MicroBlogKey(id = "1", host = "mastodon.social")
        val mapping =
            persistentMapOf(
                accountKey to PlatformType.Mastodon.deepLinkPatterns(accountKey.host),
            )

        assertEquals(
            profileRoute(accountKey, "alice"),
            DeepLinkMapping.matches("https://mastodon.social/@alice", mapping)[accountKey],
        )

        assertEquals(
            profileRoute(accountKey, "bob", "misskey.io"),
            DeepLinkMapping.matches("https://mastodon.social/@bob@misskey.io", mapping)[accountKey],
        )

        assertEquals(
            postRoute(accountKey, "12345"),
            DeepLinkMapping.matches("https://mastodon.social/@alice/12345", mapping)[accountKey],
        )
    }
}
