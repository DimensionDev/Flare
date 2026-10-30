package dev.dimension.flare.feature.plugin.wire

import kotlinx.serialization.Serializable

@Serializable
public data class ComposeConfigV1(
    val text: TextConfigV1? = null,
    val media: MediaConfigV1? = null,
    val visibility: VisibilityConfigV1? = null,
    val contentWarning: Boolean = false,
    val poll: PollConfigV1? = null,
    val language: LanguageConfigV1? = null,
) {
    @Serializable
    public data class TextConfigV1(
        val maxLength: Int,
    )

    @Serializable
    public data class MediaConfigV1(
        val minCountForNew: Int = 0,
        val maxCount: Int,
        val maxBytes: Long,
        val supportedMimeTypes: Set<String> = emptySet(),
        val altTextMaxLength: Int = 1_500,
        val canSensitive: Boolean = true,
    )

    @Serializable
    public data class VisibilityConfigV1(
        val allowed: Set<VisibilityV1>,
        val default: VisibilityV1 = VisibilityV1.Public,
    )

    @Serializable
    public data class PollConfigV1(
        val maxOptions: Int,
    )

    @Serializable
    public data class LanguageConfigV1(
        val maxCount: Int,
    )
}

@Serializable
public data class ComposeAssetV1(
    val handle: String,
    val fileName: String? = null,
    val mimeType: String? = null,
    val description: String? = null,
)

@Serializable
public data class ComposePollV1(
    val options: List<String>,
    val expiresInSeconds: Long,
    val multiple: Boolean = false,
)

@Serializable
public data class ComposeRequestV1(
    val text: String,
    val visibility: VisibilityV1,
    val languages: List<String> = emptyList(),
    val assets: List<ComposeAssetV1> = emptyList(),
    val sensitive: Boolean = false,
    val spoilerText: String? = null,
    val replyTo: EntityKeyV1? = null,
    val poll: ComposePollV1? = null,
)

@Serializable
public data class ComposeResultV1(
    val post: PostV1,
)
