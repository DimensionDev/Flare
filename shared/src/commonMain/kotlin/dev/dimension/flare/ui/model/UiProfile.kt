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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable

@Serializable
@Immutable
public data class UiProfile internal constructor(
    val key: MicroBlogKey,
    val handle: String,
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
            Element("span")
                .apply {
                    appendText(handleWithoutAtAndHost)
                }.toUi()
        } else {
            nameInternal
        }
    }

    val onClicked: ClickContext.() -> Unit by lazy {
        clickEvent.onClicked
    }

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
        handle.removePrefix("@")
    }

    val handleWithoutAtAndHost: String by lazy {
        run {
            handle
                .removePrefix("@")
                .split("@")
                .firstOrNull()
                ?: handleWithoutAt
        }.let {
            if (platformType == PlatformType.Bluesky) {
                it.removeSuffix(".bsky.social")
            } else {
                it
            }
        }
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
        handle = "@sampleUser",
        avatar = "https://example.com/avatar.jpg",
        nameInternal = Element("span").toUi(),
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
