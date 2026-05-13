package dev.dimension.flare.ui.model

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UiProfileMergeTest {
    @Test
    fun `merge keeps existing xqt location when newer profile omits it`() {
        val existing =
            createProfile(
                bottomContent =
                    UiProfile.BottomContent.Iconify(
                        items =
                            persistentMapOf(
                                UiProfile.BottomContent.Iconify.Icon.Location to "Tokyo".toUiPlainText(),
                                UiProfile.BottomContent.Iconify.Icon.Url to "example.com".toUiPlainText(),
                            ),
                    ),
            )
        val latest =
            createProfile(
                bottomContent =
                    UiProfile.BottomContent.Iconify(
                        items =
                            persistentMapOf(
                                UiProfile.BottomContent.Iconify.Icon.Url to "new.example.com".toUiPlainText(),
                            ),
                    ),
            )

        val merged = latest.mergeWith(existing)
        val bottomContent = assertIs<UiProfile.BottomContent.Iconify>(merged.bottomContent)

        assertEquals("Tokyo", bottomContent.items.getValue(UiProfile.BottomContent.Iconify.Icon.Location).raw)
        assertEquals("new.example.com", bottomContent.items.getValue(UiProfile.BottomContent.Iconify.Icon.Url).raw)
    }

    @Test
    fun `merge keeps existing bottom content when newer profile has none`() {
        val existing =
            createProfile(
                bottomContent =
                    UiProfile.BottomContent.Iconify(
                        items =
                            persistentMapOf(
                                UiProfile.BottomContent.Iconify.Icon.Location to "Tokyo".toUiPlainText(),
                            ),
                    ),
            )
        val latest = createProfile(bottomContent = null)

        val merged = latest.mergeWith(existing)
        val bottomContent = assertIs<UiProfile.BottomContent.Iconify>(merged.bottomContent)

        assertEquals("Tokyo", bottomContent.items.getValue(UiProfile.BottomContent.Iconify.Icon.Location).raw)
    }

    private fun createProfile(bottomContent: UiProfile.BottomContent?) =
        UiProfile(
            key = MicroBlogKey(id = "user", host = "x.com"),
            handle = UiHandle(raw = "user", host = "x.com"),
            avatar = "https://example.com/avatar.jpg",
            nameInternal = "User".toUiPlainText(),
            platformType = PlatformType.xQt,
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(fansCount = 1, followsCount = 2, statusesCount = 3),
            mark = persistentListOf(),
            bottomContent = bottomContent,
        )
}
