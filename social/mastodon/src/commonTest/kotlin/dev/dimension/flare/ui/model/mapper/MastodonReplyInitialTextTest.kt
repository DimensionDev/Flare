package dev.dimension.flare.ui.model.mapper

import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.Mention
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.data.platform.MastodonPlatformSpec
import dev.dimension.flare.model.ComposeInitialTextContext
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.plugin.module.dsl.modules
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class MastodonReplyInitialTextTest {
    private val accountKey = MicroBlogKey("1", "mastodon.social")
    private val mentions =
        listOf(
            Mention(id = "200", username = "bob", acct = "bob@remote.example", url = "https://remote.example/@bob"),
            Mention(id = "201", username = "bob", acct = "bob", url = "https://mastodon.social/@bob"),
            Mention(id = "1", username = "me", acct = "me", url = "https://mastodon.social/@me"),
            Mention(id = "100", username = "author", acct = "author", url = "https://mastodon.social/@author"),
        )

    @BeforeTest
    fun setup() {
        startKoin { modules(MastodonRenderTestModule::class) }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun replyIncludesRemoteAndLocalMentionsWithoutDuplicatesOrSelf() {
        val post = render(mentions + mentions.first())
        val result = assertNotNull(MastodonPlatformSpec.resolveInitialText(context(post)))

        assertEquals("@author@mastodon.social @bob@remote.example @bob@mastodon.social ", result.text)
        assertEquals(result.text.length, result.cursorPosition)
    }

    @Test
    fun replyingToOwnPostStillIncludesOtherParticipants() {
        val post = render(mentions)
        val result =
            assertNotNull(
                MastodonPlatformSpec.resolveInitialText(
                    context(post).copy(
                        currentUserHandle = UiHandle("author", accountKey.host),
                        selectedAccountKey = MicroBlogKey("100", accountKey.host),
                    ),
                ),
            )

        assertEquals("@bob@remote.example @bob@mastodon.social @me@mastodon.social ", result.text)
    }

    @Test
    fun oldCachedPostWaitsForParticipantMetadataBeforePrefill() {
        val post = render(mentions)
        val stored = JSON.encodeToJsonElement(UiTimelineV2.Post.serializer(), post).jsonObject
        val legacy = JSON.decodeFromJsonElement(UiTimelineV2.Post.serializer(), JsonObject(stored - "replyMentions"))

        assertNull(MastodonPlatformSpec.resolveInitialText(context(legacy)))
        assertNotNull(MastodonPlatformSpec.resolveInitialText(context(post)))
    }

    @Test
    fun postWithoutMentionsPrefillsOnlyAuthorAndQuoteHasNoPrefill() {
        val post = render(emptyList())

        assertEquals("@author@mastodon.social ", MastodonPlatformSpec.resolveInitialText(context(post))?.text)
        assertNull(MastodonPlatformSpec.resolveInitialText(context(post).copy(composeType = ComposeType.Quote)))
    }

    private fun render(mentions: List<Mention>): UiTimelineV2.Post =
        assertIs<UiTimelineV2.Post>(
            Status(
                id = "1000",
                createdAt = Instant.parse("2026-10-27T05:37:19Z"),
                account = Account(id = "100", username = "author", acct = "author"),
                content =
                    "<p>" +
                        mentions.joinToString(" ") {
                            """<a href="${it.url}" class="u-url mention">@<span>${it.username}</span></a>"""
                        } +
                        "</p>",
                mentions = mentions,
            ).render(accountKey.host, accountKey),
        )

    private fun context(post: UiTimelineV2.Post) =
        ComposeInitialTextContext(
            post = post,
            quotes = persistentListOf(),
            composeType = ComposeType.Reply,
            currentUserHandle = UiHandle("me", accountKey.host),
            selectedAccountKey = accountKey,
        )
}
