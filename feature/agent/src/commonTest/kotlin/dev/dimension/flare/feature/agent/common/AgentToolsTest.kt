package dev.dimension.flare.feature.agent.common

import dev.dimension.flare.data.database.app.model.RssDisplayMode
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.NotificationTimelineDataSource
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.list.ListMetaData
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.loader.RelationLoader
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.datasource.subscription.SubscriptionDataSource
import dev.dimension.flare.data.datasource.subscription.SubscriptionSourceDetection
import dev.dimension.flare.data.network.rss.DocumentData
import dev.dimension.flare.data.repository.LocalCacheRepository
import dev.dimension.flare.data.repository.SubscriptionSourceInput
import dev.dimension.flare.data.repository.toUiRssSource
import dev.dimension.flare.feature.agent.presenter.AgentMessagePart
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiRssSource
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.route.DeeplinkRoute
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
    fun searchCachedPostsUsesLocalCacheRepositoryAndStoresAttachments() =
        runTest {
            val post =
                createPost(
                    statusKey = MicroBlogKey("cached-post", "example.social"),
                    content = "cached local result".toUiPlainText(),
                )
            val repository = FakeLocalCacheRepository(cachedPosts = listOf(post))
            val messagePartStore = AgentToolMessagePartStore()
            val tool = SearchCachedPostsTool(localCacheSession(repository, messagePartStore))

            val result =
                tool.execute(
                    SearchCachedPostsTool.Args(
                        query = "cached",
                        maxItems = 10,
                    ),
                )

            assertEquals("cached", repository.searchPostsQuery)
            assertTrue(result.contains("Local cached post search"))
            assertTrue(result.contains("network not used"))
            assertTrue(result.contains("cached local result"))
            val messagePart = messagePartStore.snapshot().single()
            assertTrue(messagePart is AgentMessagePart.PostCard)
            assertEquals(post.statusKey, messagePart.post.statusKey)
        }

    @Test
    fun searchCachedUsersRejectsBlankQueryWithoutRepositoryCall() =
        runTest {
            val repository = FakeLocalCacheRepository()
            val tool = SearchCachedUsersTool(localCacheSession(repository))

            val result = tool.execute(SearchCachedUsersTool.Args(query = " "))

            assertTrue(result.contains("requires a query"))
            assertEquals(null, repository.searchUsersQuery)
        }

    @Test
    fun loadNotificationsUsesRequestedAccountAndStoresAttachments() =
        runTest {
            val post =
                createPost(
                    statusKey = MicroBlogKey("notification-post", "example.social"),
                    content = "mentioned you in a post".toUiPlainText(),
                )
            val alice =
                StubNotificationDataSource(
                    accountKey = MicroBlogKey("alice", "example.social"),
                    items = listOf(post),
                )
            val bob =
                StubNotificationDataSource(
                    accountKey = MicroBlogKey("bob", "example.social"),
                    items = listOf(createPost(statusKey = MicroBlogKey("bob-post", "example.social"))),
                )
            val messagePartStore = AgentToolMessagePartStore()
            val tool =
                LoadNotificationsTool(
                    AgentToolSession(
                        status = null,
                        searchTargets = emptyList(),
                        composeTargets = emptyList(),
                        postActionTargets = emptyList(),
                        notificationTargets =
                            listOf(
                                AgentNotificationTarget(
                                    accountKey = alice.accountKey,
                                    platformType = PlatformType.Mastodon,
                                    dataSource = alice,
                                ),
                                AgentNotificationTarget(
                                    accountKey = bob.accountKey,
                                    platformType = PlatformType.Mastodon,
                                    dataSource = bob,
                                ),
                            ),
                        userTargets = emptyList(),
                        messagePartStore = messagePartStore,
                        inputRequestStore = AgentToolInputRequestStore(),
                    ),
                )

            val result =
                tool.execute(
                    LoadNotificationsTool.Args(
                        filter = "mention",
                        accountId = "alice",
                        accountHost = "example.social",
                        maxItems = 10,
                    ),
                )

            assertEquals(NotificationFilter.Mention, alice.lastFilter)
            assertEquals(null, bob.lastFilter)
            assertTrue(result.contains("Notifications"))
            assertTrue(result.contains("mentioned you in a post"))
            val messagePart = messagePartStore.snapshot().single()
            assertTrue(messagePart is AgentMessagePart.PostCard)
            assertEquals(post.statusKey, messagePart.post.statusKey)
        }

    @Test
    fun loadListTimelineStoresPostAttachments() =
        runTest {
            val post =
                createPost(
                    statusKey = MicroBlogKey("list-post", "example.social"),
                    content = "list timeline post".toUiPlainText(),
                )
            val messagePartStore = AgentToolMessagePartStore()
            val tool =
                LoadListTimelineTool(
                    AgentToolSession(
                        status = null,
                        searchTargets = emptyList(),
                        composeTargets = emptyList(),
                        postActionTargets = emptyList(),
                        listTargets =
                            listOf(
                                stubListTarget(
                                    timelineItems = listOf(post),
                                ),
                            ),
                        userTargets = emptyList(),
                        messagePartStore = messagePartStore,
                        inputRequestStore = AgentToolInputRequestStore(),
                    ),
                )

            val result =
                tool.execute(
                    LoadListTimelineTool.Args(
                        listId = "friends",
                        accountId = "alice",
                        accountHost = "example.social",
                    ),
                )

            assertTrue(result.contains("list timeline post"))
            val messagePart = messagePartStore.snapshot().single()
            assertTrue(messagePart is AgentMessagePart.PostCard)
            assertEquals(post.statusKey, messagePart.post.statusKey)
        }

    @Test
    fun createListRequestsConfirmationBeforeCreating() =
        runTest {
            val created = mutableListOf<ListMetaData>()
            val inputRequestStore = AgentToolInputRequestStore()
            val tool =
                CreateListTool(
                    AgentToolSession(
                        status = null,
                        searchTargets = emptyList(),
                        composeTargets = emptyList(),
                        postActionTargets = emptyList(),
                        listTargets =
                            listOf(
                                stubListTarget(
                                    create = { created += it },
                                ),
                            ),
                        userTargets = emptyList(),
                        messagePartStore = AgentToolMessagePartStore(),
                        inputRequestStore = inputRequestStore,
                    ),
                )

            val result =
                tool.execute(
                    CreateListTool.Args(
                        title = "Friends",
                        description = "Close friends",
                        accountId = "alice",
                        accountHost = "example.social",
                    ),
                )

            assertTrue(created.isEmpty())
            assertTrue(result.contains("confirmation_required"))
            val request = assertNotNull(inputRequestStore.snapshot())
            assertTrue(request.requestId.startsWith("list-create:"))
            assertTrue(
                request.options
                    .single()
                    .value
                    .contains("confirmed=true"),
            )
        }

    @Test
    fun listBuiltInTimelinesReturnsAvailableTargets() =
        runTest {
            val tool =
                ListBuiltInTimelinesTool(
                    AgentToolSession(
                        status = null,
                        searchTargets = emptyList(),
                        composeTargets = emptyList(),
                        postActionTargets = emptyList(),
                        builtInTimelineTargets =
                            listOf(
                                stubBuiltInTimelineTarget(
                                    id = "common.home:alice@example.social",
                                    specId = "common.home",
                                    title = "Home",
                                ),
                            ),
                        userTargets = emptyList(),
                        messagePartStore = AgentToolMessagePartStore(),
                        inputRequestStore = AgentToolInputRequestStore(),
                    ),
                )

            val result = tool.execute(ListBuiltInTimelinesTool.Args(platforms = listOf("mastodon")))

            assertTrue(result.contains("common.home:alice@example.social"))
            assertTrue(result.contains("specId: common.home"))
        }

    @Test
    fun loadBuiltInTimelineStoresPostAttachments() =
        runTest {
            val post =
                createPost(
                    statusKey = MicroBlogKey("built-in-post", "example.social"),
                    content = "built-in timeline post".toUiPlainText(),
                )
            val messagePartStore = AgentToolMessagePartStore()
            val tool =
                LoadBuiltInTimelineTool(
                    AgentToolSession(
                        status = null,
                        searchTargets = emptyList(),
                        composeTargets = emptyList(),
                        postActionTargets = emptyList(),
                        builtInTimelineTargets =
                            listOf(
                                stubBuiltInTimelineTarget(
                                    id = "common.home:alice@example.social",
                                    specId = "common.home",
                                    title = "Home",
                                    items = listOf(post),
                                ),
                            ),
                        userTargets = emptyList(),
                        messagePartStore = messagePartStore,
                        inputRequestStore = AgentToolInputRequestStore(),
                    ),
                )

            val result =
                tool.execute(
                    LoadBuiltInTimelineTool.Args(
                        timelineId = "common.home:alice@example.social",
                    ),
                )

            assertTrue(result.contains("built-in timeline post"))
            val messagePart = messagePartStore.snapshot().single()
            assertTrue(messagePart is AgentMessagePart.PostCard)
            assertEquals(post.statusKey, messagePart.post.statusKey)
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

            assertTrue(result.contains("hello from agent"))
            assertFalse(dataSource.composed)
            val inputRequest = assertNotNull(inputRequestStore.snapshot())
            assertTrue(inputRequest.requestId.startsWith("compose:new:"))
            assertEquals(1, inputRequest.options.size)
            assertEquals("confirm", inputRequest.options.first().id)
            assertEquals("hello from agent", assertNotNull(inputRequest.postPreview).content.original.raw)
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
                        messagePartStore = AgentToolMessagePartStore(),
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

            assertFalse(alice.composed)
            assertFalse(bob.composed)
            val inputRequest = assertNotNull(inputRequestStore.snapshot())
            assertTrue(result.isNotBlank())
            assertTrue(inputRequest.requestId.startsWith("compose-account:new:"))
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
                        messagePartStore = AgentToolMessagePartStore(),
                        inputRequestStore = inputRequestStore,
                    ),
                )

            val result =
                tool.execute(
                    ComposePostTool.Args(
                        content = "hello from agent",
                    ),
                )

            assertFalse(mastodon.composed)
            assertFalse(twitter.composed)
            assertFalse(weibo.composed)
            val inputRequest = assertNotNull(inputRequestStore.snapshot())
            assertTrue(result.isNotBlank())
            assertTrue(inputRequest.requestId.startsWith("compose-platform:new:"))
            assertEquals(3, inputRequest.options.size)
            assertTrue(
                inputRequest.options.any { it.id == "platform:xQt" && it.value.contains("platforms=xQt") },
            )
            assertTrue(inputRequest.options.any { it.value.contains("platforms=VVo") })
            assertTrue(
                inputRequest.options.any {
                    it.id == "platform:Mastodon" &&
                        it.value.contains("hello from agent")
                },
            )
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

            assertFalse(dataSource.composed)
            val inputRequest = assertNotNull(inputRequestStore.snapshot())
            assertTrue(inputRequest.requestId.startsWith("compose:reply:"))
            assertTrue(result.contains("post-1@example.social"))
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
            val messagePartStore = AgentToolMessagePartStore()
            val inputRequestStore = AgentToolInputRequestStore()
            messagePartStore.addPosts(listOf(firstPost, secondPost))
            val tool =
                composePostTool(
                    dataSource = dataSource,
                    messagePartStore = messagePartStore,
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

            assertFalse(dataSource.composed)
            val inputRequest = assertNotNull(inputRequestStore.snapshot())
            assertTrue(result.isNotBlank())
            assertTrue(inputRequest.requestId.startsWith("compose-target:reply:"))
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
            assertEquals(
                "action-0-like",
                request.options
                    .first()
                    .id,
            )
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

            assertTrue(handledEvents.isEmpty())
            val request = assertNotNull(inputRequestStore.snapshot())
            assertTrue(request.requestId.startsWith("post-action-confirm:"))
            assertEquals(1, request.options.size)
            assertEquals("confirm", request.options.first().id)
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

    @Test
    fun loadUserRelationLoadsRelationState() =
        runTest {
            val accountKey = MicroBlogKey("alice", "example.social")
            val userKey = MicroBlogKey("bob", "example.social")
            val dataSource =
                StubRelationDataSource(
                    accountKey = accountKey,
                    relation = UiRelation(following = true, muted = true),
                    supportedTypes = setOf(RelationActionType.Follow, RelationActionType.Mute),
                )
            val tool =
                LoadUserRelationTool(
                    relationSession(
                        dataSource = dataSource,
                    ),
                )

            val result =
                tool.execute(
                    LoadUserRelationTool.Args(
                        targetUserId = userKey.id,
                        targetUserHost = userKey.host,
                        accountId = "alice",
                        accountHost = "example.social",
                    ),
                )

            assertTrue(result.contains("User relation"))
            assertTrue(result.contains("following: true"))
            assertTrue(result.contains("muted: true"))
        }

    @Test
    fun listRelationActionsUsesSupportedTypesAndCurrentRelation() =
        runTest {
            val accountKey = MicroBlogKey("alice", "example.social")
            val userKey = MicroBlogKey("bob", "example.social")
            val dataSource =
                StubRelationDataSource(
                    accountKey = accountKey,
                    relation = UiRelation(following = false, muted = true),
                    supportedTypes = setOf(RelationActionType.Follow, RelationActionType.Mute),
                )
            val inputRequestStore = AgentToolInputRequestStore()
            val tool =
                ListRelationActionsTool(
                    relationSession(
                        dataSource = dataSource,
                        inputRequestStore = inputRequestStore,
                    ),
                )

            val result =
                tool.execute(
                    ListRelationActionsTool.Args(
                        targetUserId = userKey.id,
                        targetUserHost = userKey.host,
                    ),
                )

            assertTrue(result.contains("actionId=follow"))
            assertTrue(result.contains("actionId=unmute"))
            assertFalse(result.contains("actionId=block"))
            val request = assertNotNull(inputRequestStore.snapshot())
            assertEquals(2, request.options.size)
        }

    @Test
    fun executeRelationActionRequestsConfirmationBeforeHandlingAction() =
        runTest {
            val accountKey = MicroBlogKey("alice", "example.social")
            val dataSource =
                StubRelationDataSource(
                    accountKey = accountKey,
                    supportedTypes = setOf(RelationActionType.Mute),
                )
            val handledActions = mutableListOf<RelationAction>()
            val inputRequestStore = AgentToolInputRequestStore()
            val tool =
                ExecuteRelationActionTool(
                    session =
                        relationSession(
                            dataSource = dataSource,
                            inputRequestStore = inputRequestStore,
                        ),
                    actionHandler = { _, action, _, _ -> handledActions += action },
                )

            val result =
                tool.execute(
                    ExecuteRelationActionTool.Args(
                        action = "mute",
                        targetUserId = "bob",
                        targetUserHost = "example.social",
                        accountId = "alice",
                        accountHost = "example.social",
                    ),
                )

            assertTrue(handledActions.isEmpty())
            val request = assertNotNull(inputRequestStore.snapshot())
            assertTrue(request.requestId.startsWith("relation-confirm:"))
            assertEquals(1, request.options.size)
            assertEquals("confirm", request.options.first().id)
        }

    @Test
    fun executeRelationActionHandlesActionAfterConfirmation() =
        runTest {
            val accountKey = MicroBlogKey("alice", "example.social")
            val userKey = MicroBlogKey("bob", "example.social")
            val dataSource =
                StubRelationDataSource(
                    accountKey = accountKey,
                    supportedTypes = setOf(RelationActionType.Follow),
                )
            val handledActions = mutableListOf<Pair<RelationAction, MicroBlogKey>>()
            val tool =
                ExecuteRelationActionTool(
                    session = relationSession(dataSource = dataSource),
                    actionHandler = { _, action, targetUserKey, _ -> handledActions += action to targetUserKey },
                )

            val result =
                tool.execute(
                    ExecuteRelationActionTool.Args(
                        action = "unfollow",
                        targetUserId = userKey.id,
                        targetUserHost = userKey.host,
                        accountId = accountKey.id,
                        accountHost = accountKey.host,
                        confirmed = true,
                    ),
                )

            assertTrue(result.contains("Relation action submitted."))
            assertEquals(listOf(RelationAction.Unfollow to userKey), handledActions)
        }

    @Test
    fun listSubscriptionSourcesIncludesGenericSubscriptionTypes() =
        runTest {
            val dataSource =
                StubSubscriptionDataSource(
                    sources =
                        listOf(
                            subscriptionSource(
                                id = 1,
                                url = "https://example.com/feed.xml",
                                title = "Example RSS",
                                type = SubscriptionType.RSS,
                            ),
                            subscriptionSource(
                                id = 2,
                                url = "mastodon.example",
                                title = "Mastodon Public",
                                type = SubscriptionType.MASTODON_PUBLIC,
                            ),
                        ),
                )
            val tool = ListSubscriptionSourcesTool(subscriptionSession(dataSource))

            val result = tool.execute(ListSubscriptionSourcesTool.Args())

            assertTrue(result.contains("sourceId: 1"))
            assertTrue(result.contains("type: RSS"))
            assertTrue(result.contains("sourceId: 2"))
            assertTrue(result.contains("type: MASTODON_PUBLIC"))
        }

    @Test
    fun loadSubscriptionTimelineStoresRssFeedForArticleTool() =
        runTest {
            val feed =
                createFeed(
                    url = "https://example.com/article",
                    title = "Article title",
                    descriptionHtml = "<p>RSS description</p>",
                )
            val dataSource =
                StubSubscriptionDataSource(
                    timelineItems = mapOf(SubscriptionType.RSS to "https://example.com/feed.xml" to listOf(feed)),
                    article =
                        DocumentData(
                            title = "Article title",
                            content = "<p>RSS description</p>",
                            textContent = "RSS description",
                            length = null,
                            excerpt = null,
                            byline = null,
                            dir = null,
                            siteName = null,
                            lang = null,
                            publishedTime = null,
                        ),
                )
            val session = subscriptionSession(dataSource)
            val timelineTool = LoadSubscriptionTimelineTool(session)
            val articleTool = LoadRssArticleTool(session)

            val timelineResult =
                timelineTool.execute(
                    LoadSubscriptionTimelineTool.Args(
                        type = "RSS",
                        url = "https://example.com/feed.xml",
                    ),
                )

            assertTrue(timelineResult.contains("rssArticleRef: [[rss:https://example.com/article]]"))

            val articleResult =
                articleTool.execute(
                    LoadRssArticleTool.Args(
                        articleRef = "[[rss:https://example.com/article]]",
                    ),
                )

            assertTrue(articleResult.contains("RSS article"))
            assertEquals("https://example.com/article", dataSource.lastArticleUrl)
            assertEquals("<p>RSS description</p>", dataSource.lastArticleDescriptionHtml)
        }

    @Test
    fun saveSubscriptionSourceRequestsConfirmationBeforeSavingDetectedRssFeed() =
        runTest {
            val dataSource =
                StubSubscriptionDataSource(
                    detections =
                        mapOf(
                            "example.com/feed.xml" to
                                SubscriptionSourceDetection.RssFeed(
                                    title = "Example RSS",
                                    url = "https://example.com/feed.xml",
                                    icon = "https://example.com/favicon.ico",
                                ),
                        ),
                )
            val inputRequestStore = AgentToolInputRequestStore()
            val tool =
                SaveSubscriptionSourceTool(
                    subscriptionSession(
                        dataSource = dataSource,
                        inputRequestStore = inputRequestStore,
                    ),
                )

            val result =
                tool.execute(
                    SaveSubscriptionSourceTool.Args(
                        url = "example.com/feed.xml",
                    ),
                )

            assertTrue(dataSource.savedInputs.isEmpty())
            val request = assertNotNull(inputRequestStore.snapshot())
            assertTrue(request.requestId.startsWith("subscription-save:RSS:https://example.com/feed.xml"))
            assertEquals(1, request.options.size)
            assertEquals("confirm", request.options.first().id)
        }

    @Test
    fun saveSubscriptionSourceSavesAfterConfirmation() =
        runTest {
            val dataSource =
                StubSubscriptionDataSource(
                    detections =
                        mapOf(
                            "example.com/feed.xml" to
                                SubscriptionSourceDetection.RssFeed(
                                    title = "Example RSS",
                                    url = "https://example.com/feed.xml",
                                    icon = "https://example.com/favicon.ico",
                                ),
                        ),
                )
            val tool = SaveSubscriptionSourceTool(subscriptionSession(dataSource))

            val result =
                tool.execute(
                    SaveSubscriptionSourceTool.Args(
                        url = "example.com/feed.xml",
                        confirmed = true,
                    ),
                )

            assertTrue(result.contains("Subscription source saved."))
            val saved = dataSource.savedInputs.single()
            assertEquals(SubscriptionType.RSS, saved.type)
            assertEquals("https://example.com/feed.xml", saved.url)
            assertEquals("Example RSS", saved.title)
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
        messagePartStore: AgentToolMessagePartStore = AgentToolMessagePartStore(),
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
                messagePartStore = messagePartStore,
                inputRequestStore = inputRequestStore,
            ),
        )
}

