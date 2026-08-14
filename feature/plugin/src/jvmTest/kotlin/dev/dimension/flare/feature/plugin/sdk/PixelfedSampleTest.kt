package dev.dimension.flare.feature.plugin.sdk

import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.capabilities
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.adapter.PluginPlatformSpecSourceV1
import dev.dimension.flare.feature.plugin.adapter.accountCredential
import dev.dimension.flare.feature.plugin.host.KtorPluginHttpTransport
import dev.dimension.flare.feature.plugin.host.PluginAsset
import dev.dimension.flare.feature.plugin.host.PluginCallTimeoutV1
import dev.dimension.flare.feature.plugin.host.PluginCredentialAccess
import dev.dimension.flare.feature.plugin.host.PluginHttpTransport
import dev.dimension.flare.feature.plugin.host.PluginInvocationContextV1
import dev.dimension.flare.feature.plugin.host.PluginTransportBodyV1
import dev.dimension.flare.feature.plugin.host.PluginTransportMultipartPartV1
import dev.dimension.flare.feature.plugin.host.PluginTransportRequestV1
import dev.dimension.flare.feature.plugin.host.PluginTransportResponseV1
import dev.dimension.flare.feature.plugin.installer.PluginInstaller
import dev.dimension.flare.feature.plugin.lifecycle.PluginRunningSnapshotV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginStateStore
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.login.PluginFormLoginCoordinatorV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthLoginCoordinatorV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthPendingStoreV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthPendingV1
import dev.dimension.flare.feature.plugin.login.PluginWebCookieLoginCoordinatorV1
import dev.dimension.flare.feature.plugin.runtime.PluginCallException
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimeKeyV1
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimePool
import dev.dimension.flare.feature.plugin.wire.ComposeAssetV1
import dev.dimension.flare.feature.plugin.wire.ComposeRequestV1
import dev.dimension.flare.feature.plugin.wire.ComposeResultV1
import dev.dimension.flare.feature.plugin.wire.DetectorMatchV1
import dev.dimension.flare.feature.plugin.wire.DetectorRequestV1
import dev.dimension.flare.feature.plugin.wire.DetectorResultV1
import dev.dimension.flare.feature.plugin.wire.EntityKeyV1
import dev.dimension.flare.feature.plugin.wire.EntityPageRequestV1
import dev.dimension.flare.feature.plugin.wire.EntityRequestV1
import dev.dimension.flare.feature.plugin.wire.HandleRequestV1
import dev.dimension.flare.feature.plugin.wire.HashtagV1
import dev.dimension.flare.feature.plugin.wire.LoginBeginRequestV1
import dev.dimension.flare.feature.plugin.wire.LoginResumeRequestV1
import dev.dimension.flare.feature.plugin.wire.LoginTransitionV1
import dev.dimension.flare.feature.plugin.wire.MutationRequestV1
import dev.dimension.flare.feature.plugin.wire.MutationResultV1
import dev.dimension.flare.feature.plugin.wire.PageDirectionV1
import dev.dimension.flare.feature.plugin.wire.PageRequestV1
import dev.dimension.flare.feature.plugin.wire.PageV1
import dev.dimension.flare.feature.plugin.wire.PluginAccountCredentialV1
import dev.dimension.flare.feature.plugin.wire.PluginErrorCodeV1
import dev.dimension.flare.feature.plugin.wire.PostV1
import dev.dimension.flare.feature.plugin.wire.ProfileTimelineRequestV1
import dev.dimension.flare.feature.plugin.wire.ProfileV1
import dev.dimension.flare.feature.plugin.wire.SearchRequestV1
import dev.dimension.flare.feature.plugin.wire.SemanticActionV1
import dev.dimension.flare.feature.plugin.wire.TimelinePageRequestV1
import dev.dimension.flare.feature.plugin.wire.VisibilityV1
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.Source
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PixelfedSampleTest {
    private lateinit var root: Path

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("pixelfed-sample-test")
    }

    @AfterTest
    fun tearDown() {
        FileSystem.SYSTEM.deleteRecursively(root.toOkioPath(), mustExist = false)
    }

    @Test
    fun actualPackageIsDeterministicAndExercisesTheV1Runtime() =
        runBlocking {
            val firstPackage = root.resolve("first.fpp")
            val secondPackage = root.resolve("second.fpp")
            pack(firstPackage)
            pack(secondPackage)
            assertContentEquals(firstPackage.readBytes(), secondPackage.readBytes())

            val plugin = install(firstPackage)
            assertEquals(PLUGIN_ID, plugin.installed.pluginId)
            assertEquals("Pixelfed", plugin.installed.manifest.platform.id)
            assertTrue(
                plugin.installed.manifest.platform.timelines
                    .single { it.id == "home" }
                    .defaultForNewAccount,
            )

            val transport = PixelfedFixtureTransport()
            val pool = PluginRuntimePool(FileSystem.SYSTEM, transport)
            val platformScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            var stage = "detector"
            try {
                val detector =
                    pool.invoke(
                        plugin = plugin,
                        key = PluginRuntimeKeyV1.detector(PLUGIN_ID, plugin.installed.packageHash, "detector"),
                        context = detectorContext(plugin),
                        method = "detector.detect",
                        request = DetectorRequestV1(ORIGIN),
                        requestSerializer = DetectorRequestV1.serializer(),
                        responseSerializer = DetectorResultV1.serializer(),
                    )
                assertEquals(DetectorMatchV1.Exact, detector.match)
                assertEquals("Pixelfed", detector.software)
                assertEquals("pixelfed.social", detector.instance?.domain)
                assertTrue(!detector.compatibleMode)

                stage = "OAuth begin"
                val loginContext = loginContext(plugin)
                val loginKey = PluginRuntimeKeyV1.login(PLUGIN_ID, plugin.installed.packageHash, "flow")
                val begin =
                    pool.invoke(
                        plugin = plugin,
                        key = loginKey,
                        context = loginContext,
                        method = "login.oauth.begin",
                        request =
                            LoginBeginRequestV1(
                                methodId = "oauth",
                                origin = ORIGIN,
                                flowId = "flow",
                                state = "state-value",
                                redirectUri = REDIRECT_URI,
                            ),
                        requestSerializer = LoginBeginRequestV1.serializer(),
                        responseSerializer = LoginTransitionV1.serializer(),
                    )
                val browser = assertIs<LoginTransitionV1.ExternalBrowser>(begin)
                assertTrue(browser.url.startsWith("$ORIGIN/oauth/authorize?"))
                assertTrue("state=state-value" in browser.url)

                stage = "OAuth resume"
                val resume =
                    pool.invoke(
                        plugin = plugin,
                        key = loginKey,
                        context = loginContext,
                        method = "login.oauth.resume",
                        request =
                            LoginResumeRequestV1(
                                methodId = "oauth",
                                origin = ORIGIN,
                                flowId = "flow",
                                redirectUri = REDIRECT_URI,
                                callbackParameters = mapOf("code" to "oauth-code", "state" to "state-value"),
                                pendingPayload = browser.pendingPayload,
                            ),
                        requestSerializer = LoginResumeRequestV1.serializer(),
                        responseSerializer = LoginTransitionV1.serializer(),
                        timeout = PluginCallTimeoutV1.Extended,
                    )
                val success = assertIs<LoginTransitionV1.Success>(resume).value
                assertEquals("42", success.accountId)
                assertEquals("pixelfed.social", success.profile.key.host)
                assertEquals(1, success.composeConfig?.media?.minCountForNew)
                assertEquals(
                    setOf(VisibilityV1.Public, VisibilityV1.Unlisted, VisibilityV1.Followers),
                    success.composeConfig?.visibility?.allowed,
                )

                val credential = MemoryCredential(success.credential)
                val accountKey = PluginRuntimeKeyV1.account(PLUGIN_ID, plugin.installed.packageHash, ORIGIN, "42")
                val accountContext = accountContext(plugin, credential)
                val spec = plugin.platformSpec(pool, platformScope)
                assertEquals("Pixelfed", spec.platformId)
                assertEquals("file://${plugin.iconPath}", spec.metadata.iconUrl)
                val platformContext =
                    MemoryPlatformDataSourceContext(
                        accountKey = MicroBlogKey("42", "pixelfed.social"),
                        credential = plugin.accountCredential(success),
                    )
                val dataSource = spec.createDataSource(platformContext)
                val capabilities = dataSource.capabilities
                assertNotNull(capabilities.timeline)
                assertNotNull(capabilities.search)
                assertNotNull(capabilities.profile)
                assertNotNull(capabilities.post)
                assertNotNull(capabilities.compose)
                assertNotNull(capabilities.tabCatalog?.configuration)
                assertNull(capabilities.relation)
                assertNull(capabilities.notification)
                assertNull(capabilities.list)
                assertNull(capabilities.directMessage)
                assertEquals(
                    1,
                    capabilities.tabCatalog
                        ?.configuration
                        ?.defaultTabs
                        ?.size,
                )
                assertEquals(
                    6,
                    capabilities.tabCatalog
                        ?.configuration
                        ?.builtInTimelineTabs
                        ?.size,
                )
                assertEquals(
                    1,
                    capabilities.compose
                        ?.composeConfig(ComposeType.New)
                        ?.media
                        ?.minCountForNew,
                )

                stage = "adapter timeline"
                val timeline = capabilities.timeline!!.homeTimeline().load(1, PagingRequest.Refresh)
                assertEquals(
                    "100",
                    timeline.data
                        .single()
                        .statusKey.id,
                )
                assertEquals("$ORIGIN/api/v1/timelines/home?max_id=100", timeline.nextKey)

                stage = "adapter search"
                val search = capabilities.search!!.searchStatus("photo").load(1, PagingRequest.Refresh)
                assertEquals(
                    "100",
                    search.data
                        .single()
                        .statusKey.id,
                )

                stage = "adapter profile timeline"
                val gallery =
                    capabilities.profile!!
                        .userTimeline(MicroBlogKey("42", "pixelfed.social"), mediaOnly = true)
                        .load(1, PagingRequest.Refresh)
                assertEquals(
                    "100",
                    gallery.data
                        .single()
                        .statusKey.id,
                )
                assertTrue(transport.requests.any { "only_media=true" in it.url })

                stage = "adapter post detail"
                val detail =
                    capabilities.post!!
                        .postHandler.loader
                        .status(MicroBlogKey("100", "pixelfed.social"))
                assertEquals("100", detail.statusKey.id)

                stage = "post mutation"
                val mutation =
                    pool.invoke(
                        plugin = plugin,
                        key = accountKey,
                        context = accountContext,
                        method = "capabilities.post.mutate",
                        request = MutationRequestV1(EntityKeyV1("100", "pixelfed.social"), SemanticActionV1.Favourite),
                        requestSerializer = MutationRequestV1.serializer(),
                        responseSerializer = MutationResultV1.serializer(),
                    )
                assertEquals(
                    true,
                    assertIs<MutationResultV1.UpdatedPost>(mutation)
                        .post.actions
                        .first {
                            it.action ==
                                SemanticActionV1.Unfavourite
                        }.active,
                )

                stage = "not-found mapping"
                val notFound =
                    assertFailsWith<PluginCallException> {
                        pool.invoke(
                            plugin = plugin,
                            key = accountKey,
                            context = accountContext,
                            method = "capabilities.post.detail",
                            request = EntityRequestV1(EntityKeyV1("missing", "pixelfed.social")),
                            requestSerializer = EntityRequestV1.serializer(),
                            responseSerializer = PostV1.serializer(),
                        )
                    }
                assertEquals(PluginErrorCodeV1.NotFound, notFound.error.code)

                stage = "compose"
                val composeContext =
                    accountContext(
                        plugin,
                        credential,
                        mapOf("photo" to MemoryAsset("photo.jpg", "image/jpeg", "fixture".encodeToByteArray())),
                    )
                val compose =
                    pool.invoke(
                        plugin = plugin,
                        key = accountKey,
                        context = composeContext,
                        method = "capabilities.compose.publish",
                        request =
                            ComposeRequestV1(
                                text = "A sample photo",
                                visibility = VisibilityV1.Public,
                                assets = listOf(ComposeAssetV1("photo", "photo.jpg", "image/jpeg", "Alt text")),
                            ),
                        requestSerializer = ComposeRequestV1.serializer(),
                        responseSerializer = ComposeResultV1.serializer(),
                        timeout = PluginCallTimeoutV1.Extended,
                    )
                assertEquals("101", compose.post.key.id)

                val appRequest = transport.requests.single { it.url == "$ORIGIN/api/v1/apps" }
                assertEquals(REDIRECT_URI, assertIs<PluginTransportBodyV1.Form>(appRequest.body).values["redirect_uris"])
                val upload = transport.requests.single { it.url == "$ORIGIN/api/v1/media" }
                val uploadParts = assertIs<PluginTransportBodyV1.Multipart>(upload.body).parts
                assertEquals("photo.jpg", assertIs<PluginTransportMultipartPartV1.Asset>(uploadParts.first()).fileName)
                val publish = transport.requests.single { it.url == "$ORIGIN/api/v1/statuses" && it.method == "POST" }
                assertNotNull(publish.headers["Idempotency-Key"])
                assertTrue("\"media_ids\":[\"media-1\"]" in assertIs<PluginTransportBodyV1.Text>(publish.body).value)
            } catch (error: Throwable) {
                throw AssertionError("Pixelfed sample failed during $stage after ${transport.requests}", error)
            } finally {
                platformScope.cancel()
                pool.close()
            }
        }

    @Test
    fun everyDeclaredCapabilityPaginationAndRemoteErrorIsExecutable() =
        runBlocking {
            val packagePath = root.resolve("capabilities.fpp")
            pack(packagePath)
            val plugin = install(packagePath)
            val transport = PixelfedFixtureTransport()
            val pool = PluginRuntimePool(FileSystem.SYSTEM, transport)
            val credential = MemoryCredential(buildJsonObject { put("accessToken", "token") })
            val key = PluginRuntimeKeyV1.account(PLUGIN_ID, plugin.installed.packageHash, ORIGIN, "42")
            val context = accountContext(plugin, credential)
            try {
                val postPageSerializer = PageV1.serializer(PostV1.serializer())
                val profilePageSerializer = PageV1.serializer(ProfileV1.serializer())

                listOf("home", "discover", "local", "federated", "bookmarks", "favourites").forEach { timelineId ->
                    val page =
                        pool.invokeAccount(
                            plugin,
                            key,
                            context,
                            "capabilities.timeline.page",
                            TimelinePageRequestV1(timelineId, pageRequest(), emptyMap()),
                            TimelinePageRequestV1.serializer(),
                            postPageSerializer,
                        )
                    assertEquals(
                        "100",
                        page.items
                            .single()
                            .key.id,
                        timelineId,
                    )
                }
                assertTrue(transport.requests.any { it.url.contains("/timelines/public?") && "local=true" in it.url })
                assertTrue(transport.requests.any { it.url.contains("/timelines/public?") && "local=false" in it.url })

                val bookmarkPage =
                    pool.invokeAccount(
                        plugin,
                        key,
                        context,
                        "capabilities.timeline.page",
                        TimelinePageRequestV1("bookmarks", pageRequest(), emptyMap()),
                        TimelinePageRequestV1.serializer(),
                        postPageSerializer,
                    )
                assertEquals("max_id:100", bookmarkPage.olderCursor)
                pool.invokeAccount(
                    plugin,
                    key,
                    context,
                    "capabilities.timeline.page",
                    TimelinePageRequestV1("bookmarks", pageRequest().copy(cursor = bookmarkPage.olderCursor), emptyMap()),
                    TimelinePageRequestV1.serializer(),
                    postPageSerializer,
                )
                assertTrue(transport.requests.any { "/api/v1/bookmarks?" in it.url && "max_id=100" in it.url })
                val completedPage =
                    pool.invokeAccount(
                        plugin,
                        key,
                        context,
                        "capabilities.timeline.page",
                        TimelinePageRequestV1("favourites", pageRequest().copy(limit = 2), emptyMap()),
                        TimelinePageRequestV1.serializer(),
                        postPageSerializer,
                    )
                assertTrue(completedPage.endReached)
                assertNull(completedPage.olderCursor)

                listOf("posts", "discoverPosts").forEach { operation ->
                    val page =
                        pool.invokeAccount(
                            plugin,
                            key,
                            context,
                            "capabilities.search.$operation",
                            SearchRequestV1("photo", pageRequest()),
                            SearchRequestV1.serializer(),
                            postPageSerializer,
                        )
                    assertEquals(
                        "100",
                        page.items
                            .single()
                            .key.id,
                        operation,
                    )
                }
                val completedSearch =
                    pool.invokeAccount(
                        plugin,
                        key,
                        context,
                        "capabilities.search.posts",
                        SearchRequestV1("photo", pageRequest().copy(limit = 2)),
                        SearchRequestV1.serializer(),
                        postPageSerializer,
                    )
                assertTrue(completedSearch.endReached)
                assertNull(completedSearch.olderCursor)
                listOf("profiles", "discoverProfiles").forEach { operation ->
                    val page =
                        pool.invokeAccount(
                            plugin,
                            key,
                            context,
                            "capabilities.search.$operation",
                            SearchRequestV1("alice", pageRequest()),
                            SearchRequestV1.serializer(),
                            profilePageSerializer,
                        )
                    assertEquals(
                        "42",
                        page.items
                            .single()
                            .key.id,
                        operation,
                    )
                }
                val hashtags =
                    pool.invokeAccount(
                        plugin,
                        key,
                        context,
                        "capabilities.search.discoverHashtags",
                        SearchRequestV1("", pageRequest()),
                        SearchRequestV1.serializer(),
                        PageV1.serializer(HashtagV1.serializer()),
                    )
                assertEquals("photography", hashtags.items.single().name)

                val byId =
                    pool.invokeAccount(
                        plugin,
                        key,
                        context,
                        "capabilities.profile.byId",
                        EntityRequestV1(EntityKeyV1("42", "pixelfed.social")),
                        EntityRequestV1.serializer(),
                        ProfileV1.serializer(),
                    )
                assertEquals("@alice@pixelfed.social", byId.handle)
                val byHandle =
                    pool.invokeAccount(
                        plugin,
                        key,
                        context,
                        "capabilities.profile.byHandle",
                        HandleRequestV1("@alice", "pixelfed.social"),
                        HandleRequestV1.serializer(),
                        ProfileV1.serializer(),
                    )
                assertEquals("42", byHandle.key.id)
                val profileTimeline =
                    pool.invokeAccount(
                        plugin,
                        key,
                        context,
                        "capabilities.profile.timeline",
                        ProfileTimelineRequestV1(EntityKeyV1("42", "pixelfed.social"), "gallery", pageRequest()),
                        ProfileTimelineRequestV1.serializer(),
                        postPageSerializer,
                    )
                assertEquals(
                    "100",
                    profileTimeline.items
                        .single()
                        .key.id,
                )
                listOf("following", "followers").forEach { operation ->
                    val page =
                        pool.invokeAccount(
                            plugin,
                            key,
                            context,
                            "capabilities.profile.$operation",
                            EntityPageRequestV1(EntityKeyV1("42", "pixelfed.social"), pageRequest()),
                            EntityPageRequestV1.serializer(),
                            profilePageSerializer,
                        )
                    assertEquals(
                        "42",
                        page.items
                            .single()
                            .key.id,
                        operation,
                    )
                }

                val postRequest = EntityRequestV1(EntityKeyV1("100", "pixelfed.social"))
                val detail =
                    pool.invokeAccount(
                        plugin,
                        key,
                        context,
                        "capabilities.post.detail",
                        postRequest,
                        EntityRequestV1.serializer(),
                        PostV1.serializer(),
                    )
                assertEquals("100", detail.key.id)
                val thread =
                    pool.invokeAccount(
                        plugin,
                        key,
                        context,
                        "capabilities.post.context",
                        EntityPageRequestV1(postRequest.key, pageRequest()),
                        EntityPageRequestV1.serializer(),
                        postPageSerializer,
                    )
                assertEquals(listOf("99", "100", "101"), thread.items.map { it.key.id })
                val deleted =
                    pool.invokeAccount(
                        plugin,
                        key,
                        context,
                        "capabilities.post.delete",
                        postRequest,
                        EntityRequestV1.serializer(),
                        MutationResultV1.serializer(),
                    )
                assertIs<MutationResultV1.Deleted>(deleted)
                SemanticActionV1.entries
                    .filter {
                        it in
                            setOf(
                                SemanticActionV1.Favourite,
                                SemanticActionV1.Unfavourite,
                                SemanticActionV1.Repost,
                                SemanticActionV1.Unrepost,
                                SemanticActionV1.Bookmark,
                                SemanticActionV1.Unbookmark,
                            )
                    }.forEach { action ->
                        val result =
                            pool.invokeAccount(
                                plugin,
                                key,
                                context,
                                "capabilities.post.mutate",
                                MutationRequestV1(postRequest.key, action),
                                MutationRequestV1.serializer(),
                                MutationResultV1.serializer(),
                            )
                        assertIs<MutationResultV1.UpdatedPost>(result)
                    }

                val composeError =
                    assertFailsWith<PluginCallException> {
                        pool.invokeAccount(
                            plugin,
                            key,
                            context,
                            "capabilities.compose.publish",
                            ComposeRequestV1(text = "media required", visibility = VisibilityV1.Public),
                            ComposeRequestV1.serializer(),
                            ComposeResultV1.serializer(),
                        )
                    }
                assertEquals(PluginErrorCodeV1.Validation, composeError.error.code)

                mapOf(
                    "unauthorized" to PluginErrorCodeV1.AuthenticationRequired,
                    "invalid" to PluginErrorCodeV1.Validation,
                    "limited" to PluginErrorCodeV1.RateLimited,
                    "broken" to PluginErrorCodeV1.InvalidResponse,
                    "remote" to PluginErrorCodeV1.Remote,
                ).forEach { (id, expected) ->
                    val error =
                        assertFailsWith<PluginCallException>(id) {
                            pool.invokeAccount(
                                plugin,
                                key,
                                context,
                                "capabilities.post.detail",
                                EntityRequestV1(EntityKeyV1(id, "pixelfed.social")),
                                EntityRequestV1.serializer(),
                                PostV1.serializer(),
                            )
                        }
                    assertEquals(expected, error.error.code, id)
                    if (id == "limited") assertEquals(17, error.error.retryAfterSeconds)
                }

                transport.instanceBody = MASTODON_INSTANCE
                val detector =
                    pool.invoke(
                        plugin = plugin,
                        key = PluginRuntimeKeyV1.detector(PLUGIN_ID, plugin.installed.packageHash, "mastodon-detector"),
                        context = detectorContext(plugin),
                        method = "detector.detect",
                        request = DetectorRequestV1(ORIGIN),
                        requestSerializer = DetectorRequestV1.serializer(),
                        responseSerializer = DetectorResultV1.serializer(),
                    )
                assertEquals(DetectorMatchV1.None, detector.match)
            } finally {
                pool.close()
            }
        }

    @Test
    fun timelineSchemaChangeRetiresExistingPluginTimelineSpecs() =
        runBlocking {
            val packagePath = root.resolve("timeline-schema.fpp")
            pack(packagePath)
            val plugin = install(packagePath)
            val changed =
                plugin.copy(
                    installed =
                        plugin.installed.copy(
                            manifest =
                                plugin.installed.manifest.copy(
                                    platform =
                                        plugin.installed.manifest.platform.copy(
                                            timelineSchemaVersion = 2,
                                        ),
                                ),
                        ),
                )
            val pool = PluginRuntimePool(FileSystem.SYSTEM, PixelfedFixtureTransport())
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            try {
                val originalIds =
                    plugin
                        .platformSpec(pool, scope)
                        .timelineSpecs
                        .map { it.id }
                        .toSet()
                val changedIds =
                    changed
                        .platformSpec(pool, scope)
                        .timelineSpecs
                        .map { it.id }
                        .toSet()

                assertTrue(originalIds.any { ":schema1:" in it })
                assertTrue(changedIds.any { ":schema2:" in it })
                assertTrue(originalIds.any { it.startsWith("plugin_") && "_schema" !in it })
                assertTrue(changedIds.any { "_schema2_" in it })
                assertTrue(originalIds.intersect(changedIds).isEmpty())
            } finally {
                scope.cancel()
                pool.close()
            }
        }

    @Test
    fun optionalRealInstanceSmoke() =
        runBlocking {
            val configuredHost = System.getenv("PIXELFED_TEST_HOST")?.trim()?.takeIf(String::isNotEmpty) ?: return@runBlocking
            val origin =
                if (configuredHost.startsWith("https://")) {
                    configuredHost.trimEnd('/')
                } else {
                    "https://${configuredHost.trimEnd('/')}"
                }
            val packagePath = root.resolve("real-instance.fpp")
            pack(packagePath)
            val plugin = install(packagePath)
            val transport = KtorPluginHttpTransport(OkHttp.create())
            val pool = PluginRuntimePool(FileSystem.SYSTEM, transport)
            try {
                val detector =
                    pool.invoke(
                        plugin = plugin,
                        key = PluginRuntimeKeyV1.detector(PLUGIN_ID, plugin.installed.packageHash, "real-detector"),
                        context = detectorContext(plugin, origin),
                        method = "detector.detect",
                        request = DetectorRequestV1(origin),
                        requestSerializer = DetectorRequestV1.serializer(),
                        responseSerializer = DetectorResultV1.serializer(),
                    )
                assertEquals(DetectorMatchV1.Exact, detector.match)

                val token = System.getenv("PIXELFED_TEST_TOKEN")?.trim()?.takeIf(String::isNotEmpty) ?: return@runBlocking
                val credential = MemoryCredential(buildJsonObject { put("accessToken", token) })
                val accountId = "real-smoke"
                val key = PluginRuntimeKeyV1.account(PLUGIN_ID, plugin.installed.packageHash, origin, accountId)
                val context = accountContext(plugin, credential, origin = origin, accountId = accountId)
                val timeline =
                    pool.invoke(
                        plugin = plugin,
                        key = key,
                        context = context,
                        method = "capabilities.timeline.page",
                        request = TimelinePageRequestV1("home", PageRequestV1(PageDirectionV1.Refresh, 5), emptyMap()),
                        requestSerializer = TimelinePageRequestV1.serializer(),
                        responseSerializer = PageV1.serializer(PostV1.serializer()),
                    )
                assertTrue(timeline.items.size <= 5)

                if (System.getenv("PIXELFED_TEST_ALLOW_WRITE") != "1") return@runBlocking
                val icon = locateSdk().resolve("examples/pixelfed/assets/icon.png").readBytes()
                val writeContext =
                    accountContext(
                        plugin = plugin,
                        credential = credential,
                        assets = mapOf("smoke-image" to MemoryAsset("flare-plugin-smoke.png", "image/png", icon)),
                        origin = origin,
                        accountId = accountId,
                    )
                var created: EntityKeyV1? = null
                try {
                    val result =
                        pool.invoke(
                            plugin = plugin,
                            key = key,
                            context = writeContext,
                            method = "capabilities.compose.publish",
                            request =
                                ComposeRequestV1(
                                    text = "Flare Social Plugin API automated smoke test",
                                    visibility = VisibilityV1.Unlisted,
                                    assets = listOf(ComposeAssetV1("smoke-image", "flare-plugin-smoke.png", "image/png")),
                                ),
                            requestSerializer = ComposeRequestV1.serializer(),
                            responseSerializer = ComposeResultV1.serializer(),
                            timeout = PluginCallTimeoutV1.Extended,
                        )
                    created = result.post.key
                } finally {
                    created?.let { postKey ->
                        pool.invoke(
                            plugin = plugin,
                            key = key,
                            context = context,
                            method = "capabilities.post.delete",
                            request = EntityRequestV1(postKey),
                            requestSerializer = EntityRequestV1.serializer(),
                            responseSerializer = MutationResultV1.serializer(),
                        )
                    }
                }
            } finally {
                pool.close()
                transport.close()
            }
        }

    private suspend fun install(packagePath: Path): RunningPluginV1 {
        val namespace = root.resolve("state").toOkioPath()
        val store = PluginStateStore.open(FileSystem.SYSTEM, namespace)
        val installer = PluginInstaller(FileSystem.SYSTEM, store)
        installer.commit(installer.inspect(packagePath.toOkioPath()), confirmed = true)
        return PluginStateStore
            .open(FileSystem.SYSTEM, namespace)
            .running.plugins
            .getValue(PLUGIN_ID)
    }

    private fun pack(output: Path) {
        val sdk = locateSdk()
        val process =
            ProcessBuilder("node", sdk.resolve("pack.mjs").toString(), sdk.resolve("examples/pixelfed").toString(), output.toString())
                .redirectErrorStream(true)
                .start()
        val message = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) { "Pixelfed package failed: $message" }
    }

    private fun locateSdk(): Path =
        sequenceOf(Paths.get("sdk"), Paths.get("feature/plugin/sdk"))
            .map(Path::toAbsolutePath)
            .firstOrNull { Files.isRegularFile(it.resolve("pack.mjs")) }
            ?: error("feature/plugin/sdk was not found")
}

