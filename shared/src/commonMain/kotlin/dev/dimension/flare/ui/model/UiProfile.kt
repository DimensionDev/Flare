package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.humanizer.humanize
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import moe.tlaster.ktml.dom.Element

@Immutable
data class UiProfile internal constructor(
    override val key: MicroBlogKey,
    override val handle: String,
    override val avatar: String,
    override val name: UiRichText,
    override val platformType: PlatformType,
    override val onClicked: ClickContext.() -> Unit,
    val banner: String?,
    val description: UiRichText?,
    val matrices: Matrices,
    val mark: ImmutableList<Mark>,
    val bottomContent: BottomContent?,
) : UiUserV2 {
    @Immutable
    data class Matrices(
        val fansCount: Long,
        val followsCount: Long,
        val statusesCount: Long,
        val platformFansCount: String? = null,
    ) {
        val fansCountHumanized = platformFansCount ?: fansCount.humanize()
        val followsCountHumanized = followsCount.humanize()
        val statusesCountHumanized = statusesCount.humanize()
    }

    sealed interface BottomContent {
        data class Fields(
            val fields: ImmutableMap<String, UiRichText>,
        ) : BottomContent

        data class Iconify(
            val items: ImmutableMap<Icon, UiRichText>,
        ) : BottomContent {
            enum class Icon {
                Location,
                Url,
                Verify,
            }
        }
    }

    enum class Mark {
        Verified,
        Cat,
        Bot,
        Locked,
    }
}

fun createSampleUser(): UiProfile =
    UiProfile(
        key = MicroBlogKey("sampleKey", "sampleHost"),
        handle = "@sampleUser",
        avatar = "https://example.com/avatar.jpg",
        name = Element("span").toUi(),
        platformType = PlatformType.Mastodon,
        onClicked = { /* Handle click */ },
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
