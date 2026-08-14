package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.ui.model.UiTimelineV2
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeConfigMediaTest {
    @Test
    fun mergedAccountsUseStrictestCompressionLimits() {
        val first = media(maxSizeBytes = 16_000_000, maxWidth = 2_000, maxHeight = 4_000)
        val second = media(maxSizeBytes = 5_000_000, maxWidth = 4_000, maxHeight = 2_000)

        assertEquals(
            ComposeConfig.Media.Compression(
                maxSizeBytes = 5_000_000,
                maxWidth = 2_000,
                maxHeight = 2_000,
            ),
            first.merge(second).media?.compression,
        )
    }

    @Test
    fun mergedAccountsIntersectPluginMediaAndVisibilityConstraints() {
        val first =
            media(
                maxSizeBytes = 16_000_000,
                maxWidth = 2_000,
                maxHeight = 4_000,
                minCountForNew = 1,
                mimeTypes = setOf("image/jpeg", "image/png"),
                visibility =
                    setOf(
                        UiTimelineV2.Post.Visibility.Public,
                        UiTimelineV2.Post.Visibility.Home,
                        UiTimelineV2.Post.Visibility.Followers,
                    ),
            )
        val second =
            media(
                maxSizeBytes = 5_000_000,
                maxWidth = 4_000,
                maxHeight = 2_000,
                minCountForNew = 2,
                mimeTypes = setOf("image/png", "video/mp4"),
                visibility =
                    setOf(
                        UiTimelineV2.Post.Visibility.Home,
                        UiTimelineV2.Post.Visibility.Followers,
                    ),
            )
        val merged = first.merge(second)

        assertEquals(2, merged.media?.minCountForNew)
        assertEquals(setOf("image/png"), merged.media?.supportedMimeTypes)
        assertEquals(
            setOf(
                UiTimelineV2.Post.Visibility.Home,
                UiTimelineV2.Post.Visibility.Followers,
            ),
            merged.visibility?.allowedValues,
        )
    }

    private fun media(
        maxSizeBytes: Long,
        maxWidth: Int,
        maxHeight: Int,
        minCountForNew: Int = 0,
        mimeTypes: Set<String>? = null,
        visibility: Set<UiTimelineV2.Post.Visibility>? = null,
    ): ComposeConfig =
        ComposeConfig(
            media =
                ComposeConfig.Media(
                    maxCount = 4,
                    canSensitive = true,
                    altTextMaxLength = 1_000,
                    allowMediaOnly = true,
                    minCountForNew = minCountForNew,
                    supportedMimeTypes = mimeTypes,
                    compression = ComposeConfig.Media.Compression(maxSizeBytes, maxWidth, maxHeight),
                ),
            visibility = visibility?.let { ComposeConfig.Visibility(allowedValues = it) },
        )
}