private fun stubListTarget(
    accountKey: MicroBlogKey = MicroBlogKey("alice", "example.social"),
    platformType: PlatformType = PlatformType.Mastodon,
    lists: List<UiList.List> =
        listOf(
            UiList.List(
                id = "friends",
                title = "Friends",
                description = "Close friends",
            ),
        ),
    timelineItems: List<UiTimelineV2> = emptyList(),
    create: suspend (ListMetaData) -> Unit = {},
): AgentListTarget =
    AgentListTarget(
        accountKey = accountKey,
        platformType = platformType,
        supportedMetaData = setOf("TITLE", "DESCRIPTION"),
        listCached = { lists },
        loadListInfo = { listId -> lists.first { it.id == listId } },
        loadListTimeline = { _, _ -> timelineItems },
        loadUserLists = { lists },
        createList = create,
        updateList = { _, _ -> },
        deleteList = { _ -> },
        addMember = { _, _ -> },
        removeMember = { _, _ -> },
    )

private fun stubBuiltInTimelineTarget(
    id: String,
    specId: String,
    title: String,
    accountKey: MicroBlogKey = MicroBlogKey("alice", "example.social"),
    platformType: PlatformType = PlatformType.Mastodon,
    items: List<UiTimelineV2> = emptyList(),
): AgentBuiltInTimelineTarget =
    AgentBuiltInTimelineTarget(
        id = id,
        specId = specId,
        title = title,
        accountKey = accountKey,
        platformType = platformType,
        loadTimeline = { pageSize -> items.take(pageSize) },
    )

