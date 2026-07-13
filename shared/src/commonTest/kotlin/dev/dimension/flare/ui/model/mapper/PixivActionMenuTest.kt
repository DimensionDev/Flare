package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PixivActionMenuTest {
    private val statusKey = MicroBlogKey("42", "pixiv.net")
    private val accountKey = MicroBlogKey("7", "pixiv.net")

    @Test
    fun unfavouritedWorkUsesFavouriteSemanticsAndHeartIcon() {
        val action =
            ActionMenu.pixivFavourite(
                statusKey = statusKey,
                favourited = false,
                count = 10,
                accountKey = accountKey,
            )

        assertEquals(UiIcon.Like, action.icon)
        assertEquals(
            ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Favorite),
            action.text,
        )
        assertEquals(PostActionFamily.Favorite, action.actionFamily)
        assertNull(action.color)
    }

    @Test
    fun favouritedWorkUsesUnfavouriteSemanticsAndFilledHeartIcon() {
        val action =
            ActionMenu.pixivFavourite(
                statusKey = statusKey,
                favourited = true,
                count = 11,
                accountKey = accountKey,
            )

        assertEquals(UiIcon.Unlike, action.icon)
        assertEquals(
            ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.UnFavorite),
            action.text,
        )
        assertEquals(PostActionFamily.Favorite, action.actionFamily)
        assertEquals(ActionMenu.Item.Color.Red, action.color)
    }
}
