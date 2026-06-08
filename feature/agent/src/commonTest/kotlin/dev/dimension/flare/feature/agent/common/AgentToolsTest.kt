package dev.dimension.flare.feature.agent.common

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

internal class AgentToolsTest {
    @Test
    fun emptyPlatformListSearchesEveryTarget() {
        val targets = agentSearchTargets()

        assertEquals(targets, targets.filterByPlatformNames(emptyList()))
    }

    @Test
    fun allPlatformAliasesSearchEveryTarget() {
        val targets = agentSearchTargets()

        assertEquals(targets, targets.filterByPlatformNames(listOf("all")))
        assertEquals(targets, targets.filterByPlatformNames(listOf("全平台")))
        assertEquals(targets, targets.filterByPlatformNames(listOf("跨平台")))
    }

    @Test
    fun platformAliasesResolveToSpecificTargets() {
        val targets = agentSearchTargets()

        assertEquals(listOf(targets[0]), targets.filterByPlatformNames(listOf("微博")))
        assertEquals(listOf(targets[1]), targets.filterByPlatformNames(listOf("twitter")))
        assertEquals(listOf(targets[1]), targets.filterByPlatformNames(listOf("推特")))
        assertEquals(listOf(targets[2]), targets.filterByPlatformNames(listOf("蓝天")))
    }

    @Test
    fun unknownPlatformDoesNotFallBackToEveryTarget() {
        val targets = agentSearchTargets()

        assertTrue(targets.filterByPlatformNames(listOf("unknown-social-network")).isEmpty())
    }

    @Test
    fun composeToolRequestsConfirmationBeforePublishing() =
        runTest {
            val dataSource = StubComposeDataSource(accountKey = MicroBlogKey("alice", "example.social"))
            val inputRequestStore = AgentToolInputRequestStore()
            val tool = composePostTool(dataSource, inputRequestStore)

            val result =
                tool.execute(
                    ComposePostTool.Args(
                        content = "hello from agent",
                        accountId = "alice",
                        accountHost = "example.social",
                    ),
                )

            assertTrue(result.contains("确认使用以下账号发送以下内容吗？"))
            assertTrue(result.contains("账号 Key：alice@example.social"))
            assertTrue(result.contains("hello from agent"))
            assertFalse(dataSource.composed)
            val inputRequest = assertNotNull(inputRequestStore.snapshot())
            assertEquals("取消", inputRequest.options.first { it.id == "cancel" }.label)
            assertEquals("确认发送", inputRequest.options.first { it.id == "confirm" }.label)
            assertEquals("hello from agent", assertNotNull(inputRequest.postPreview).content.raw)
        }

    @Test
    fun composeToolRequestsAccountSelectionWhenMultipleAccountsMatch() =
        runTest {
            val alice = StubComposeDataSource(accountKey = MicroBlogKey("alice", "example.social"))
            val bob = StubComposeDataSource(accountKey = MicroBlogKey("bob", "example.social"))
            val inputRequestStore = AgentToolInputRequestStore()
            val tool =
                ComposePostTool(
                    AgentToolSession(
                        status = null,
                        searchTargets = emptyList(),
                        composeTargets =
                            listOf(
                                AgentComposeTarget(
                                    accountKey = alice.accountKey,
                                    platformType = PlatformType.Mastodon,
                                    dataSource = alice,
                                ),
                                AgentComposeTarget(
                                    accountKey = bob.accountKey,
                                    platformType = PlatformType.Mastodon,
                                    dataSource = bob,
                                ),
                            ),
                        postActionTargets = emptyList(),
                        userTargets = emptyList(),
                        attachmentStore = AgentToolAttachmentStore(),
                        inputRequestStore = inputRequestStore,
                    ),
                )

            val result =
                tool.execute(
                    ComposePostTool.Args(
                        content = "hello from agent",
                        platforms = listOf("mastodon"),
                    ),
                )

            assertEquals("请选择用于发送这条内容的账号。", result)
            assertFalse(alice.composed)
            assertFalse(bob.composed)
            val inputRequest = assertNotNull(inputRequestStore.snapshot())
            assertEquals(2, inputRequest.options.size)
            assertTrue(
                inputRequest.options
                    .first()
                    .value
                    .contains("accountId=alice"),
            )
            assertTrue(
                inputRequest.options
                    .first()
                    .value
                    .contains("hello from agent"),
            )
        }

