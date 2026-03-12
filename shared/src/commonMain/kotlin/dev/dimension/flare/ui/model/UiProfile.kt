package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import com.fleeksoft.ksoup.nodes.Element
import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.common.SerializableImmutableMap
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.humanizer.Formatter.humanize
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.Serializable

@Serializable
@Immutable
public data class UiProfile internal constructor(
    val key: MicroBlogKey,
    val handle: UiHandle,
    val avatar: String,
    private val nameInternal: UiRichText,
    val platformType: PlatformType,
    private val clickEvent: ClickEvent,
    public val banner: String?,
    public val description: UiRichText?,
    public val matrices: Matrices,
    public val mark: SerializableImmutableList<Mark>,
    public val bottomContent: BottomContent?,
) {
    // If name is blank, use handle without @ as display name
    val name: UiRichText by lazy {
        if (nameInternal.raw.isEmpty() || nameInternal.raw.isBlank()) {
            handleWithoutAtAndHost.toUiPlainText()
        } else {
            nameInternal
        }
    }

    val onClicked: ClickContext.() -> Unit by lazy {
        clickEvent.onClicked
    }

    internal fun mergeWith(existing: UiProfile): UiProfile =
        UiProfile(
            key = key,
            handle =
                if (handle.raw.isBlank()) {
                    existing.handle
                } else {
                    handle
                },
            avatar = avatar.ifBlank { existing.avatar },
            nameInternal =
                if (name.raw.isBlank()) {
                    existing.name
                } else {
                    name
                },
            platformType = platformType,
            clickEvent = clickEvent,
            banner = banner ?: existing.banner,
            description = description ?: existing.description,
            matrices = matrices.mergeWith(existing.matrices),
            mark = (existing.mark + mark).distinct().toPersistentList(),
            bottomContent = bottomContent ?: existing.bottomContent,
        )

    @Serializable
    @Immutable
    public data class Matrices internal constructor(
        val fansCount: Long,
        val followsCount: Long,
        val statusesCount: Long,
        val platformFansCount: String? = null,
    ) {
        val fansCountHumanized: String = platformFansCount ?: fansCount.humanize()
        val followsCountHumanized: String = followsCount.humanize()
        val statusesCountHumanized: String = statusesCount.humanize()
    }

    val handleWithoutAt: String by lazy {
        handle.normalizedRaw
    }

    val handleWithoutAtAndHost: String by lazy {
        handleWithoutAt.let {
            if (platformType == PlatformType.Bluesky) {
                it.removeSuffix(".bsky.social")
            } else {
                it
            }
        }
    }

    val host: String? by lazy {
        handle.normalizedHost
    }

    @Serializable
    public sealed interface BottomContent {
        @Serializable
        public data class Fields internal constructor(
            val fields: SerializableImmutableMap<String, UiRichText>,
        ) : BottomContent

        @Serializable
        public data class Iconify internal constructor(
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
        avatar = "https://example.com/avatar.jpg",
        nameInternal = "".toUiPlainText(),
        platformType = PlatformType.Mastodon,
        clickEvent = ClickEvent.Noop,
        banner = "https://example.com/banner.jpg",
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
