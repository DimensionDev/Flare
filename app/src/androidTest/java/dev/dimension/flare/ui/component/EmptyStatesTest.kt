package dev.dimension.flare.ui.component

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import dev.dimension.flare.R
import dev.dimension.flare.common.collectPagingState
import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.data.model.appearance.GlobalAppearance
import dev.dimension.flare.data.model.appearance.TimelineAppearance
import dev.dimension.flare.data.model.tab.UiTimelineTabItem
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.common.items
import dev.dimension.flare.ui.common.itemsIndexed
import dev.dimension.flare.ui.component.status.ListEmptyView
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.screen.media.MediaViewerScreen
import dev.dimension.flare.ui.screen.rss.OPMLImportSheet
import dev.dimension.flare.ui.screen.settings.AllTabsPresenter
import dev.dimension.flare.ui.screen.settings.TabAddBottomSheet
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
class EmptyStatesTest {
    private val originalMediaQueryIntegration = androidx.compose.ui.ComposeUiFlags.isMediaQueryIntegrationEnabled

    @org.junit.After
    fun restoreMediaQueryIntegration() {
        androidx.compose.ui.ComposeUiFlags.isMediaQueryIntegrationEnabled = originalMediaQueryIntegration
    }

    init {
        androidx.compose.ui.ComposeUiFlags.isMediaQueryIntegrationEnabled = true
    }

    @get:Rule val compose = createComposeRule()