private class MemoryCredential(
    private var value: JsonElement,
) : PluginCredentialAccess {
    override suspend fun read(): JsonElement = value

    override suspend fun replace(value: JsonElement) {
        this.value = value
    }
}

private class MemoryPlatformDataSourceContext(
    override val accountKey: MicroBlogKey,
    credential: PluginAccountCredentialV1,
) : PlatformDataSourceContext {
    private var value = PluginJsonV1.encodeToJsonElement(PluginAccountCredentialV1.serializer(), credential)

    override fun <T : Any> credential(serializer: KSerializer<T>): T = PluginJsonV1.decodeFromJsonElement(serializer, value)

    override fun <T : Any> credentialFlow(serializer: KSerializer<T>): Flow<T> = flowOf(credential(serializer))

    override suspend fun <T : Any> updateCredential(
        serializer: KSerializer<T>,
        credential: T,
    ) {
        value = PluginJsonV1.encodeToJsonElement(serializer, credential)
    }
}

private class MemoryOAuthPendingStore : PluginOAuthPendingStoreV1 {
    private val values = mutableMapOf<String, PluginOAuthPendingV1>()

    override suspend fun save(pending: PluginOAuthPendingV1) {
        values[pending.flowId] = pending
    }

    override suspend fun load(flowId: String): PluginOAuthPendingV1? = values[flowId]

