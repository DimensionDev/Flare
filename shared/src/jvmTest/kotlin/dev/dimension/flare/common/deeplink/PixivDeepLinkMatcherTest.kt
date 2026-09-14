package dev.dimension.flare.common.deeplink

import dev.dimension.flare.data.platform.PIXIV_HOST
import dev.dimension.flare.data.platform.PixivPlatformSpec
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.route.DeeplinkRoute
import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PixivDeepLinkMatcherTest {
    private val account = UiAccount(MicroBlogKey("account", PIXIV_HOST), PixivPlatformSpec.platformId)
    private val mapping = mapOf(account to PixivPlatformSpec.deepLinks(account.accountKey))

    @Test
    fun artworkUrlVariantsOpenTheSameArtwork() {
        urlVariants("artworks/149594519").forEach { url ->
            val matches = PlatformDeepLinkMatcher.matches(url, mapping)

            assertEquals(1, matches.size, url)
            val match = assertNotNull(matches[account], url)
            assertEquals(
                DeeplinkRoute.Gallery.Detail(
                    accountType = AccountType.Specific(account.accountKey),
                    statusKey = MicroBlogKey("149594519", PIXIV_HOST),
                ),
                match.route,
                url,
            )
            assertEquals(Url(url).host, match.host, url)
        }
    }

    @Test
    fun userUrlVariantsOpenTheSameProfile() {
        urlVariants("users/12345").forEach { url ->
            val matches = PlatformDeepLinkMatcher.matches(url, mapping)

            assertEquals(1, matches.size, url)
            assertEquals(
                DeeplinkRoute.Profile.User(
                    accountType = AccountType.Specific(account.accountKey),
                    userKey = MicroBlogKey("12345", PIXIV_HOST),
                ),
                assertNotNull(matches[account], url).route,
                url,
            )
        }
    }

    @Test
    fun sharingParametersAndFragmentsDoNotChangeTheArtwork() {
        val expected = PlatformDeepLinkMatcher.matches("https://www.pixiv.net/artworks/149594519", mapping)[account]?.route

        urlVariants("artworks/149594519").forEach { url ->
            val sharedUrl = "$url?utm_source=share&utm_medium=ios#big_0"
            assertEquals(
                expected,
                assertNotNull(PlatformDeepLinkMatcher.matches(sharedUrl, mapping)[account], sharedUrl).route,
                sharedUrl,
            )
        }
    }

    @Test
    fun variantsRemainAvailableToEveryPixivAccount() {
        val secondAccount = UiAccount(MicroBlogKey("second", PIXIV_HOST), PixivPlatformSpec.platformId)
        val accounts = listOf(account, secondAccount)
        val mapping = accounts.associateWith { PixivPlatformSpec.deepLinks(it.accountKey) }

        urlVariants("artworks/149594519").forEach { url ->
            val matches = PlatformDeepLinkMatcher.matches(url, mapping)
            assertEquals(accounts.toSet(), matches.keys, url)
            accounts.forEach { candidate ->
                assertEquals(
                    DeeplinkRoute.Gallery.Detail(
                        accountType = AccountType.Specific(candidate.accountKey),
                        statusKey = MicroBlogKey("149594519", PIXIV_HOST),
                    ),
                    assertNotNull(matches[candidate], url).route,
                    url,
                )
            }
        }
    }

    @Test
    fun unrelatedHostsAndUnsupportedPathsDoNotMatch() {
        listOf(
            "https://www.pixiv.net.example.com/artworks/149594519",
            "https://example.pixiv.net/artworks/149594519",
            "https://example.com/en/artworks/149594519/",
            "https://www.pixiv.net/unknown/artworks/149594519",
            "https://www.pixiv.net/en/artworks/149594519/comments",
            "https://www.pixiv.net/en/users/12345/bookmarks/artworks",
            "https://www.pixiv.net/artworks/149594519//",
        ).forEach { url ->
            assertTrue(PlatformDeepLinkMatcher.matches(url, mapping).isEmpty(), url)
        }
    }

    private fun urlVariants(path: String): List<String> =
        listOf(
            "https://www.pixiv.net/$path",
            "https://www.pixiv.net/en/$path",
            "https://www.pixiv.net/$path/",
            "https://www.pixiv.net/en/$path/",
            "https://pixiv.net/$path",
            "https://pixiv.net/en/$path",
            "https://pixiv.net/$path/",
            "https://pixiv.net/en/$path/",
        )
}