    @Test
    fun searchWaitsForBothResultsAndRemovesEmptyWhenAUserAppears() {
        val users = MutableSharedFlow<List<UiProfile>>(replay = 1)
        val posts = MutableSharedFlow<List<UiTimelineV2>>(replay = 1)
        compose.setContent {
            val userState by users.collectPagingState()
            val postState by posts.collectPagingState()
            TestTheme {
                LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Fixed(2)) {
                    searchContent(userState, postState, {})
                }
            }
        }
        val empty = compose.onNodeWithText(label(R.string.empty_search_results))
        empty.assertDoesNotExist()
        compose.runOnIdle { users.tryEmit(emptyList()) }
        empty.assertDoesNotExist()
        compose.runOnIdle { posts.tryEmit(emptyList()) }
        empty.assertIsDisplayed()
        compose.runOnIdle { users.tryEmit(listOf(profile())) }
        empty.assertDoesNotExist()
        compose.onNodeWithText("Search result").assertIsDisplayed()
    }

    @Test
    fun failedSearchIsNotReportedAsNoResults() {
        val users = flow<List<UiProfile>> { error("Search failed") }
        val posts = flowOf(emptyList<UiTimelineV2>())
        compose.setContent {
            val userState by users.collectPagingState()
            val postState by posts.collectPagingState()
            TestTheme {
                LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Fixed(2)) {
                    searchContent(userState, postState, {})
                }
            }
        }
        compose.onNodeWithText(label(R.string.empty_search_results)).assertDoesNotExist()
    }

    @Test
    fun bothPagingGridOverloadsGiveEmptyContentTheWholeRow() {
        val data = MutableStateFlow(emptyList<Int>())
        var indexed by mutableStateOf(false)
        compose.setContent {
            val state by data.collectPagingState()
            TestTheme {
                Box(Modifier.size(320.dp, 200.dp).testTag("grid")) {
                    LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Fixed(2), modifier = Modifier.fillMaxSize()) {
                        if (indexed) {
                            itemsIndexed(state, emptyContent = { ListEmptyView(Modifier.fillMaxWidth().testTag("empty")) }) { _, _, _ -> }
                        } else {
                            items(state, emptyContent = { ListEmptyView(Modifier.fillMaxWidth().testTag("empty")) }) { }
                        }
                    }
                }
            }
        }

        fun assertWidth() {
            val grid = compose.onNodeWithTag("grid").getUnclippedBoundsInRoot()
            val empty = compose.onNodeWithTag("empty").getUnclippedBoundsInRoot()
            assertEquals((grid.right - grid.left).value, (empty.right - empty.left).value, 1f)
        }
        assertWidth()
        compose.runOnIdle { indexed = true }
        assertWidth()
    }

    @Test
    fun emojiPickerDistinguishesAnEmptyCatalogFromNoSearchMatches() {
        compose.setContent {
            TestTheme {
                EmojiPicker(data = persistentMapOf(), onEmojiSelected = {}, accountType = AccountType.Guest)
            }
        }
        compose.onNodeWithText(label(R.string.empty_emojis)).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement("unmatched")
        compose.onNodeWithText(label(R.string.empty_emojis)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.empty_emoji_search)).assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextReplacement("")
        compose.onNodeWithText(label(R.string.empty_emojis)).assertIsDisplayed()
    }

    @Test
    fun mediaViewerShowsEmptyOnlyAfterSuccessAndCanStillBeClosed() {
        var media by mutableStateOf<UiState<ImmutableList<UiMedia>>>(UiState.Loading())
        var dismissed = false
        compose.setContent {
            TestTheme {
                MediaViewerScreen(
                    medias = media,
                    initialIndex = 0,
                    preview = null,
                    previewProxy = ProxyRef.Global,
                    onDismiss = { dismissed = true },
                    toAltText = {},
                    uriHandler = LocalUriHandler.current,
                    fileName = { "unused" },
                    fileNames = { emptyMap() },
                )
            }
        }
        compose.onNodeWithText(label(R.string.empty_media)).assertDoesNotExist()
        compose.runOnIdle { media = UiState.Success(persistentListOf()) }
        val emptyMessage = compose.onNodeWithText(label(R.string.empty_media))
        emptyMessage.assertIsDisplayed()
        val pixels = emptyMessage.captureToImage().toPixelMap()
        assertTrue(
            "Empty media text must remain visible on the dark viewer background",
            (0 until pixels.height).any { y ->
                (0 until pixels.width).any { x -> pixels[x, y].luminance() > 0.5f }
            },
        )
        compose.onNodeWithContentDescription(label(R.string.navigate_back)).performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun emptyOpmlImportShowsAResultAndKeepsTheDoneAction() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("empty-subscriptions", ".opml", context.cacheDir)
        file.writeText("""<?xml version="1.0"?><opml version="2.0"><head><title>Empty</title></head><body/></opml>""")
        var dismissed = false
        try {
            compose.setContent {
                TestTheme { OPMLImportSheet(Uri.fromFile(file), onBack = { dismissed = true }) }
            }
            compose.waitUntil(5_000) {
                compose
                    .onAllNodes(
                        androidx.compose.ui.test
                            .hasText(label(R.string.empty_opml_import)),
                    ).fetchSemanticsNodes()
                    .isNotEmpty()
            }
            compose.onNodeWithText(label(R.string.empty_opml_import)).assertIsDisplayed()
            compose.onNodeWithText(label(android.R.string.ok)).performClick()
            compose.runOnIdle { assertTrue(dismissed) }
        } finally {
            file.delete()
        }
    }

    @Test
    fun addTabSheetWaitsForResolvedCatalogBeforeShowingEmpty() {
        var loaded by mutableStateOf(false)
        compose.setContent {
            val state =
                object : AllTabsPresenter.State {
                    override val rssTabs = persistentListOf<UiTimelineTabItem>()
                    override val flattenedAccountTabs: UiState<ImmutableList<AllTabsPresenter.State.FlattenedAccountTabs>> =
                        UiState.Success(
                            persistentListOf<AllTabsPresenter.State.FlattenedAccountTabs>(),
                        )
                    override val accountTabs: UiState<ImmutableList<AllTabsPresenter.State.AccountTabs>> =
                        UiState.Success(
                            persistentListOf(AllTabsPresenter.State.AccountTabs(profile(), persistentListOf(), tabsLoaded = loaded)),
                        )
                }
            TestTheme {
                TabAddBottomSheet(
                    tabs = persistentListOf(),
                    allTabs = state,
                    onDismissRequest = {},
                    onAddTab = {},
                    onDeleteTab = {},
                    toAddRssSource = {},
                )
            }
        }
        compose.onNodeWithText("Search result").performClick()
        val empty = compose.onNodeWithText(label(R.string.empty_tab_candidates))
        empty.assertDoesNotExist()
        compose.runOnIdle { loaded = true }
        empty.assertIsDisplayed()
        compose.runOnIdle { loaded = false }
        empty.assertDoesNotExist()
    }

    @Composable
    private fun TestTheme(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalGlobalAppearance provides GlobalAppearance(),
            LocalTimelineAppearance provides TimelineAppearance(),
        ) {
            MaterialTheme(content = content)
        }
    }

    private fun label(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun profile() =
        UiProfile(
            key = MicroBlogKey("search-result", "example.com"),
            handle = UiHandle("search-result", "example.com"),
            avatar = null,
            nameInternal = "Search result".toUiPlainText(),
            platformId = "mastodon",
            clickEvent = ClickEvent.Noop,
            banner = null,
            description = null,
            matrices = UiProfile.Matrices(0, 0, 0),
            mark = persistentListOf(),
            bottomContent = null,
        )
}