    override suspend fun consume(pending: PluginOAuthPendingV1): Boolean = values.remove(pending.flowId, pending)
}

private class MemoryAsset(
    override val fileName: String?,
    override val mimeType: String?,
    private val bytes: ByteArray,
) : PluginAsset {
    override val size: Long = bytes.size.toLong()

    override fun openSource(): Source = Buffer().write(bytes)
}

private class PixelfedFixtureTransport : PluginHttpTransport {
    val requests = mutableListOf<PluginTransportRequestV1>()
    var instanceBody: String = INSTANCE

    override suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1 {
        requests += request
        val path = URI(request.url).path
        return when {
            path == "/api/v1/instance" -> {
                response(instanceBody)
            }

            path == "/api/v1/apps" -> {
                response("""{"client_id":"client","client_secret":"secret"}""")
            }

            path == "/oauth/token" -> {
                response("""{"access_token":"token"}""")
            }

            path == "/api/v1/accounts/verify_credentials" -> {
                response(ACCOUNT)
            }

            path == "/api/v1/timelines/home" -> {
                response("[$STATUS]", mapOf("Link" to listOf("<$ORIGIN/api/v1/timelines/home?max_id=100>; rel=\"next\"")))
            }

            path == "/api/v1/timelines/public" || path == "/api/v1/bookmarks" || path == "/api/v1/favourites" -> {
                response("[$STATUS]")
            }

            path == "/api/v1/discover/posts" -> {
                response("""{"data":[{"post":$STATUS}]}""")
            }

            path == "/api/v1.1/discover/accounts/popular" -> {
                response("""{"accounts":[{"account":$ACCOUNT}]}""")
            }

            path == "/api/v1.1/discover/posts/hashtags" -> {
                response("""{"hashtags":[{"name":"photography","url":"$ORIGIN/tags/photography"}]}""")
            }

            path == "/api/v1/accounts/42" || path == "/api/v1/accounts/lookup" -> {
                response(ACCOUNT)
            }

            path == "/api/v1/accounts/42/statuses" -> {
                response("[$STATUS]")
            }

            path == "/api/v1/accounts/42/following" || path == "/api/v1/accounts/42/followers" -> {
                response("[$ACCOUNT]")
            }

            path == "/api/v2/search" -> {
                response("""{"statuses":[$STATUS],"accounts":[$ACCOUNT]}""")
            }

            path == "/api/v1/statuses/100/context" -> {
                response("""{"ancestors":[${statusWithId("99")}],"descendants":[${statusWithId("101")}]}""")
            }

            path.startsWith("/api/v1/statuses/100/") &&
                path.substringAfterLast('/') in
                setOf("favourite", "unfavourite", "reblog", "unreblog", "bookmark", "unbookmark") -> {
                response(
                    STATUS
                        .replace("\"favourited\":false", "\"favourited\":${path.endsWith("/favourite")}")
                        .replace("\"reblogged\":false", "\"reblogged\":${path.endsWith("/reblog")}")
                        .replace("\"bookmarked\":false", "\"bookmarked\":${path.endsWith("/bookmark")}"),
                )
            }

            path == "/api/v1/statuses/100" && request.method == "DELETE" -> {
                response("", status = 204)
            }

            path == "/api/v1/statuses/100" -> {
                response(STATUS)
            }

            path == "/api/v1/statuses/missing" -> {
                response("""{"error":"missing"}""", status = 404)
            }

            path == "/api/v1/statuses/unauthorized" -> {
                response("""{"error":"invalid_token"}""", status = 401)
            }

            path == "/api/v1/statuses/invalid" -> {
                response("""{"error":"caption is too long"}""", status = 422)
            }

            path == "/api/v1/statuses/limited" -> {
                response("""{"error":"slow_down"}""", mapOf("Retry-After" to listOf("17")), status = 429)
            }

            path == "/api/v1/statuses/broken" -> {
                response("not-json")
            }

            path == "/api/v1/statuses/remote" -> {
                response("""{"error":"upstream_failed"}""", status = 503)
            }

            path == "/api/v1/media" -> {
                response("""{"id":"media-1"}""")
            }

            path == "/api/v1/statuses" && request.method == "POST" -> {
                response(STATUS.replace("\"100\"", "\"101\""))
            }

            else -> {
                error("Unexpected Pixelfed fixture request: ${request.method} ${request.url}")
            }
        }
    }

