package dev.dimension.flare.feature.plugin.adapter

import androidx.paging.LoadState
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDetail
import dev.dimension.flare.data.datasource.microblog.list.ListMetaData
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.host.PluginHttpTransport
import dev.dimension.flare.feature.plugin.host.PluginTransportRequestV1
import dev.dimension.flare.feature.plugin.host.PluginTransportResponseV1
import dev.dimension.flare.feature.plugin.installer.PluginInstaller
import dev.dimension.flare.feature.plugin.installer.TestFppFactory
import dev.dimension.flare.feature.plugin.lifecycle.PluginStateStore
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.runtime.PluginCallException
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimePool
import dev.dimension.flare.feature.plugin.wire.EntityKeyV1
import dev.dimension.flare.feature.plugin.wire.LoginSuccessV1
import dev.dimension.flare.feature.plugin.wire.PluginAccountCredentialV1
import dev.dimension.flare.feature.plugin.wire.ProfileV1
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.postEventOrNull
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.SYSTEM
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PluginDetailCapabilityTest {
    private val fileSystem = FileSystem.SYSTEM
    private lateinit var root: Path
    private lateinit var runtimePool: PluginRuntimePool
    private lateinit var scope: CoroutineScope

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("plugin-detail-capability-test").toOkioPath()
        runtimePool = PluginRuntimePool(fileSystem, NoopTransport())
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @AfterTest
    fun tearDown() =
        runBlocking {
            scope.cancel()
            runtimePool.close()
            fileSystem.deleteRecursively(root, mustExist = false)
        }

    @Test
    fun partialCapabilitiesKeepHostEntrypointsAndMappedRoutesConsistent() =
        runBlocking<Unit> {
            val plugin = install()
            for (missing in listOf("page", "badge")) {
                val source =
                    authenticated(
                        plugin,
                        unavailableOperations =
                            mapOf(
                                PluginAbiV1.Capabilities.NOTIFICATION to setOf(missing),
                                PluginAbiV1.Capabilities.ARTICLE to setOf("comments"),
                            ),
                    )
                val notification = requireNotNull(source.capabilitySet.notification)
                assertEquals(missing != "page", notification.timeline != null)
                assertEquals(missing != "badge", notification.events != null)
                assertEquals(null, source.capabilitySet.article)
                assertEquals(ClickEvent.Noop, detailPosts(source)[2].clickEvent)
                assertIs<ClickEvent.Deeplink>(detailPosts(source)[3].clickEvent)
            }
        }

    @Test
    fun guestsKeepDetailRoutesWithoutAccountCapabilities() =
        runBlocking<Unit> {
            val plugin = install(manifest = MANIFEST.replace("\"capabilities\": {", "\"guest\": {\"enabled\": true}, \"capabilities\": {"))
            val source = PluginDataSourceV1.guest(plugin, runtimePool, ORIGIN, "en", emptyMap(), null)
            val capabilities = source.capabilitySet
            assertEquals(null, capabilities.relation)
            assertEquals(null, capabilities.list)
            assertEquals(null, capabilities.notification)
            assertEquals(null, capabilities.directMessage)
            assertEquals(null, capabilities.tabCatalog)
            assertTrue(capabilities.article != null)
            assertTrue(capabilities.gallery != null)
            val posts = detailPosts(source)
            assertIs<DeeplinkRoute.Article>(DeeplinkRoute.parse(assertIs<ClickEvent.Deeplink>(posts[2].clickEvent).url))
            assertIs<DeeplinkRoute.Gallery.Detail>(DeeplinkRoute.parse(assertIs<ClickEvent.Deeplink>(posts[3].clickEvent).url))
        }

    @Test
    fun pluginTimelinesRemainAvailableToMixedTimelineLoaders() =
        runBlocking<Unit> {
            val plugin = install()
            val source = authenticated(plugin)
            val first =
                assertIs<CacheableRemoteLoader<*>>(
                    source.timeline("dynamic-feed", mapOf("kind" to "photos", "sort" to "new")),
                )
            val reordered =
                assertIs<CacheableRemoteLoader<*>>(
                    source.timeline("dynamic-feed", mapOf("sort" to "new", "kind" to "photos")),
                )
            val another =
                assertIs<CacheableRemoteLoader<*>>(
                    source.timeline("dynamic-feed", mapOf("kind" to "details")),
                )
            assertEquals(first.pagingKey, reordered.pagingKey)
            assertTrue(first.pagingKey != another.pagingKey)
            assertEquals(false, first.supportPrepend)
            assertEquals(
                listOf("post-1"),
                source
                    .timeline("dynamic-feed", mapOf("kind" to "photos"))
                    .load(20, PagingRequest.Refresh)
                    .data
                    .map { it.statusKey.id },
            )
            assertIs<CacheableRemoteLoader<*>>(
                source.extraCapabilities.listAdapter!!.listTimeline("list-1"),
            )
            val notifications = requireNotNull(source.capabilitySet.notification?.timeline)
            assertTrue(
                assertIs<CacheableRemoteLoader<*>>(notifications.notification(NotificationFilter.All)).pagingKey !=
                    assertIs<CacheableRemoteLoader<*>>(notifications.notification(NotificationFilter.Mention)).pagingKey,
            )
            val otherAccount = authenticated(plugin, accountKey = MicroBlogKey("account-2", "plugin.example"))
            val otherOrigin =
                authenticated(plugin, accountKey = MicroBlogKey("account-1", "plugin.example:8443"), origin = "https://plugin.example:8443")
            val upgraded = authenticated(plugin.copy(installed = plugin.installed.copy(packageHash = "f".repeat(64))))
            val keys =
                listOf(source, otherAccount, otherOrigin, upgraded).map {
                    assertIs<CacheableRemoteLoader<*>>(it.timeline("dynamic-feed", mapOf("kind" to "photos"))).pagingKey
                }
            assertEquals(4, keys.toSet().size)
            val prepend =
                authenticated(install(MANIFEST.replace("\"directions\": [\"refresh\"]", "\"directions\": [\"refresh\", \"newer\"]")))
            assertTrue(assertIs<CacheableRemoteLoader<*>>(prepend.timeline("dynamic-feed")).supportPrepend)
        }

    @Test
    fun composeWithoutProfileCapabilityKeepsThePersistedLoginProfile() =
        runBlocking<Unit> {
            val manifest =
                MANIFEST.replace(
                    "\"capabilities\": {",
                    """
                    "composeDefaults": { "text": { "maxLength": 1000 } },
                    "capabilities": { "flare.datasource.compose/v1": { "operations": { "publish": {} } },
                    """.trimIndent(),
                )
            val script =
                SCRIPT.replace(
                    "definePlugin({ capabilities: {",
                    """
                    definePlugin({ capabilities: {
                    compose: { publish() { return { post: post("published") }; } },
                    """.trimIndent(),
                )
            val dataSource = authenticated(install(manifest, script))
            assertEquals(null, dataSource.capabilitySet.profile)
            assertTrue(dataSource.capabilitySet.compose != null)
            val profile = dataSource.authenticatedProfile
            assertEquals(MicroBlogKey("account-1", "plugin.example"), profile?.key)
            assertEquals("Test", profile?.name?.raw)
        }

    @Test
    fun directMessagePollingAdvancesPageCursorsEvenWhenThePageIsEmpty() =
        runBlocking {
            val source = authenticated(install(script = directMessagePagingScript()))
            val loader = requireNotNull(source.extraCapabilities.directMessageAdapter).loader
            val room = MicroBlogKey("room-1", "plugin.example")
            val page = loader.loadMessages(room, 20, PagingRequest.Refresh)
            val persistedCursor = page.data.single().remoteCursor
            assertEquals("room-1-after-refresh", page.previousKey)
            assertEquals(page.previousKey, persistedCursor)
            assertTrue(loader.fetchNewMessages(room, persistedCursor).messages.isEmpty())

            val next = loader.fetchNewMessages(room, persistedCursor).messages.single()
            assertEquals("newer:room-1-after-empty", assertIs<UiDMItem.Message.Text>(next.content).text.raw)
            assertEquals("room-1-after-newer", next.remoteCursor)
            loader.deleteMessage(room, next.key)
        }

    @Test
    fun directMessagePollingKeepsRoomCursorsWhenLoadingHistoryOrSending() =
        runBlocking {
            val source = authenticated(install(script = directMessagePagingScript()))
            val loader = requireNotNull(source.extraCapabilities.directMessageAdapter).loader
            val firstRoom = MicroBlogKey("room-1", "plugin.example")
            val secondRoom = MicroBlogKey("room-2", "plugin.example")
            loader.loadMessages(firstRoom, 20, PagingRequest.Refresh)
            loader.loadMessages(secondRoom, 20, PagingRequest.Refresh)
            loader.loadMessages(firstRoom, 20, PagingRequest.Append("older"))
            val sent = loader.sendMessage(firstRoom, "hello")
            assertEquals("room-1-after-refresh", sent.remoteCursor)
            assertTrue(loader.fetchNewMessages(firstRoom, sent.remoteCursor).messages.isEmpty())
            assertTrue(loader.fetchNewMessages(secondRoom, null).messages.isEmpty())
        }

    @Test
    fun directMessagePollingResumesAPersistedCursorWithANewAdapter() =
        runBlocking {
            val plugin = install(script = directMessagePagingScript())
            val first = requireNotNull(authenticated(plugin).extraCapabilities.directMessageAdapter).loader
            val room = MicroBlogKey("room-1", "plugin.example")
            val persisted = first.loadMessages(room, 20, PagingRequest.Refresh).data.single()
            val restored = requireNotNull(authenticated(plugin).extraCapabilities.directMessageAdapter).loader
            assertTrue(restored.fetchNewMessages(room, persisted.remoteCursor).messages.isEmpty())
            val next = restored.fetchNewMessages(room, persisted.remoteCursor).messages.single()
            assertEquals("newer:room-1-after-empty", assertIs<UiDMItem.Message.Text>(next.content).text.raw)
        }

    @Test
    fun directMessagePollingRetriesTheCursorWhenMappingFails() =
        runBlocking {
            val script =
                "var attempts = 0;\n" +
                    directMessagePagingScript().replace(
                        "sender: profile,",
                        """
                        sender: direction === "newer" && attempts++ === 0
                          ? Object.assign({}, profile, { avatarUrl: "https://plugin.example/denied.png" }) : profile,
                        """.trimIndent(),
                    )
            val source = authenticated(install(MANIFEST.replace("\"mediaNetwork\": true", "\"mediaNetwork\": false"), script))
            val loader = requireNotNull(source.extraCapabilities.directMessageAdapter).loader
            val room = MicroBlogKey("room-1", "plugin.example")
            val cursor =
                loader
                    .loadMessages(room, 20, PagingRequest.Refresh)
                    .data
                    .single()
                    .remoteCursor
            assertTrue(loader.fetchNewMessages(room, cursor).messages.isEmpty())
            assertFailsWith<IllegalArgumentException> { loader.fetchNewMessages(room, cursor) }
            val retried = loader.fetchNewMessages(room, cursor).messages.single()
            assertEquals("newer:room-1-after-empty", assertIs<UiDMItem.Message.Text>(retried.content).text.raw)
        }

    private fun directMessagePagingScript(): String =
        SCRIPT
            .replace(
                "messages() { return { items: [] }; },",
                """
                messages(request) {
                  const room = request.roomKey.id;
                  const direction = request.page.direction;
                  if (direction === "newer" && request.page.cursor === room + "-after-refresh") {
                    return { items: [], newerCursor: room + "-after-empty" };
                  }
                  return {
                    items: [{
                      key: { id: room + "-" + direction, host: "plugin.example" },
                      roomKey: request.roomKey,
                      sender: profile,
                      createdAt: "2026-01-01T00:00:00Z",
                      content: { value: direction + ":" + (request.page.cursor || "initial") },
                      entityToken: "message-token"
                    }],
                    newerCursor: room + "-after-" + direction
                  };
                },
                """.trimIndent(),
            ).replace(
                "delete() { return { type: \"deleted\" }; },",
                """
                delete(request) {
                  if (request.messageEntityToken !== "message-token") throw new Error("Missing message entity token");
                  return { type: "deleted" };
                },
                """.trimIndent(),
            )

    @Test
    fun coldListAndDirectMessageDetailsCallDedicatedPluginMethods() =
        runBlocking {
            val plugin = install()
            val accountKey = MicroBlogKey("account-1", "plugin.example")
            val dataSource = authenticated(plugin)

            val listAdapter = requireNotNull(dataSource.extraCapabilities.listAdapter)
            assertEquals("Cold list", listAdapter.info("list-1").title)
            assertEquals("Updated", listAdapter.update("list-1", ListMetaData(title = "Updated")).title)
            assertTrue(
                listAdapter
                    .listTimeline("list-1")
                    .load(20, PagingRequest.Refresh)
                    .data
                    .isEmpty(),
            )

            val roomKey = MicroBlogKey("room-1", accountKey.host)
            val directMessage = requireNotNull(dataSource.extraCapabilities.directMessageAdapter)
            assertEquals(roomKey, directMessage.roomInfo(roomKey).key)
            assertTrue(directMessage.canSendDirectMessage(MicroBlogKey("user-1", accountKey.host)))

            val relation = requireNotNull(dataSource.capabilitySet.relation)
            assertEquals(
                setOf(RelationActionType.Follow),
                relation.supportedRelationTypes,
            )
            val relationLoader = relation.relationHandler.dataSource
            assertEquals(false, relationLoader.relation(MicroBlogKey("user-1", accountKey.host)).following)
            relationLoader.follow(MicroBlogKey("user-1", accountKey.host))

            val notification = requireNotNull(dataSource.capabilitySet.notification)
            val notificationTimeline = requireNotNull(notification.timeline)
            assertEquals(
                listOf(NotificationFilter.All, NotificationFilter.Mention),
                notificationTimeline.supportedNotificationFilter,
            )
            assertTrue(
                notificationTimeline
                    .notification()
                    .load(20, PagingRequest.Refresh)
                    .data
                    .isEmpty(),
            )
            assertEquals(0, requireNotNull(notification.events).notificationHandler.loader.notificationBadgeCount())

            val articleKey = MicroBlogKey("article-1", accountKey.host)
            val article = requireNotNull(dataSource.capabilitySet.article)
            assertEquals("Plugin article", article.article(articleKey).title)
            assertTrue(
                article
                    .articleComments(articleKey)
                    .load(20, PagingRequest.Refresh)
                    .data
                    .isEmpty(),
            )

            val galleryKey = MicroBlogKey("gallery-1", accountKey.host)
            val gallery = requireNotNull(dataSource.capabilitySet.gallery)
            val galleryDetail = gallery.galleryDetail(galleryKey)
            assertIs<LoadState.NotLoading>(galleryDetail.refreshState.first { it !is LoadState.Loading })
            assertEquals(
                "Plugin gallery",
                assertIs<CacheState.Success<GalleryDetail>>(galleryDetail.data.first { it is CacheState.Success }).data.title,
            )
            assertTrue(
                gallery
                    .galleryComments(galleryKey)
                    .load(20, PagingRequest.Refresh)
                    .data
                    .isEmpty(),
            )
            assertTrue(
                gallery
                    .galleryRecommendations(galleryKey)
                    .load(20, PagingRequest.Refresh)
                    .data
                    .isEmpty(),
            )

            assertEquals(
                1,
                dataSource
                    .timeline("dynamic-feed", mapOf("kind" to "photos"))
                    .load(20, PagingRequest.Refresh)
                    .data.size,
            )
        }

    @Test
    fun mixedTimelinePostsUseTheirSelectedDetailRoutes() =
        runBlocking {
            val dataSource = authenticated(install())
            val accountType = AccountType.Specific(MicroBlogKey("account-1", "plugin.example"))
            val posts = detailPosts(dataSource)

            assertEquals(
                listOf(
                    ClickEvent.Deeplink(DeeplinkRoute.Status.Detail(MicroBlogKey("default", "plugin.example"), accountType)),
                    ClickEvent.Deeplink(DeeplinkRoute.Status.Detail(MicroBlogKey("post", "plugin.example"), accountType)),
                    ClickEvent.Deeplink(DeeplinkRoute.Article(accountType, MicroBlogKey("article", "plugin.example"))),
                    ClickEvent.Deeplink(DeeplinkRoute.Gallery.Detail(MicroBlogKey("gallery", "plugin.example"), accountType)),
                    ClickEvent.Deeplink(DeeplinkRoute.OpenLinkDirectly("https://plugin.example/posts/link")),
                ),
                posts.map { it.clickEvent },
            )
        }

    @Test
    fun detailLinksRequireTheNegotiatedTargetCapability() =
        runBlocking {
            val dataSource =
                authenticated(
                    install(),
                    unavailableCapabilities = setOf(PluginAbiV1.Capabilities.ARTICLE, PluginAbiV1.Capabilities.GALLERY),
                )
            val posts = detailPosts(dataSource)

            assertIs<ClickEvent.Deeplink>(posts[0].clickEvent)
            assertIs<ClickEvent.Deeplink>(posts[1].clickEvent)
            assertEquals(ClickEvent.Noop, posts[2].clickEvent)
            assertEquals(ClickEvent.Noop, posts[3].clickEvent)
            assertEquals(ClickEvent.Deeplink(DeeplinkRoute.OpenLinkDirectly("https://plugin.example/posts/link")), posts[4].clickEvent)
        }

    @Test
    fun articleAndGalleryLinksDoNotRequirePostDetail() =
        runBlocking<Unit> {
            val dataSource = authenticated(install(), unavailableCapabilities = setOf(PluginAbiV1.Capabilities.POST))
            val posts = detailPosts(dataSource)

            assertEquals(ClickEvent.Noop, posts[0].clickEvent)
            assertEquals(ClickEvent.Noop, posts[1].clickEvent)
            assertIs<DeeplinkRoute.Article>(DeeplinkRoute.parse(assertIs<ClickEvent.Deeplink>(posts[2].clickEvent).url))
            assertIs<DeeplinkRoute.Gallery.Detail>(DeeplinkRoute.parse(assertIs<ClickEvent.Deeplink>(posts[3].clickEvent).url))
        }

    @Test
    fun linkPostsDoNotRequireAnyDetailCapability() =
        runBlocking {
            val source =
                authenticated(
                    install(),
                    unavailableCapabilities =
                        setOf(
                            PluginAbiV1.Capabilities.POST,
                            PluginAbiV1.Capabilities.ARTICLE,
                            PluginAbiV1.Capabilities.GALLERY,
                        ),
                )
            val posts = detailPosts(source)
            assertTrue(posts.take(4).all { it.clickEvent == ClickEvent.Noop })
            assertEquals(ClickEvent.Deeplink(DeeplinkRoute.OpenLinkDirectly("https://plugin.example/posts/link")), posts[4].clickEvent)
            assertEquals(UiTimelineV2.Post.MediaClickPolicy.OpenPostClickEvent, posts[4].mediaClickPolicy)
        }

    @Test
    fun packageMissingDedicatedDetailMethodIsRejected() =
        runBlocking {
            val input = root / "missing-detail.fpp"
            TestFppFactory.write(input, TestFppFactory.validEntries(manifest = MANIFEST, script = SCRIPT.replace(LIST_DETAIL, "")))
            val store = PluginStateStore.open(fileSystem, root / "missing-detail-store")

            val error = runCatching { PluginInstaller(fileSystem, store).inspect(input) }.exceptionOrNull()

            assertTrue(error != null && "capabilities.list.detail" in error.message.orEmpty())
        }

    @Test
    fun galleryBookmarkRefreshesOpenDetailsAfterUpdatedPostAndFailedMutation() =
        assertGalleryMutationRefreshes("{ type: 'updatedPost', post: post(request.key.id) }")

    @Test
    fun galleryBookmarkRefreshesOpenDetailsAfterInvalidation() =
        assertGalleryMutationRefreshes("{ type: 'invalidate', keys: [request.key] }")

    @Test
    fun galleryBookmarkRefreshesOpenDetailsWhenMutationReturnsNoPost() = assertGalleryMutationRefreshes("{ type: 'noChange' }")

    private fun assertGalleryMutationRefreshes(result: String) =
        runBlocking {
            val plugin =
                install(
                    manifest = MANIFEST.replace("\"detail\": {} } },", "\"detail\": {}, \"mutate\": {} } },"),
                    script =
                        "let bookmarked = false;\n" +
                            SCRIPT
                                .replace(
                                    "post: { detail(request)",
                                    """post: { mutate(request) {
                                if (request.actionToken === "fail") throw flare.error({ code: "Network", message: { key: "error.network", fallback: "temporary failure" } });
                                if (request.action !== (bookmarked ? "Unbookmark" : "Bookmark")) throw new Error("stale bookmark action");
                                bookmarked = !bookmarked;
                                return $result;
                            }, detail(request)""",
                                ).replace(
                                    "title: \"Plugin gallery\",",
                                    "title: \"Plugin gallery\", actions: [{ action: bookmarked ? 'Unbookmark' : 'Bookmark', active: bookmarked }],",
                                ),
                )
            val source = authenticated(plugin)
            val gallery = requireNotNull(source.capabilitySet.gallery)
            val key = MicroBlogKey("gallery-1", "plugin.example")
            val cache = gallery.galleryDetail(key)
            val observer = CoroutineScope(coroutineContext + Job(coroutineContext.job))
            val visible = cache.data.stateIn(observer, SharingStarted.Eagerly, CacheState.Empty())
            val anotherView = gallery.galleryDetail(key)
            val secondObserver = CoroutineScope(coroutineContext + Job(coroutineContext.job))
            val secondVisible = anotherView.data.stateIn(secondObserver, SharingStarted.Eagerly, CacheState.Empty())
            val secondRefresh = launch { anotherView.refreshState.collect { if (it is LoadState.Error) throw it.error } }
            val refresh = launch { cache.refreshState.collect { if (it is LoadState.Error) throw it.error } }
            try {
                suspend fun detail(bookmarked: Boolean): GalleryDetail =
                    withTimeout(10_000) {
                        assertIs<CacheState.Success<GalleryDetail>>(
                            visible.first { it is CacheState.Success && it.data.isBookmarked == bookmarked },
                        ).data
                    }
                val before = detail(false)
                withTimeout(10_000) { secondVisible.first { it is CacheState.Success } }
                val handler = source.postMutationHandler
                val updater = RecordingDatabaseUpdater()
                val bookmark = assertIs<PostEvent.Semantic>(requireNotNull(before.bookmarkAction.postEventOrNull()).postEvent)
                assertFailsWith<PluginCallException> { handler.handle(bookmark.copy(actionToken = "fail"), updater) }
                assertEquals(0, updater.updates)
                assertEquals(
                    false,
                    assertIs<CacheState.Success<GalleryDetail>>(visible.first { it is CacheState.Success }).data.isBookmarked,
                )

                handler.handle(bookmark, updater)
                assertEquals(if (result.contains("noChange")) 0 else 1, updater.updates)
                val bookmarked = detail(true)
                withTimeout(10_000) { secondVisible.first { it is CacheState.Success && it.data.isBookmarked } }
                secondObserver.coroutineContext.job.cancelAndJoin()
                handler.handle(requireNotNull(bookmarked.bookmarkAction.postEventOrNull()).postEvent, updater)
                assertEquals(false, detail(false).isBookmarked)
                assertEquals(
                    true,
                    assertIs<CacheState.Success<GalleryDetail>>(anotherView.data.first { it is CacheState.Success }).data.isBookmarked,
                )
            } finally {
                observer.coroutineContext.job.cancelAndJoin()
                secondObserver.coroutineContext.job.cancelAndJoin()
                secondRefresh.cancelAndJoin()
                refresh.cancelAndJoin()
            }
        }

    private suspend fun detailPosts(dataSource: PluginDataSourceV1): List<UiTimelineV2.Post> =
        dataSource
            .timeline("dynamic-feed", mapOf("kind" to "details"))
            .load(20, PagingRequest.Refresh)
            .data
            .map { assertIs<UiTimelineV2.TimelinePostItem>(it).post }

    private suspend fun authenticated(
        plugin: RunningPluginV1,
        unavailableCapabilities: Set<String> = emptySet(),
        unavailableOperations: Map<String, Set<String>> = emptyMap(),
        accountKey: MicroBlogKey = MicroBlogKey("account-1", "plugin.example"),
        origin: String = ORIGIN,
    ): PluginDataSourceV1 {
        val success =
            LoginSuccessV1(
                accountId = accountKey.id,
                origin = origin,
                credential = JsonObject(emptyMap()),
                profile = ProfileV1(EntityKeyV1(accountKey.id, accountKey.host), "test@plugin.example", "Test"),
                capabilities =
                    plugin.installed.manifest.platform.capabilities
                        .filterKeys { it !in unavailableCapabilities }
                        .mapValues { (id, declaration) -> declaration.operations.keys - unavailableOperations[id].orEmpty() },
            )
        return PluginDataSourceV1.authenticated(
            plugin = plugin,
            runtimePool = runtimePool,
            context = MemoryPlatformContext(accountKey, plugin.accountCredential(success)),
            timelineSpecs = emptyMap(),
            dynamicTimelineSpec = null,
            coroutineScope = scope,
        )
    }

    private suspend fun install(
        manifest: String = MANIFEST,
        script: String = SCRIPT,
    ): RunningPluginV1 {
        val input = root / "plugin.fpp"
        TestFppFactory.write(input, TestFppFactory.validEntries(manifest = manifest, script = script))
        val namespace = root / "plugin-store"
        val store = PluginStateStore.open(fileSystem, namespace)
        PluginInstaller(fileSystem, store).let { installer ->
            installer.inspect(input).also { installer.commit(it, true, activate = store::install) }
        }
        return PluginStateStore
            .open(fileSystem, namespace)
            .running.plugins
            .getValue(PLUGIN_ID)
    }
}