private fun createPost(
    statusKey: MicroBlogKey,
    content: dev.dimension.flare.ui.render.UiRichText = "content".toUiPlainText(),
    user: UiProfile? = null,
    actions: ImmutableList<ActionMenu> = persistentListOf(),
): UiTimelineV2.Post =
    UiTimelineV2.Post(
        platformType = PlatformType.Mastodon,
        images = persistentListOf(),
        sensitive = false,
        contentWarning = null,
        user = user,
        content = UiTranslatableText(content),
        actions = actions,
        poll = null,
        statusKey = statusKey,
        card = null,
        createdAt = Clock.System.now().toUi(),
        emojiReactions = persistentListOf(),
        sourceChannel = null,
        visibility = null,
        replyToHandle = null,
        clickEvent = ClickEvent.Noop,
        accountType = AccountType.Specific(MicroBlogKey("viewer", "example.social")),
    )

private fun localCacheSession(
    repository: LocalCacheRepository,
    messagePartStore: AgentToolMessagePartStore = AgentToolMessagePartStore(),
): AgentToolSession =
    AgentToolSession(
        status = null,
        searchTargets = emptyList(),
        composeTargets = emptyList(),
        postActionTargets = emptyList(),
        userTargets = emptyList(),
        messagePartStore = messagePartStore,
        inputRequestStore = AgentToolInputRequestStore(),
        localCacheRepository = repository,
    )