    private fun response(
        body: String,
        headers: Map<String, List<String>> = emptyMap(),
        status: Int = 200,
    ): PluginTransportResponseV1 = PluginTransportResponseV1(status, headers, body.encodeToByteArray())
}

private fun statusWithId(id: String): String = STATUS.replace("\"id\":\"100\"", "\"id\":\"$id\"")

private fun RunningPluginV1.platformSpec(
    pool: PluginRuntimePool,
    scope: CoroutineScope,
) = PluginPlatformSpecSourceV1(
    running =
        PluginRunningSnapshotV1(
            plugins = mapOf(installed.pluginId to this),
            referencedPackageHashes = setOf(installed.packageHash),
            issues = emptyList(),
            indexHealthy = true,
        ),
    runtimePool = pool,
    oauth = PluginOAuthLoginCoordinatorV1(pool, MemoryOAuthPendingStore(), { id -> takeIf { installed.pluginId == id } }),
    form = PluginFormLoginCoordinatorV1(pool),
    webCookie = PluginWebCookieLoginCoordinatorV1(pool),
    coroutineScope = scope,
).load(emptySet()).single()

private fun detectorContext(
    plugin: RunningPluginV1,
    origin: String = ORIGIN,
): PluginInvocationContextV1 = PluginInvocationContextV1.detector(PLUGIN_ID, "Pixelfed", plugin.installed.packageHash, origin, "en")

