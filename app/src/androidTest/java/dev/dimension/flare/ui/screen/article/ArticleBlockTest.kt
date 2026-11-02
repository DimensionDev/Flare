package dev.dimension.flare.ui.screen.article

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.dimension.flare.ui.model.UiArticleBlock
import dev.dimension.flare.ui.model.UiArticleContentGateReason
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArticleBlockTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val url = "https://www.fanbox.cc/@npyu/posts/9963413"
    private val handledUrls = mutableListOf<String>()
    private val externalUrls = mutableListOf<String>()

    @Test
    fun clickingEmbedCardSendsTheOriginalUrlToTheInAppHandler() {
        show(UiArticleBlock.Embed(key = "embed", url = url, title = "Fanbox post"))

        composeRule.onNodeWithText("Fanbox post").performClick()

        assertEquals(listOf(url), handledUrls)
        assertEquals(emptyList<String>(), externalUrls)
    }

    @Test
    fun tappingDisplayedEmbedUrlUsesTheInAppHandler() {
        show(UiArticleBlock.Embed(key = "embed", url = url, title = "Fanbox post"))

        composeRule.onNodeWithText(url, useUnmergedTree = true).performTouchInput { click() }

        assertEquals(listOf(url), handledUrls)
        assertEquals(emptyList<String>(), externalUrls)
    }

    @Test
    fun contentGateBrowserButtonKeepsUsingTheExternalCallback() {
        show(
            UiArticleBlock.ContentGate(
                key = "gate",
                reason = UiArticleContentGateReason.SubscriptionRequired(platformId = "Fanbox"),
                actionUrl = url,
            ),
        )

        composeRule.onNode(hasClickAction()).performClick()

        assertEquals(emptyList<String>(), handledUrls)
        assertEquals(listOf(url), externalUrls)
    }

    private fun show(block: UiArticleBlock) {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalUriHandler provides
                        object : UriHandler {
                            override fun openUri(uri: String) {
                                handledUrls += uri
                            }
                        },
                ) {
                    ArticleBlock(
                        block = block,
                        onOpenUrl = { externalUrls += it },
                        onDownloadFile = { error("Must not download a file") },
                        onOpenMedia = { error("Must not open media") },
                    )
                }
            }
        }
    }
}
