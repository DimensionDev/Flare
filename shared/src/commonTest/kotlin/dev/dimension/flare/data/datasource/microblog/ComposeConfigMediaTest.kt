package dev.dimension.flare.data.datasource.microblog

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

    private fun media(
        maxSizeBytes: Long,
        maxWidth: Int,
        maxHeight: Int,
    ): ComposeConfig =
        ComposeConfig(
            media =
                ComposeConfig.Media(
                    maxCount = 4,
                    canSensitive = true,
                    altTextMaxLength = 1_000,
                    allowMediaOnly = true,
                    compression = ComposeConfig.Media.Compression(maxSizeBytes, maxWidth, maxHeight),
                ),
        )
}
