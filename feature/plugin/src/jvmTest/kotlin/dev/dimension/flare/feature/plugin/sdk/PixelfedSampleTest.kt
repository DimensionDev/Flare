package dev.dimension.flare.feature.plugin.sdk

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
import dev.dimension.flare.feature.plugin.lifecycle.PluginStateStore
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
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
import dev.dimension.flare.feature.plugin.wire.EntityRequestV1
import dev.dimension.flare.feature.plugin.wire.LoginBeginRequestV1
import dev.dimension.flare.feature.plugin.wire.LoginResumeRequestV1
import dev.dimension.flare.feature.plugin.wire.LoginTransitionV1
import dev.dimension.flare.feature.plugin.wire.MutationRequestV1
import dev.dimension.flare.feature.plugin.wire.MutationResultV1
import dev.dimension.flare.feature.plugin.wire.PageDirectionV1
import dev.dimension.flare.feature.plugin.wire.PageRequestV1
import dev.dimension.flare.feature.plugin.wire.PageV1
import dev.dimension.flare.feature.plugin.wire.PluginErrorCodeV1
import dev.dimension.flare.feature.plugin.wire.PostV1
import dev.dimension.flare.feature.plugin.wire.SearchRequestV1
import dev.dimension.flare.feature.plugin.wire.SemanticActionV1
import dev.dimension.flare.feature.plugin.wire.TimelinePageRequestV1
import dev.dimension.flare.feature.plugin.wire.VisibilityV1
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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

                stage = "timeline"
                val credential = MemoryCredential(success.credential)
                val accountKey = PluginRuntimeKeyV1.account(PLUGIN_ID, plugin.installed.packageHash, "42")
                val accountContext = accountContext(plugin, credential)
                val timeline =
                    pool.invoke(
                        plugin = plugin,
                        key = accountKey,
                        context = accountContext,
                        method = "capabilities.timeline.page",
                        request = TimelinePageRequestV1("home", pageRequest(), emptyMap()),
                        requestSerializer = TimelinePageRequestV1.serializer(),
                        responseSerializer = PageV1.serializer(PostV1.serializer()),
                    )
                assertEquals(
                    "100",
                    timeline.items
                        .single()
                        .key.id,
                )
                assertEquals("$ORIGIN/api/v1/timelines/home?max_id=100", timeline.olderCursor)
                assertTrue(!timeline.endReached)

                stage = "search"
                val search =
                    pool.invoke(
                        plugin = plugin,
                        key = accountKey,
                        context = accountContext,
                        method = "capabilities.search.posts",
                        request = SearchRequestV1("photo", pageRequest()),
                        requestSerializer = SearchRequestV1.serializer(),
                        responseSerializer = PageV1.serializer(PostV1.serializer()),
                    )
                assertEquals(
                    "100",
                    search.items
                        .single()
                        .key.id,
                )

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
                pool.close()
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

    override suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1 {
        requests += request
        val path = URI(request.url).path
        return when {
            path == "/api/v1/instance" -> {
                response(INSTANCE)
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

            path == "/api/v2/search" -> {
                response("""{"statuses":[$STATUS],"accounts":[$ACCOUNT]}""")
            }

            path == "/api/v1/statuses/100/favourite" -> {
                response(STATUS.replace("\"favourited\":false", "\"favourited\":true"))
            }

            path == "/api/v1/statuses/missing" -> {
                response("""{"error":"missing"}""", status = 404)
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

private fun detectorContext(plugin: RunningPluginV1): PluginInvocationContextV1 =
    PluginInvocationContextV1.detector(PLUGIN_ID, "Pixelfed", plugin.installed.packageHash, ORIGIN, "en")

private fun loginContext(plugin: RunningPluginV1): PluginInvocationContextV1 =
    PluginInvocationContextV1.login(PLUGIN_ID, "Pixelfed", plugin.installed.packageHash, ORIGIN, emptySet(), "en")

private fun accountContext(
    plugin: RunningPluginV1,
    credential: PluginCredentialAccess,
    assets: Map<String, PluginAsset> = emptyMap(),
): PluginInvocationContextV1 =
    PluginInvocationContextV1.account(PLUGIN_ID, "Pixelfed", plugin.installed.packageHash, ORIGIN, "42", "en", credential, assets)

private fun pageRequest(): PageRequestV1 = PageRequestV1(PageDirectionV1.Refresh, 1)

private const val PLUGIN_ID = "dev.dimension.flare.sample.pixelfed"
private const val ORIGIN = "https://pixelfed.social"
private const val REDIRECT_URI = "flare://Callback/SignIn/Plugin/flow"
private const val INSTANCE =
    """{"uri":"pixelfed.social","title":"Pixelfed Social","version":"3.5.3 (compatible; Pixelfed 0.12.7)","description":"Photos","stats":{"user_count":10},"registrations":true,"configuration":{"statuses":{"max_characters":500,"max_media_attachments":4},"media_attachments":{"image_size_limit":10485760,"video_size_limit":20971520,"description_limit":1500,"supported_mime_types":["image/jpeg","image/png","video/mp4"]}}}"""
private const val ACCOUNT =
    """{"id":"42","username":"alice","acct":"alice","display_name":"Alice","note":"<p>Photos</p>","avatar":"https://pixelfed.social/avatar.jpg","header":"https://pixelfed.social/header.jpg","url":"https://pixelfed.social/alice","followers_count":3,"following_count":4,"statuses_count":5,"locked":false,"bot":false,"fields":[]}"""
private const val STATUS =
    """{"id":"100","account":$ACCOUNT,"created_at":"2026-08-14T00:00:00Z","content":"<p>A photo</p>","url":"https://pixelfed.social/p/example","media_attachments":[{"id":"media","type":"image","url":"https://pixelfed.social/media.jpg","preview_url":"https://pixelfed.social/preview.jpg","meta":{"original":{"width":800,"height":600}}}],"sensitive":false,"visibility":"public","favourites_count":1,"reblogs_count":2,"replies_count":0,"favourited":false,"reblogged":false,"bookmarked":false}"""