    @Test
    fun composeToolRequestsPlatformSelectionWhenMultiplePlatformsMatch() =
        runTest {
            val mastodon = StubComposeDataSource(accountKey = MicroBlogKey("masto", "example.social"))
            val twitter = StubComposeDataSource(accountKey = MicroBlogKey("x", "x.com"))
            val weibo = StubComposeDataSource(accountKey = MicroBlogKey("weibo", "weibo.com"))
            val inputRequestStore = AgentToolInputRequestStore()
            val tool =
                ComposePostTool(
                    AgentToolSession(
                        status = null,
                        searchTargets = emptyList(),
                        composeTargets =
                            listOf(
                                AgentComposeTarget(
                                    accountKey = mastodon.accountKey,
                                    platformType = PlatformType.Mastodon,
                                    dataSource = mastodon,
                                ),
                                AgentComposeTarget(
                                    accountKey = twitter.accountKey,
                                    platformType = PlatformType.xQt,
                                    dataSource = twitter,
                                ),
                                AgentComposeTarget(
                                    accountKey = weibo.accountKey,
                                    platformType = PlatformType.VVo,
                                    dataSource = weibo,
                                ),
                            ),
                        postActionTargets = emptyList(),
                        userTargets = emptyList(),
                        attachmentStore = AgentToolAttachmentStore(),
                        inputRequestStore = inputRequestStore,
                    ),
                )

            val result =
                tool.execute(
                    ComposePostTool.Args(
                        content = "hello from agent",
                    ),
                )

            assertEquals("请选择用于发送这条内容的平台。", result)
            assertFalse(mastodon.composed)
            assertFalse(twitter.composed)
            assertFalse(weibo.composed)
            val inputRequest = assertNotNull(inputRequestStore.snapshot())
            assertEquals(3, inputRequest.options.size)
            assertTrue(inputRequest.options.any { it.label == "Twitter/X" && it.value.contains("platforms=xQt") })
            assertTrue(inputRequest.options.any { it.label == "微博" && it.value.contains("platforms=VVo") })
            assertTrue(inputRequest.options.any { it.label == "Mastodon" && it.value.contains("hello from agent") })
        }

    @Test
    fun composeToolPublishesAfterConfirmation() =
        runTest {
            val dataSource = StubComposeDataSource(accountKey = MicroBlogKey("alice", "example.social"))
            val tool = composePostTool(dataSource)

            val result =
                tool.execute(
                    ComposePostTool.Args(
                        content = "hello from agent",
                        accountId = "alice",
                        accountHost = "example.social",
                        confirmed = true,
                    ),
                )

            assertTrue(result.contains("Post sent successfully."))
            assertTrue(dataSource.composed)
            assertEquals("hello from agent", assertNotNull(dataSource.lastData).content)
        }

    @Test
    fun composeToolRequestsReplyConfirmationWithCurrentStatus() =
        runTest {
            val targetPost = createPost(statusKey = MicroBlogKey("post-1", "example.social"))
            val dataSource = StubComposeDataSource(accountKey = MicroBlogKey("alice", "example.social"))
            val inputRequestStore = AgentToolInputRequestStore()
            val tool =
                composePostTool(
                    dataSource = dataSource,
                    inputRequestStore = inputRequestStore,
                    status =
                        AgentToolContext.StatusContext(
                            postDataSource = StubPostDataSource,
                            statusKey = targetPost.statusKey,
                            currentPlatformType = PlatformType.Mastodon,
                            currentPost = targetPost,
                        ),
                )

            val result =
                tool.execute(
                    ComposePostTool.Args(
                        content = "reply from agent",
                        action = "reply",
                        accountId = "alice",
                        accountHost = "example.social",
                        useCurrentStatus = true,
                    ),
                )

            assertTrue(result.contains("确认使用以下账号回复以下帖子，并发送以下内容吗？"))
            assertTrue(result.contains("statusKey：post-1@example.social"))
            assertFalse(dataSource.composed)
            assertNotNull(inputRequestStore.snapshot())
        }

