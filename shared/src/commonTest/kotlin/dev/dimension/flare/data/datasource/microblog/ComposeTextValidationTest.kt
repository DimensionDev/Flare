package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ComposeTextValidationTest {
    @Test
    fun publishingResolvesAnUnspecifiedAudienceToThePlatformDefault() =
        runTest {
            val config = ComposeConfig.Visibility(defaultVisibility = UiTimelineV2.Post.Visibility.Followers)
            val source = TestComposeSource(ComposeConfig.Text(100), config)
            source.compose(ComposeData("post")) {}
            assertEquals(UiTimelineV2.Post.Visibility.Followers, source.lastPublished?.visibility)
        }

    @Test
    fun publishingWithoutAudienceSupportUsesPublic() =
        runTest {
            val source = TestComposeSource(ComposeConfig.Text(100))
            source.compose(ComposeData("post")) {}
            assertEquals(UiTimelineV2.Post.Visibility.Public, source.lastPublished?.visibility)
        }

    @Test
    fun publishingRejectsUnsupportedVisibilityBeforeInvokingPlatform() =
        runTest {
            val source = TestComposeSource(ComposeConfig.Text(100))
            for (visibility in listOf(UiTimelineV2.Post.Visibility.SelfOnly, UiTimelineV2.Post.Visibility.Friends)) {
                assertFailsWith<IllegalArgumentException> { source.compose(ComposeData("post", visibility = visibility)) {} }
            }
            assertEquals(0, source.published)
        }

    @Test
    fun publishingValidatesVisibilityForTheActualComposeType() =
        runTest {
            val config =
                ComposeConfig.Visibility(
                    allVisibilities = persistentListOf(UiTimelineV2.Post.Visibility.Public, UiTimelineV2.Post.Visibility.SelfOnly),
                )
            val source = TestComposeSource(ComposeConfig.Text(100), config)
            val data = ComposeData("post", visibility = UiTimelineV2.Post.Visibility.SelfOnly)
            source.compose(data) {}
            assertEquals(1, source.published)
            assertFailsWith<IllegalArgumentException> {
                source.compose(
                    data.copy(referenceStatus = ComposeData.ReferenceStatus(ComposeStatus.Reply(MicroBlogKey("post", "example.com")))),
                ) {}
            }
            assertEquals(1, source.published)
        }

    @Test
    fun publishingChecksFinalInputBeforeInvokingPlatform() =
        runTest {
            val rule =
                ComposeConfig.Text.withValidation(3) { content, spoilerText ->
                    ComposeConfig.Text.Check(
                        remainingLength = 3 - content.length,
                        isValid = content.length <= 3 && spoilerText == null,
                    )
                }
            val source = TestComposeSource(rule)
            assertFailsWith<IllegalArgumentException> { source.compose(ComposeData("long")) {} }
            assertFailsWith<IllegalArgumentException> { source.compose(ComposeData("ok", spoilerText = "cw")) {} }
            assertEquals(0, source.published)
            source.compose(ComposeData("ok", spoilerText = " ")) {}
            assertEquals(1, source.published)
        }

    @Test
    fun publishingUsesTheCurrentAvailableLimit() =
        runTest {
            val limits = MutableStateFlow(500)
            val rule = ComposeConfig.Text(limits)
            assertFalse(rule.remainingLength("a".repeat(3000)).first().isValid)
            val source = TestComposeSource(rule)
            assertFailsWith<IllegalArgumentException> { source.compose(ComposeData("a".repeat(3000))) {} }
            assertEquals(0, source.published)
            limits.value = 4000
            source.compose(ComposeData("a".repeat(3000))) {}
            assertEquals(1, source.published)
        }
}

private class TestComposeSource(
    private val rule: ComposeConfig.Text,
    private val visibility: ComposeConfig.Visibility? = null,
) : ComposeDataSource {
    override val accountKey = MicroBlogKey("test", "example.com")
    var published = 0
    var lastPublished: ComposeData? = null

    override suspend fun publish(
        data: ComposeData,
        progress: () -> Unit,
    ) {
        published++
        lastPublished = data
    }

    override fun composeConfig(type: ComposeType) =
        ComposeConfig(text = rule, visibility = visibility.takeUnless { type == ComposeType.Reply })

    override fun homeTimeline(): Nothing = error("unused")

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): Nothing = error("unused")

    override fun context(statusKey: MicroBlogKey): Nothing = error("unused")

    override fun searchStatus(query: String): Nothing = error("unused")

    override fun searchUser(query: String): Nothing = error("unused")

    override fun discoverUsers(): Nothing = error("unused")

    override fun discoverStatuses(): Nothing = error("unused")

    override fun discoverHashtags(): Nothing = error("unused")

    override fun following(userKey: MicroBlogKey): Nothing = error("unused")

    override fun fans(userKey: MicroBlogKey): Nothing = error("unused")

    override suspend fun profileTabs(userKey: MicroBlogKey): Nothing = error("unused")
}
