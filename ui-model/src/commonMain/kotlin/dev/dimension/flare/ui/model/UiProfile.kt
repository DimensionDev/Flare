package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.common.SerializableImmutableMap
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.humanizer.Formatter.humanize
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.native.HiddenFromObjC

@Serializable
@Immutable
public data class UiProfile public constructor(
    val key: MicroBlogKey,
    val handle: UiHandle,
    val avatar: UiMedia.Image?,
    private val nameInternal: UiRichText,
    @SerialName("platformType")
    val platformId: String,
    val platformIcon: UiIcon = UiIcon.World,
    public val clickEvent: ClickEvent,
    public val banner: UiMedia.Image?,
    public val description: UiRichText?,
    public val sourceLanguages: SerializableImmutableList<String> = persistentListOf(),
    @Transient
    public val translationDisplayState: TranslationDisplayState = TranslationDisplayState.Hidden,
    public val matrices: Matrices,
    public val mark: SerializableImmutableList<Mark>,
    public val bottomContent: BottomContent?,
) {
    public constructor(
        key: MicroBlogKey,
        handle: UiHandle,
        avatar: String,
        nameInternal: UiRichText,
        platformId: String,
        platformIcon: UiIcon = UiIcon.World,
        clickEvent: ClickEvent,
        banner: String?,
        description: UiRichText?,
        sourceLanguages: SerializableImmutableList<String> = persistentListOf(),
        translationDisplayState: TranslationDisplayState = TranslationDisplayState.Hidden,
        matrices: Matrices,
        mark: SerializableImmutableList<Mark>,
        bottomContent: BottomContent?,
    ) : this(
        key = key,
        handle = handle,
        avatar = avatar.toUiImage(),
        nameInternal = nameInternal,
        platformId = platformId,
        platformIcon = platformIcon,
        clickEvent = clickEvent,
        banner = banner.toUiImage(),
        description = description,
        sourceLanguages = sourceLanguages,
        translationDisplayState = translationDisplayState,
        matrices = matrices,
        mark = mark,
        bottomContent = bottomContent,
    )

    // If name is blank, use handle without @ as display name
    val name: UiRichText by lazy {
        if (nameInternal.raw.isEmpty() || nameInternal.raw.isBlank()) {
            handleWithoutAt.toUiPlainText(sourceLanguages)
        } else {
            nameInternal
        }
    }

    val onClicked: ClickContext.() -> Unit by lazy {
        clickEvent.onClicked
    }

    @HiddenFromObjC
    public fun mergeWith(existing: UiProfile): UiProfile {
        val keepExistingHandle = handle.raw.isBlank()
        val keepExistingName = nameInternal.raw.isBlank()
        return UiProfile(
            key = key,
            handle = if (keepExistingHandle) existing.handle else handle,
            avatar = avatar ?: existing.avatar,
            nameInternal = if (keepExistingName) existing.nameInternal else nameInternal,
            platformId = platformId,
            platformIcon = platformIcon,
            clickEvent = clickEvent,
            banner = banner ?: existing.banner,
            description = description ?: existing.description,
            sourceLanguages = if (sourceLanguages.isEmpty()) existing.sourceLanguages else sourceLanguages,
            matrices = matrices.mergeWith(existing.matrices),
            mark = (existing.mark + mark).distinct().toPersistentList(),
            bottomContent = bottomContent.mergeWith(existing.bottomContent),
        )
    }

    @Serializable
    @Immutable
    public data class Matrices public constructor(
        val fansCount: Long,
        val followsCount: Long,
        val statusesCount: Long,
        val platformFansCount: String? = null,
    ) {
        val fansCountHumanized: String by lazy {
            platformFansCount ?: fansCount.humanize()
        }
        val followsCountHumanized: String by lazy {
            followsCount.humanize()
        }
        val statusesCountHumanized: String by lazy {
            statusesCount.humanize()
        }
    }

    val handleWithoutAt: String by lazy {
        handle.normalizedRaw
    }

    val host: String? by lazy {
        handle.normalizedHost
    }

    @Serializable
    public sealed interface BottomContent {
        @Serializable
        public data class Fields public constructor(
            val fields: SerializableImmutableMap<String, UiRichText>,
        ) : BottomContent

        @Serializable
        public data class Iconify public constructor(
            val items: SerializableImmutableMap<Icon, UiRichText>,
        ) : BottomContent {
            public enum class Icon {
                Location,
                Url,
                Verify,
            }
        }
    }

    @Serializable
    public enum class Mark {
        Verified,
        Cat,
        Bot,
        Locked,
    }
}

public fun createSampleUser(): UiProfile =
    UiProfile(
        key = MicroBlogKey("sampleKey", "sampleHost"),
        handle =
            UiHandle(
                raw = "sampleUser",
                host = "sampleHost",
            ),
        avatar = "https://example.com/avatar.jpg".toUiImage(),
        nameInternal = "".toUiPlainText(),
        platformId = "Mastodon",
        clickEvent = ClickEvent.Noop,
        banner = "https://example.com/banner.jpg".toUiImage(),
        description = null,
        matrices =
            UiProfile.Matrices(
                fansCount = 1000,
                followsCount = 500,
                statusesCount = 300,
                platformFansCount = "1K",
            ),
        mark = persistentListOf(),
        bottomContent = null,
    )

private fun UiProfile.Matrices.mergeWith(existing: UiProfile.Matrices): UiProfile.Matrices =
    UiProfile.Matrices(
        fansCount = fansCount.takeUnless { it == 0L && existing.fansCount > 0L } ?: existing.fansCount,
        followsCount = followsCount.takeUnless { it == 0L && existing.followsCount > 0L } ?: existing.followsCount,
        statusesCount = statusesCount.takeUnless { it == 0L && existing.statusesCount > 0L } ?: existing.statusesCount,
        platformFansCount = platformFansCount ?: existing.platformFansCount,
    )

private fun UiProfile.BottomContent?.mergeWith(existing: UiProfile.BottomContent?): UiProfile.BottomContent? =
    when {
        this == null -> {
            existing
        }

        existing == null -> {
            this
        }

        this is UiProfile.BottomContent.Fields && existing is UiProfile.BottomContent.Fields -> {
            UiProfile.BottomContent.Fields(
                fields = (existing.fields + fields).toPersistentMap(),
            )
        }

        this is UiProfile.BottomContent.Iconify && existing is UiProfile.BottomContent.Iconify -> {
            UiProfile.BottomContent.Iconify(
                items = (existing.items + items).toPersistentMap(),
            )
        }

        else -> {
            this
        }
    }