    @Test
    fun composeToolPublishesQuoteAfterConfirmation() =
        runTest {
            val targetPost = createPost(statusKey = MicroBlogKey("post-quote", "example.social"))
            val dataSource = StubComposeDataSource(accountKey = MicroBlogKey("alice", "example.social"))
            val tool =
                composePostTool(
                    dataSource = dataSource,
                    status =
                        AgentToolContext.StatusContext(
                            postDataSource = StubPostDataSource,
                            statusKey = targetPost.statusKey,
                            currentPlatformType = PlatformType.Mastodon,
                            currentPost = targetPost,
                        ),
                )

            val result =
                tool.execute(
                    ComposePostTool.Args(
                        content = "quote from agent",
                        action = "quote",
                        accountId = "alice",
                        accountHost = "example.social",
                        useCurrentStatus = true,
                        confirmed = true,
                    ),
                )

            assertTrue(result.contains("Post sent successfully."))
            assertTrue(dataSource.composed)
            val referenceStatus = assertNotNull(assertNotNull(dataSource.lastData).referenceStatus).composeStatus
            assertTrue(referenceStatus is ComposeStatus.Quote)
            assertEquals(targetPost.statusKey, referenceStatus.statusKey)
        }

    @Test
    fun composeToolRequestsTargetPostSelectionWhenMultipleReferencesExist() =
        runTest {
            val firstPost = createPost(statusKey = MicroBlogKey("post-1", "example.social"), content = "first".toUiPlainText())
            val secondPost = createPost(statusKey = MicroBlogKey("post-2", "example.social"), content = "second".toUiPlainText())
            val dataSource = StubComposeDataSource(accountKey = MicroBlogKey("alice", "example.social"))
            val attachmentStore = AgentToolAttachmentStore()
            val inputRequestStore = AgentToolInputRequestStore()
            attachmentStore.addPosts(listOf(firstPost, secondPost))
            val tool =
                composePostTool(
                    dataSource = dataSource,
                    attachmentStore = attachmentStore,
                    inputRequestStore = inputRequestStore,
                )

            val result =
                tool.execute(
                    ComposePostTool.Args(
                        content = "reply from agent",
                        action = "reply",
                        accountId = "alice",
                        accountHost = "example.social",
                    ),
                )

            assertEquals("请选择要回复的帖子。", result)
            assertFalse(dataSource.composed)
            val inputRequest = assertNotNull(inputRequestStore.snapshot())
            assertEquals(2, inputRequest.options.size)
            assertNotNull(inputRequest.options.first().postPreview)
            assertTrue(
                inputRequest.options
                    .first()
                    .value
                    .contains("targetStatusId=post-1"),
            )
        }

    @Test
    fun userLoaderPlatformAliasesResolveToSpecificTargets() {
        val accountKey = MicroBlogKey("viewer", "x.com")
        val targets =
            listOf(
                AgentUserTarget(
                    accountKey = accountKey,
                    platformType = PlatformType.xQt,
                    dataSource = StubMicroblogDataSource,
                    loadUserById = {
                        error("unused")
                    },
                ),
            )

        assertEquals(targets, targets.filterUserTargetsByPlatformNames(listOf("twitter")))
    }

    @Test
    fun listPostActionsFiltersUiAndTranslateActions() =
        runTest {
            val accountKey = MicroBlogKey("alice", "example.social")
            val targetPost =
                createPost(
                    statusKey = MicroBlogKey("post-1", "example.social"),
                    actions =
                        persistentListOf(
                            postAction(
                                type = ActionMenu.Item.Text.Localized.Type.Like,
                                event =
                                    PostEvent.Mastodon.Like(
                                        postKey = MicroBlogKey("post-1", "example.social"),
                                        liked = false,
                                        accountKey = accountKey,
                                        count = 0,
                                    ),
                                accountKey = accountKey,
                            ),
                            ActionMenu.Item(
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Translate),
                                clickEvent = ClickEvent.Noop,
                            ),
                            ActionMenu.Item(
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                                clickEvent = ClickEvent.Noop,
                            ),
                        ),
                )
            val inputRequestStore = AgentToolInputRequestStore()
            val tool =
                ListPostActionsTool(
                    postActionSession(
                        post = targetPost,
                        dataSource = StubPostActionDataSource,
                        accountKey = accountKey,
                        inputRequestStore = inputRequestStore,
                    ),
                )