private class FakeLocalCacheRepository(
    private val cachedPosts: List<UiTimelineV2.Post> = emptyList(),
    private val viewedPosts: List<UiTimelineV2.Post> = emptyList(),
    private val cachedUsers: List<UiProfile> = emptyList(),
    private val viewedUsers: List<UiProfile> = emptyList(),
) : LocalCacheRepository {
    var searchPostsQuery: String? = null
    var searchUsersQuery: String? = null

    override suspend fun searchPosts(
        query: String,
        limit: Int,
    ): List<UiTimelineV2.Post> {
        searchPostsQuery = query
        return cachedPosts.take(limit)
    }

    override suspend fun listViewedPosts(limit: Int): List<UiTimelineV2.Post> = viewedPosts.take(limit)

    override suspend fun searchUsers(
        query: String,
        limit: Int,
    ): List<UiProfile> {
        searchUsersQuery = query
        return cachedUsers.take(limit)
    }

    override suspend fun listViewedUsers(limit: Int): List<UiProfile> = viewedUsers.take(limit)
}

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
        messagePartStore = AgentToolMessagePartStore(),
        inputRequestStore = inputRequestStore,
    )

private fun relationSession(
    dataSource: StubRelationDataSource,
    messagePartStore: AgentToolMessagePartStore = AgentToolMessagePartStore(),
    inputRequestStore: AgentToolInputRequestStore = AgentToolInputRequestStore(),
): AgentToolSession =
    AgentToolSession(
        status = null,
        searchTargets = emptyList(),
        composeTargets = emptyList(),
        postActionTargets = emptyList(),
        relationTargets =
            listOf(
                AgentRelationTarget(
                    accountKey = dataSource.accountKey,
                    platformType = PlatformType.Mastodon,
                    dataSource = dataSource,
                ),
            ),
        userTargets = emptyList(),
        messagePartStore = messagePartStore,
        inputRequestStore = inputRequestStore,
    )

