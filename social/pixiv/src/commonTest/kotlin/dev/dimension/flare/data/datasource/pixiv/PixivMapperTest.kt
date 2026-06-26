package dev.dimension.flare.data.datasource.pixiv

import dev.dimension.flare.data.network.pixiv.model.PixivIllust
import dev.dimension.flare.data.network.pixiv.model.PixivImageUrls
import dev.dimension.flare.data.network.pixiv.model.PixivMetaPage
import dev.dimension.flare.data.network.pixiv.model.PixivMetaSinglePage
import dev.dimension.flare.data.network.pixiv.model.PixivUser
import dev.dimension.flare.ui.model.UiMedia
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PixivMapperTest {
    @Test
    fun singlePageIllustUsesMetaSinglePageOriginalImageUrl() {
        val post =
            pixivIllust(
                imageUrls =
                    PixivImageUrls(
                        squareMedium = "https://example.com/square.jpg",
                        medium = "https://example.com/medium.jpg",
                        large = "https://example.com/large.jpg",
                    ),
                metaSinglePage =
                    PixivMetaSinglePage(
                        originalImageUrl = "https://example.com/original.png",
                    ),
            ).toUiMedia()

        val image = assertIs<UiMedia.Image>(post.single())
        assertEquals("https://example.com/original.png", image.url)
        assertEquals("https://example.com/medium.jpg", image.previewUrl)
    }

    @Test
    fun multiPageIllustUsesPageImageUrls() {
        val post =
            pixivIllust(
                imageUrls =
                    PixivImageUrls(
                        medium = "https://example.com/cover-medium.jpg",
                        large = "https://example.com/cover-large.jpg",
                    ),
                metaSinglePage =
                    PixivMetaSinglePage(
                        originalImageUrl = "https://example.com/cover-original.png",
                    ),
                metaPages =
                    listOf(
                        PixivMetaPage(
                            imageUrls =
                                PixivImageUrls(
                                    medium = "https://example.com/page-0-medium.jpg",
                                    large = "https://example.com/page-0-large.jpg",
                                    original = "https://example.com/page-0-original.png",
                                ),
                        ),
                    ),
            ).toUiMedia()

        val image = assertIs<UiMedia.Image>(post.single())
        assertEquals("https://example.com/page-0-original.png", image.url)
        assertEquals("https://example.com/page-0-medium.jpg", image.previewUrl)
    }

    private fun pixivIllust(
        imageUrls: PixivImageUrls,
        metaSinglePage: PixivMetaSinglePage? = null,
        metaPages: List<PixivMetaPage> = emptyList(),
    ): PixivIllust =
        PixivIllust(
            id = 146347478,
            title = "As if in a dream",
            type = "illust",
            imageUrls = imageUrls,
            user =
                PixivUser(
                    id = 21714218,
                    name = "Hyde",
                    account = "hidetohyde",
                ),
            createDate = "2026-06-22T22:09:00+00:00",
            width = 4000,
            height = 3000,
            metaSinglePage = metaSinglePage,
            metaPages = metaPages,
        )
}
