package dev.dimension.flare.feature.agent.presenter

import dev.dimension.flare.data.model.ProxyRef
import dev.dimension.flare.feature.agent.common.AgentFailure
import dev.dimension.flare.feature.agent.common.AgentFailureKind
import dev.dimension.flare.feature.agent.common.AgentInputRequest
import dev.dimension.flare.feature.agent.common.AgentInputRequestOptionButtonType
import dev.dimension.flare.feature.agent.common.AgentPendingInputRequest
import dev.dimension.flare.feature.agent.common.AgentRetryMode
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.time.Instant

class AgentMediaSnapshotTest {
    @Test
    fun profileCardsRetainTheirViewerScopeWithoutUsingTheProfileOwner() {
        for (proxy in listOf(ProxyRef.Account(VIEWER), ProxyRef.Global, ProxyRef.System)) {
            val original = profile(proxy)
            val restored = assertIs<AgentMessagePart.UserCard>(roundTrip(AgentMessagePart.UserCard(original))).user
            assertEquals(original.avatar, restored.avatar)
            assertEquals(original.banner, restored.banner)
            assertEquals(
                proxy,
                restored.description!!
                    .imageMedias
                    .single()
                    .proxy,
            )
        }
    }

    @Test
    fun profileWithOnlyInlineMediaRetainsItsScope() {
        val proxy = ProxyRef.Account(VIEWER)
        val original = profile(proxy).copy(avatar = null, banner = null)
        val restored = assertIs<AgentMessagePart.UserCard>(roundTrip(AgentMessagePart.UserCard(original))).user
        assertEquals(
            proxy,
            restored.description!!
                .imageMedias
                .single()
                .proxy,
        )
    }

    @Test
    fun postCardsRestoreMediaFromThePostsViewerAccount() {
        val original = post()
        val restored = assertIs<AgentMessagePart.PostCard>(roundTrip(AgentMessagePart.PostCard(original))).post
        assertEquals(original.images.single(), restored.images.single())
        assertEquals(original.user!!.avatar, restored.user!!.avatar)
        assertEquals(original.user!!.banner, restored.user!!.banner)
    }

    @Test
    fun confirmationPreviewsKeepDifferentAccountScopes() {
        val other = ProxyRef.Account(MicroBlogKey("other-viewer", "other.example"))
        val first = profile(ProxyRef.Account(VIEWER))
        val second = profile(other)
        val request =
            AgentInputRequest(
                requestId = "choose",
                options =
                    listOf(
                        AgentInputRequest.Option("one", "One", AgentInputRequestOptionButtonType.Secondary, "one", userPreview = first),
                        AgentInputRequest.Option(
                            "two",
                            "Two",
                            AgentInputRequestOptionButtonType.Secondary,
                            "two",
                            userPreview = second,
                            postPreview = post(),
                        ),
                    ),
                userPreview = first,
                postPreview = post(),
            )
        val restored = assertIs<AgentMessagePart.Actions>(roundTrip(AgentMessagePart.Actions(request))).request
        assertEquals(first.avatar, restored.userPreview!!.avatar)
        assertEquals(first.avatar, restored.options[0].userPreview!!.avatar)
        assertEquals(second.avatar, restored.options[1].userPreview!!.avatar)
        assertEquals(
            post().images.single(),
            restored.options[1]
                .postPreview!!
                .images
                .single(),
        )
    }

    @Test
    fun retrySnapshotsKeepMediaScopes() {
        val user = profile(ProxyRef.Account(VIEWER))
        val pending =
            AgentPendingInputRequest(
                "confirm",
                listOf(AgentPendingInputRequest.Option("yes", "yes", userPreview = user, postPreview = post())),
                userPreview = user,
                postPreview = post(),
            )
        val failure =
            AgentFailure(
                AgentFailureKind.Network,
                "fixture",
                AgentRetryMode.Resume,
                supportingParts = listOf(AgentMessagePart.UserCard(user)),
                inputRequest = pending,
            )
        val restored = AgentFailure.decode(failure.encode())
        assertEquals(user.avatar, assertIs<AgentMessagePart.UserCard>(restored.supportingParts.single()).user.avatar)
        assertEquals(user.avatar, restored.inputRequest!!.userPreview!!.avatar)
        assertEquals(
            user.avatar,
            restored.inputRequest.options
                .single()
                .userPreview!!
                .avatar,
        )
        assertEquals(
            post().images.single(),
            restored.inputRequest.postPreview!!
                .images
                .single(),
        )
    }