private fun subscriptionSession(
    dataSource: SubscriptionDataSource,
    inputRequestStore: AgentToolInputRequestStore = AgentToolInputRequestStore(),
): AgentToolSession =
    AgentToolSession(
        status = null,
        searchTargets = emptyList(),
        composeTargets = emptyList(),
        postActionTargets = emptyList(),
        relationTargets = emptyList(),
        subscriptionDataSource = dataSource,
        userTargets = emptyList(),
        messagePartStore = AgentToolMessagePartStore(),
        inputRequestStore = inputRequestStore,
    )

private fun subscriptionSource(
    id: Int,
    url: String,
    title: String,
    type: SubscriptionType,
): UiRssSource =
    UiRssSource(
        id = id,
        url = url,
        title = title,
        lastUpdate = Clock.System.now().toUi(),
        favIcon = null,
        displayMode = RssDisplayMode.FULL_CONTENT,
        type = type,
    )

private fun createFeed(
    url: String,
    title: String,
    descriptionHtml: String?,
): UiTimelineV2.Feed =
    UiTimelineV2.Feed(
        title = title,
        description = "description",
        descriptionHtml = descriptionHtml,
        url = url,
        createdAt = Clock.System.now().toUi(),
        source = UiTimelineV2.Feed.Source(name = "Example RSS", icon = null),
        media = null,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Rss.Detail(
                    url = url,
                    descriptionHtml = descriptionHtml,
                    title = title,
                ),
            ),
        accountType = AccountType.Guest,
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