            val result = tool.execute(ListPostActionsTool.Args(useCurrentStatus = true))

            assertTrue(result.contains("actionName=Like"))
            assertFalse(result.contains("Translate"))
            assertFalse(result.contains("Share"))
            val request = assertNotNull(inputRequestStore.snapshot())
            assertEquals(1, request.options.size)
            assertEquals("Like", request.options.first().label)
        }

    @Test
    fun executePostActionRequestsConfirmationBeforeHandlingEvent() =
        runTest {
            val accountKey = MicroBlogKey("alice", "example.social")
            val event =
                PostEvent.Mastodon.Like(
                    postKey = MicroBlogKey("post-1", "example.social"),
                    liked = false,
                    accountKey = accountKey,
                    count = 0,
                )
            val targetPost =
                createPost(
                    statusKey = event.postKey,
                    actions =
                        persistentListOf(
                            postAction(
                                type = ActionMenu.Item.Text.Localized.Type.Like,
                                event = event,
                                accountKey = accountKey,
                            ),
                        ),
                )
            val handledEvents = mutableListOf<PostEvent>()
            val inputRequestStore = AgentToolInputRequestStore()
            val tool =
                ExecutePostActionTool(
                    session =
                        postActionSession(
                            post = targetPost,
                            dataSource = StubPostActionDataSource,
                            accountKey = accountKey,
                            inputRequestStore = inputRequestStore,
                        ),
                    actionHandler = { _, postEvent -> handledEvents += postEvent },
                )

            val result =
                tool.execute(
                    ExecutePostActionTool.Args(
                        actionName = "like",
                        useCurrentStatus = true,
                    ),
                )

            assertTrue(result.contains("确认对以下帖子执行“Like”操作吗？"))
            assertTrue(handledEvents.isEmpty())
            val request = assertNotNull(inputRequestStore.snapshot())
            assertEquals("确认执行", request.options.first().label)
        }

    @Test
    fun executePostActionHandlesEventAfterConfirmation() =
        runTest {
            val accountKey = MicroBlogKey("alice", "example.social")
            val event =
                PostEvent.Mastodon.Bookmark(
                    postKey = MicroBlogKey("post-1", "example.social"),
                    bookmarked = false,
                    accountKey = accountKey,
                )
            val targetPost =
                createPost(
                    statusKey = event.postKey,
                    actions =
                        persistentListOf(
                            postAction(
                                type = ActionMenu.Item.Text.Localized.Type.Bookmark,
                                event = event,
                                accountKey = accountKey,
                            ),
                        ),
                )
            val handledEvents = mutableListOf<PostEvent>()
            val tool =
                ExecutePostActionTool(
                    session =
                        postActionSession(
                            post = targetPost,
                            dataSource = StubPostActionDataSource,
                            accountKey = accountKey,
                        ),
                    actionHandler = { _, postEvent -> handledEvents += postEvent },
                )

            val result =
                tool.execute(
                    ExecutePostActionTool.Args(
                        actionName = "bookmark",
                        useCurrentStatus = true,
                        confirmed = true,
                    ),
                )

            assertTrue(result.contains("Post action executed successfully."))
            assertEquals(listOf<PostEvent>(event), handledEvents)
        }

    private fun agentSearchTargets(): List<AgentSearchTarget> =
        listOf(
            AgentSearchTarget(PlatformType.VVo, StubMicroblogDataSource),
            AgentSearchTarget(PlatformType.xQt, StubMicroblogDataSource),
            AgentSearchTarget(PlatformType.Bluesky, StubMicroblogDataSource),
        )

    private fun composePostTool(
        dataSource: StubComposeDataSource,
        inputRequestStore: AgentToolInputRequestStore = AgentToolInputRequestStore(),
        attachmentStore: AgentToolAttachmentStore = AgentToolAttachmentStore(),
        status: AgentToolContext.StatusContext? = null,
    ): ComposePostTool =
        ComposePostTool(
            AgentToolSession(
                status = status,
                searchTargets = emptyList(),
                composeTargets =
                    listOf(
                        AgentComposeTarget(
                            accountKey = dataSource.accountKey,
                            platformType = PlatformType.Mastodon,
                            dataSource = dataSource,
                        ),
                    ),
                postActionTargets = emptyList(),
                userTargets = emptyList(),
                attachmentStore = attachmentStore,
                inputRequestStore = inputRequestStore,
            ),
        )
}