private class MemoryPlatformContext(
    override val accountKey: MicroBlogKey,
    private var value: PluginAccountCredentialV1,
) : PlatformDataSourceContext {
    override fun <T : Any> credential(serializer: KSerializer<T>): T {
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    override fun <T : Any> credentialFlow(serializer: KSerializer<T>): Flow<T> = kotlinx.coroutines.flow.flowOf(credential(serializer))

    override suspend fun <T : Any> updateCredential(
        serializer: KSerializer<T>,
        credential: T,
    ) {
        value = credential as PluginAccountCredentialV1
    }
}

private class NoopTransport : PluginHttpTransport {
    override suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1 =
        PluginTransportResponseV1(200, emptyMap(), "{}".encodeToByteArray())
}

private const val MANIFEST =
    """
    {
      "schemaVersion": 1,
      "apiVersion": 1,
      "id": "dev.dimension.flare.test.detail",
      "version": "1.0.0",
      "name": "Detail test",
      "permissions": { "mediaNetwork": true },
      "platform": {
        "id": "DetailTest",
        "name": "Detail test",
        "capabilities": {
          "flare.datasource.list/v1": { "operations": {
            "page": {}, "detail": {}, "create": {}, "update": {}, "delete": {}, "timeline": {},
            "members": {}, "memberships": {}, "addMember": {}, "removeMember": {}
          } },
          "flare.datasource.direct-message/v1": { "operations": {
            "rooms": {}, "room": {}, "messages": {}, "send": {}, "delete": {}, "leave": {},
            "create": {}, "badge": {}, "canSend": {}
          } },
          "flare.datasource.relation/v1": {
            "operations": { "state": {}, "mutate": {} },
            "relationActions": ["follow"]
          },
          "flare.datasource.notification/v1": {
            "operations": { "page": { "directions": ["refresh"] }, "badge": {} },
            "notificationFilters": ["all", "mention"]
          },
          "flare.datasource.article/v1": { "operations": {
            "detail": {}, "comments": { "directions": ["refresh"] }
          } },
          "flare.datasource.gallery/v1": { "operations": {
            "detail": {}, "comments": { "directions": ["refresh"] },
            "recommendations": { "directions": ["refresh"] }
          } },
          "flare.datasource.timeline/v1": {
            "operations": { "page": { "directions": ["refresh"] } }
          },
          "flare.datasource.post/v1": { "operations": { "detail": {} } },
          "flare.datasource.tab-catalog/v1": {
            "operations": { "page": { "directions": ["refresh"] } }
          }
        },
        "timelines": [
          { "id": "home", "title": "Home", "defaultForNewAccount": true }
        ]
      }
    }
    """

private const val LIST_DETAIL =
    """detail(request) { return { id: request.key.id, title: "Cold list", entityToken: "list-token" }; },"""

private const val SCRIPT =
    """
    const profile = { key: { id: "account-1", host: "plugin.example" }, handle: "test@plugin.example", displayName: "Test" };
    function post(id, detailType) {
      const value = { key: { id, host: "plugin.example" }, author: profile, createdAt: "2026-01-01T00:00:00Z", content: { value: id } };
      if (detailType) value.detailType = detailType;
      if (detailType === "link") value.url = "https://plugin.example/posts/" + id;
      return value;
    }
    definePlugin({ capabilities: {
      post: { detail(request) { return post(request.key.id); } },
      list: {
        page() { return { items: [] }; },
        $LIST_DETAIL
        create(request) { return { id: "created", title: request.title || "Created" }; },
        update(request) {
          if (request.entityToken !== "list-token") throw new Error("missing list token");
          return { id: request.id, title: request.title, entityToken: request.entityToken };
        },
        delete() { return { type: "deleted" }; },
        timeline() { return { items: [] }; },
        members() { return { items: [] }; },
        memberships() { return { items: [] }; },
        addMember() { return profile; },
        removeMember() { return { type: "deleted" }; },
      },
      directMessage: {
        rooms() { return { items: [] }; },
        room(request) { return { key: request.key, title: "Cold room", participants: [profile], entityToken: "room-token" }; },
        messages() { return { items: [] }; },
        send(request) { return { key: { id: "message-1", host: "plugin.example" }, roomKey: request.roomKey, sender: profile, createdAt: "2026-01-01T00:00:00Z", content: { format: "plain", value: request.text } }; },
        delete() { return { type: "deleted" }; },
        leave() { return { type: "deleted" }; },
        create() { return { key: { id: "room-1", host: "plugin.example" }, title: "Room", participants: [profile] }; },
        badge() { return { value: 0 }; },
        canSend() { return { value: true }; },
      },
      relation: {
        state(request) { return { profileKey: request.key }; },
        mutate() { return { type: "noChange" }; },
      },
      notification: {
        page() { return { items: [] }; },
        badge() { return { value: 0 }; },
      },
      article: {
        detail(request) { return { key: request.key, title: "Plugin article", createdAt: "2026-01-01T00:00:00Z", content: { format: "plain", value: "Article body" }, url: "https://plugin.example/articles/article-1" }; },
        comments() { return { items: [] }; },
      },
      gallery: {
        detail(request) { return { key: request.key, title: "Plugin gallery", createdAt: "2026-01-01T00:00:00Z", content: { format: "plain", value: "Gallery body" }, url: "https://plugin.example/galleries/gallery-1", images: [{ id: "image-1", type: "image", url: "https://plugin.example/images/image-1.jpg" }] }; },
        comments() { return { items: [] }; },
        recommendations() { return { items: [] }; },
      },
      timeline: {
        page(request) {
          if (request.timelineId !== "dynamic-feed") throw new Error("bad timeline");
          if (request.parameters.kind === "details") return { items: [post("default"), post("post", "post"), post("article", "article"), post("gallery", "gallery"), post("link", "link")] };
          if (request.parameters.kind !== "photos") throw new Error("bad timeline");
          return { items: [{ key: { id: "post-1", host: "plugin.example" }, author: profile, createdAt: "2026-01-01T00:00:00Z", content: { value: "Dynamic" } }] };
        },
      },
      tabCatalog: {
        page() { return { items: [] }; },
      },
    } });
    """

private const val PLUGIN_ID = "dev.dimension.flare.test.detail"
private const val ORIGIN = "https://plugin.example"

private class RecordingDatabaseUpdater : DatabaseUpdater {
    var updates = 0

    override suspend fun updateCache(
        postKey: MicroBlogKey,
        update: suspend (UiTimelineV2) -> UiTimelineV2,
    ) {
        updates++
    }

    override suspend fun deleteFromCache(postKey: MicroBlogKey) {
        updates++
    }

    override suspend fun updateActionMenu(
        postKey: MicroBlogKey,
        newActionMenu: ActionMenu.Item,
    ) = Unit
}