private fun loginContext(plugin: RunningPluginV1): PluginInvocationContextV1 =
    PluginInvocationContextV1.login(PLUGIN_ID, "Pixelfed", plugin.installed.packageHash, ORIGIN, emptySet(), "en")

private fun accountContext(
    plugin: RunningPluginV1,
    credential: PluginCredentialAccess,
    assets: Map<String, PluginAsset> = emptyMap(),
    origin: String = ORIGIN,
    accountId: String = "42",
): PluginInvocationContextV1 =
    PluginInvocationContextV1.account(
        PLUGIN_ID,
        "Pixelfed",
        plugin.installed.packageHash,
        origin,
        accountId,
        "en",
        credential,
        assets,
    )

private suspend fun <Request, Response> PluginRuntimePool.invokeAccount(
    plugin: RunningPluginV1,
    key: PluginRuntimeKeyV1,
    context: PluginInvocationContextV1,
    method: String,
    request: Request,
    requestSerializer: KSerializer<Request>,
    responseSerializer: KSerializer<Response>,
): Response =
    invoke(
        plugin = plugin,
        key = key,
        context = context,
        method = method,
        request = request,
        requestSerializer = requestSerializer,
        responseSerializer = responseSerializer,
    )

private fun pageRequest(): PageRequestV1 = PageRequestV1(PageDirectionV1.Refresh, 1)