private fun createPost(
    statusKey: MicroBlogKey,
    content: dev.dimension.flare.ui.render.UiRichText = "content".toUiPlainText(),
    user: UiProfile? = null,
    actions: ImmutableList<ActionMenu> = persistentListOf(),
): UiTimelineV2.Post =
    UiTimelineV2.Post(
        message = null,
        platformType = PlatformType.Mastodon,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = user,
        quote = persistentListOf(),
        content = content,
        actions = actions,
        poll = null,
        statusKey = statusKey,
        card = null,
        createdAt = Clock.System.now().toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        parents = persistentListOf(),
        clickEvent = ClickEvent.Noop,
        accountType = AccountType.Specific(MicroBlogKey("viewer", "example.social")),
    )

private fun postAction(
    type: ActionMenu.Item.Text.Localized.Type,
    event: PostEvent,
    accountKey: MicroBlogKey,
): ActionMenu.Item =
    ActionMenu.Item(
        updateKey = type.name,
        text = ActionMenu.Item.Text.Localized(type),
        clickEvent = ClickEvent.event(accountKey, event),
    )

private fun postActionSession(
    post: UiTimelineV2.Post,
    dataSource: PostDataSource,
    accountKey: MicroBlogKey,
    inputRequestStore: AgentToolInputRequestStore = AgentToolInputRequestStore(),
): AgentToolSession =
    AgentToolSession(
        status =
            AgentToolContext.StatusContext(
                postDataSource = dataSource,
                statusKey = post.statusKey,
                currentPlatformType = post.platformType,
                currentPost = post,
            ),
        searchTargets = emptyList(),
        composeTargets = emptyList(),
        postActionTargets =
            listOf(
                AgentPostActionTarget(
                    accountKey = accountKey,
                    platformType = post.platformType,
                    dataSource = dataSource,
                ),
            ),
        userTargets = emptyList(),
        attachmentStore = AgentToolAttachmentStore(),
        inputRequestStore = inputRequestStore,
    )

private object StubPostDataSource : PostDataSource {
    override val postHandler: PostHandler
        get() = error("unused")

    override val postEventHandler: PostEventHandler
        get() = error("unused")
}

private object StubPostActionDataSource : PostDataSource {
    override val postHandler: PostHandler
        get() = error("unused")

    override val postEventHandler: PostEventHandler
        get() = error("unused")
}

private object StubMicroblogDataSource : MicroblogDataSource {
    override fun homeTimeline(): RemoteLoader<UiTimelineV2> = notSupported()

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): RemoteLoader<UiTimelineV2> = notSupported()

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = notSupported()

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> = notSupported()

    override fun searchUser(query: String): RemoteLoader<UiProfile> = notSupported()

    override fun discoverUsers(): RemoteLoader<UiProfile> = notSupported()

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = notSupported()

    override fun discoverHashtags(): RemoteLoader<UiHashtag> = notSupported()

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> = persistentListOf()
}

private class StubComposeDataSource(
    override val accountKey: MicroBlogKey,
) : ComposeDataSource {
    var composed: Boolean = false
    var lastData: ComposeData? = null

    override suspend fun compose(
        data: ComposeData,
        progress: () -> Unit,
    ) {
        composed = true
        lastData = data
    }

    override fun composeConfig(type: ComposeType): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(maxLength = 300),
            visibility = ComposeConfig.Visibility,
            language = ComposeConfig.Language(maxCount = 1),
        )

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> = notSupported()

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): RemoteLoader<UiTimelineV2> = notSupported()

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = notSupported()

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> = notSupported()

    override fun searchUser(query: String): RemoteLoader<UiProfile> = notSupported()

    override fun discoverUsers(): RemoteLoader<UiProfile> = notSupported()

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = notSupported()

    override fun discoverHashtags(): RemoteLoader<UiHashtag> = notSupported()

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> = persistentListOf()
}
