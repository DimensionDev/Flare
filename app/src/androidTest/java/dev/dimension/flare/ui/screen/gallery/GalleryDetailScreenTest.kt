package dev.dimension.flare.ui.screen.gallery

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.dimension.flare.common.collectPagingState
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDetail
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryOrientation
import dev.dimension.flare.data.model.appearance.GlobalAppearance
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.component.LocalGlobalAppearance
import dev.dimension.flare.ui.component.LocalTimelineAppearance
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
class GalleryDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun addingBookmarkInInfoSheetUsesTheAppUriHandler() {
        bookmarkUsesAppUriHandler(bookmarked = false, showInfoSheet = true)
    }

    @Test
    fun removingBookmarkInInfoSheetUsesTheAppUriHandler() {
        bookmarkUsesAppUriHandler(bookmarked = true, showInfoSheet = true)
    }

    @Test
    fun bookmarkOutsideInfoSheetKeepsUsingTheAppUriHandler() {
        bookmarkUsesAppUriHandler(bookmarked = false, showInfoSheet = false)
    }

    private fun bookmarkUsesAppUriHandler(
        bookmarked: Boolean,
        showInfoSheet: Boolean,
    ) {
        val accountKey = MicroBlogKey("account", "pixiv.net")
        val statusKey = MicroBlogKey("126125371", "pixiv.net")
        val bookmarkAction =
            ClickEvent.event(
                accountKey,
                PostEvent.Pixiv.Bookmark(
                    postKey = statusKey,
                    bookmarked = bookmarked,
                    accountKey = accountKey,
                ),
            ) as ClickEvent.Deeplink
        val detail =
            GalleryDetail(
                orientation = GalleryOrientation.Vertical,
                statusKey = statusKey,
                accountType = AccountType.Specific(accountKey),
                url = "https://www.pixiv.net/artworks/${statusKey.id}",
                images = persistentListOf(),
                title = "Gallery bookmark regression",
                author = null,
                createdAt = Instant.fromEpochMilliseconds(0).toUi(),
                content = null,
                isBookmarked = bookmarked,
                bookmarkAction = bookmarkAction,
                matrix = persistentListOf(),
            )
        val handledUrls = mutableListOf<String>()
        compose.setContent {
            val emptyItems by remember { flowOf(emptyList<UiTimelineV2>()) }.collectPagingState()
            CompositionLocalProvider(
                LocalGlobalAppearance provides GlobalAppearance(),
                LocalTimelineAppearance provides TimelineAppearance(),
                LocalUriHandler provides
                    object : UriHandler {
                        override fun openUri(uri: String) {
                            handledUrls += uri
                        }
                    },
            ) {
                MaterialTheme {
                    CompactGalleryContent(
                        detail = detail,
                        comments = emptyItems,
                        recommendations = emptyItems,
                        navigate = { error("Bookmark must use the app URI handler") },
                        contentPadding = PaddingValues(),
                        showInfoSheet = showInfoSheet,
                        onDismissInfoSheet = {},
                    )
                }
            }
        }

        val bookmark = hasClickAction() and isEnabled() and hasAnyAncestor(hasAnySibling(hasText(detail.title)))
        val inSheet = hasAnyAncestor(isDialog())
        compose.onNode(bookmark and if (showInfoSheet) inSheet else !inSheet).performClick()

        compose.runOnIdle {
            assertEquals(listOf(bookmarkAction.url), handledUrls)
        }
    }
}