private class StubNotificationDataSource(
    override val accountKey: MicroBlogKey,
    private val items: List<UiTimelineV2>,
    override val supportedNotificationFilter: List<NotificationFilter> = NotificationFilter.entries,
) : NotificationTimelineDataSource {
    var lastFilter: NotificationFilter? = null

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> {
        lastFilter = type
        return object : RemoteLoader<UiTimelineV2> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> =
                PagingResult(
                    endOfPaginationReached = true,
                    data = items.take(pageSize),
                )
        }
    }

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

private class StubRelationDataSource(
    val accountKey: MicroBlogKey,
    relation: UiRelation = UiRelation(),
    supportedTypes: Set<RelationActionType> = RelationActionType.entries.toSet(),
) : RelationDataSource {
    private val loader = StubRelationLoader(relation, supportedTypes)

    override val relationHandler: RelationHandler =
        RelationHandler(
            accountType = AccountType.Specific(accountKey),
            dataSource = loader,
        )

    override val supportedRelationTypes: Set<RelationActionType> = supportedTypes
}

private class StubRelationLoader(
    private val relation: UiRelation,
    override val supportedTypes: Set<RelationActionType>,
) : RelationLoader {
    override suspend fun relation(userKey: MicroBlogKey): UiRelation = relation

    override suspend fun follow(userKey: MicroBlogKey) = Unit

    override suspend fun unfollow(userKey: MicroBlogKey) = Unit

    override suspend fun block(userKey: MicroBlogKey) = Unit

    override suspend fun unblock(userKey: MicroBlogKey) = Unit

    override suspend fun mute(userKey: MicroBlogKey) = Unit

    override suspend fun unmute(userKey: MicroBlogKey) = Unit
}