    @Test
    fun sharedProfileCacheStillExcludesViewerScope() {
        val encoded = Json.encodeToString(profile(ProxyRef.Account(VIEWER)))
        assertFalse(encoded.contains("_agentMediaProxy"))
        assertFalse(encoded.contains(VIEWER.id))
    }

    @Test
    fun legacyProfilesWithoutViewerMetadataRemainUnbound() {
        val legacy = """{"type":"dev.dimension.flare.feature.agent.presenter.AgentMessagePart.UserCard","user":${Json.encodeToString(
            profile(ProxyRef.Account(VIEWER)),
        )}}"""
        val restored = assertIs<AgentMessagePart.UserCard>(Json.decodeFromString<AgentMessagePart>(legacy))
        assertEquals(ProxyRef.Unbound, restored.user.avatar!!.proxy)
    }

    @Test
    fun mixedProfileScopesAreNotReroutedToAnArbitraryAccount() {
        val original = profile(ProxyRef.Account(VIEWER)).copy(banner = image(ProxyRef.Global))
        val restored = assertIs<AgentMessagePart.UserCard>(roundTrip(AgentMessagePart.UserCard(original)))
        assertEquals(ProxyRef.Unbound, restored.user.avatar!!.proxy)
        assertEquals(ProxyRef.Unbound, restored.user.banner!!.proxy)
    }

    private fun roundTrip(part: AgentMessagePart): AgentMessagePart = Json.decodeFromString<AgentMessagePart>(Json.encodeToString(part))

    private fun image(proxy: ProxyRef) =
        UiMedia.Image(
            url = "https://media.example/image.png",
            previewUrl = "https://media.example/preview.png",
            description = null,
            width = 100f,
            height = 100f,
            sensitive = false,
            customHeaders = persistentMapOf("X-Fixture" to "fixture"),
            proxy = proxy,
        )

    private fun profile(proxy: ProxyRef) =
        UiProfile(
            key = MicroBlogKey("profile-owner", "remote.example"),
            handle = UiHandle("profile-owner", "remote.example"),
            avatar = image(proxy),
            nameInternal = "Fixture profile".toUiPlainText(),
            platformId = "Mastodon",
            clickEvent = ClickEvent.Noop,
            banner = image(proxy),
            description =
                UiRichText(
                    renderRuns =
                        persistentListOf(
                            RenderContent.Text(persistentListOf(RenderRun.Image("https://media.example/emoji.png", "emoji", proxy))),
                        ),
                    isRtl = false,
                    raw = ":emoji:",
                    innerText = ":emoji:",
                    imageUrls = persistentListOf("https://media.example/emoji.png"),
                ),
            matrices = UiProfile.Matrices(1, 2, 3),
            mark = persistentListOf(),
            bottomContent = null,
        )

    private fun post() =
        UiTimelineV2.Post(
            platformId = "Mastodon",
            images = persistentListOf(image(ProxyRef.Account(VIEWER))),
            sensitive = false,
            contentWarning = null,
            user = profile(ProxyRef.Account(VIEWER)),
            content = UiTranslatableText("Fixture post".toUiPlainText()),
            actions = persistentListOf(),
            poll = null,
            statusKey = MicroBlogKey("post", "remote.example"),
            card = null,
            createdAt = Instant.fromEpochMilliseconds(0).toUi(),
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Specific(VIEWER),
        )

    private companion object {
        val VIEWER = MicroBlogKey("viewer-account", "viewer.example")
    }
}