private const val PLUGIN_ID = "dev.dimension.flare.sample.pixelfed"
private const val ORIGIN = "https://pixelfed.social"
private const val REDIRECT_URI = "flare://Callback/SignIn/Plugin/flow"
private const val INSTANCE =
    """{"uri":"pixelfed.social","title":"Pixelfed Social","version":"3.5.3 (compatible; Pixelfed 0.12.7)","description":"Photos","stats":{"user_count":10},"registrations":true,"configuration":{"statuses":{"max_characters":500,"max_media_attachments":4},"media_attachments":{"image_size_limit":10485760,"video_size_limit":20971520,"description_limit":1500,"supported_mime_types":["image/jpeg","image/png","video/mp4"]}}}"""
private const val MASTODON_INSTANCE =
    """{"uri":"pixelfed.social","title":"Mastodon","version":"4.5.0","description":"Not Pixelfed"}"""
private const val ACCOUNT =
    """{"id":"42","username":"alice","acct":"alice","display_name":"Alice","note":"<p>Photos</p>","avatar":"https://pixelfed.social/avatar.jpg","header":"https://pixelfed.social/header.jpg","url":"https://pixelfed.social/alice","followers_count":3,"following_count":4,"statuses_count":5,"locked":false,"bot":false,"fields":[]}"""
private const val STATUS =
    """{"id":"100","account":$ACCOUNT,"created_at":"2026-08-14T00:00:00Z","content":"<p>A photo</p>","url":"https://pixelfed.social/p/example","media_attachments":[{"id":"media","type":"image","url":"https://pixelfed.social/media.jpg","preview_url":"https://pixelfed.social/preview.jpg","meta":{"original":{"width":800,"height":600}}}],"sensitive":false,"visibility":"public","favourites_count":1,"reblogs_count":2,"replies_count":0,"favourited":false,"reblogged":false,"bookmarked":false}"""