private class StubSubscriptionDataSource(
    private val sources: List<UiRssSource> = emptyList(),
    private val detections: Map<String, SubscriptionSourceDetection> = emptyMap(),
    private val timelineItems: Map<Pair<SubscriptionType, String>, List<UiTimelineV2>> = emptyMap(),
    private val article: DocumentData =
        DocumentData(
            title = "Article",
            content = "<p>Article</p>",
            textContent = "Article",
            length = null,
            excerpt = null,
            byline = null,
            dir = null,
            siteName = null,
            lang = null,
            publishedTime = null,
        ),
) : SubscriptionDataSource {
    val savedInputs = mutableListOf<SubscriptionSourceInput>()
    val deletedIds = mutableListOf<Int>()
    var lastArticleUrl: String? = null
    var lastArticleDescriptionHtml: String? = null
    var lastArticleDescriptionTitle: String? = null

    override suspend fun listSources(): List<UiRssSource> = sources

    override suspend fun detectSource(input: String): SubscriptionSourceDetection =
        detections[input] ?: error("No detection stub for $input")

    override fun createTimelineLoader(
        type: SubscriptionType,
        url: String,
    ): CacheableRemoteLoader<UiTimelineV2> = StubSubscriptionTimelineLoader(timelineItems[type to url].orEmpty())

    override suspend fun saveSource(input: SubscriptionSourceInput): UiRssSource {
        savedInputs += input
        return input.toUiRssSource()
    }

    override suspend fun deleteSource(id: Int): UiRssSource? {
        deletedIds += id
        return sources.firstOrNull { it.id == id }
    }

    override suspend fun loadRssArticle(
        url: String,
        descriptionHtml: String?,
        descriptionTitle: String?,
    ): DocumentData {
        lastArticleUrl = url
        lastArticleDescriptionHtml = descriptionHtml
        lastArticleDescriptionTitle = descriptionTitle
        return article
    }
}

private class StubSubscriptionTimelineLoader(
    private val items: List<UiTimelineV2>,
) : CacheableRemoteLoader<UiTimelineV2> {
    override val pagingKey: String = "stub-subscription"

    override suspend fun load(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiTimelineV2> =
        PagingResult(
            endOfPaginationReached = true,
            data = items.take(pageSize),
        )
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
