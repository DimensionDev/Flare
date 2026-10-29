package dev.dimension.flare.feature.plugin.sdk

import dev.dimension.flare.feature.plugin.github.GitHubPluginRepositoryV1
import dev.dimension.flare.feature.plugin.host.PluginCredentialAccess
import dev.dimension.flare.feature.plugin.host.PluginHttpTransport
import dev.dimension.flare.feature.plugin.host.PluginInvocationContextV1
import dev.dimension.flare.feature.plugin.host.PluginTransportRequestV1
import dev.dimension.flare.feature.plugin.host.PluginTransportResponseV1
import dev.dimension.flare.feature.plugin.installer.PluginInstaller
import dev.dimension.flare.feature.plugin.installer.TestFppFactory
import dev.dimension.flare.feature.plugin.lifecycle.PluginInstallSourceV1
import dev.dimension.flare.feature.plugin.lifecycle.PluginStateStore
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.login.PluginFormLoginCoordinatorV1
import dev.dimension.flare.feature.plugin.runtime.PluginCallException
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimeKeyV1
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimePool
import dev.dimension.flare.feature.plugin.wire.PageDirectionV1
import dev.dimension.flare.feature.plugin.wire.PageRequestV1
import dev.dimension.flare.feature.plugin.wire.PageV1
import dev.dimension.flare.feature.plugin.wire.PluginErrorCodeV1
import dev.dimension.flare.feature.plugin.wire.PostDetailTypeV1
import dev.dimension.flare.feature.plugin.wire.PostV1
import dev.dimension.flare.feature.plugin.wire.TimelinePageRequestV1
import dev.dimension.flare.feature.plugin.wire.requireValid
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubTemplateTest {
    private lateinit var root: Path
    private lateinit var plugin: RunningPluginV1
    private lateinit var pool: PluginRuntimePool
    private val requests = mutableListOf<PluginTransportRequestV1>()
    private var respond: (PluginTransportRequestV1) -> PluginTransportResponseV1 = { request ->
        response(if (request.url.endsWith("/api/me")) PROFILE else POSTS)
    }

    @BeforeTest
    fun setUp() =
        runBlocking {
            root = Files.createTempDirectory("github-plugin-template").toOkioPath()
            val template =
                sequenceOf(
                    Paths.get("sdk/skill/flare-plugin/assets/github-template"),
                    Paths.get("feature/plugin/sdk/skill/flare-plugin/assets/github-template"),
                ).first { Files.isRegularFile(it.resolve("manifest.json")) }
            val entries =
                Files.walk(template).use { paths ->
                    paths
                        .filter { Files.isRegularFile(it) }
                        .map { file ->
                            "repo-commit/${template.relativize(file).toString().replace('\\', '/')}" to Files.readAllBytes(file)
                        }.toList()
                }
            val archive = root / "source.zip"
            TestFppFactory.write(archive, entries)
            val source = PluginInstallSourceV1.GitHub(GitHubPluginRepositoryV1(1, "example", "feed"), "v0.1.0", "a".repeat(40))
            val store = PluginStateStore.open(FileSystem.SYSTEM, root / "store")
            val installer = PluginInstaller(FileSystem.SYSTEM, store)
            installer.commit(installer.inspect(archive, source), confirmed = true)

            plugin =
                PluginStateStore
                    .open(FileSystem.SYSTEM, root / "store")
                    .running.plugins
                    .getValue(PLUGIN_ID)
            assertEquals(source, plugin.installed.source)
            pool =
                PluginRuntimePool(
                    FileSystem.SYSTEM,
                    object : PluginHttpTransport {
                        override suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1 {
                            requests += request
                            return respond(request)
                        }
                    },
                )
        }

    @AfterTest
    fun tearDown() =
        runBlocking {
            if (::pool.isInitialized) pool.close()
            if (::root.isInitialized) FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
        }

    @Test
    fun githubTemplateVerifiesIdentityAndMapsLinkPostsWithOpaquePaging() =
        runBlocking {
            val credential = login()
            assertEquals("Bearer secret", requests.single().headers["Authorization"])
            val first = page(credential, PageDirectionV1.Refresh, "ignored-old-cursor")
            assertEquals("$ORIGIN/api/posts?limit=2", requests.last().url)
            assertEquals("cursor +/2", first.olderCursor)
            assertFalse(first.endReached)
            assertEquals(
                "post-1",
                first.items
                    .single()
                    .key.id,
            )
            assertEquals(
                "feed.example",
                assertNotNull(first.items.single().author).key.host,
            )
            assertEquals(PostDetailTypeV1.Link, first.items.single().detailType)
            assertEquals("https://example.org/posts/post-1", first.items.single().url)

            respond = { response("""{"items":[],"next_cursor":"page-3"}""") }
            val empty = page(credential, PageDirectionV1.Older, first.olderCursor)
            assertTrue(requests.last().url.endsWith("&cursor=cursor%20%2B%2F2"))
            assertTrue(empty.items.isEmpty())
            assertFalse(empty.endReached)
            assertEquals("page-3", empty.olderCursor)

            respond = { response("""{"items":[],"next_cursor":null}""") }
            val end = page(credential, PageDirectionV1.Older, empty.olderCursor)
            assertTrue(end.endReached)
            assertNull(end.olderCursor)
        }

    @Test
    fun typedRemoteErrorsRemainRecoverableAndPreserveRetryDelay() =
        runBlocking {
            val credential = login()
            listOf(
                401 to PluginErrorCodeV1.AuthenticationRequired,
                404 to PluginErrorCodeV1.NotFound,
                429 to PluginErrorCodeV1.RateLimited,
                503 to PluginErrorCodeV1.Remote,
            ).forEach { (status, code) ->
                respond = { PluginTransportResponseV1(status, mapOf("Retry-After" to listOf("17")), byteArrayOf()) }
                val failure = assertFailsWith<PluginCallException> { page(credential) }
                assertEquals(code, failure.error.code)
                if (status == 429) assertEquals(17, failure.error.retryAfterSeconds)
            }
            respond = { response(POSTS) }
            assertEquals(1, page(credential).items.size)
        }

    @Test
    fun malformedWebsiteResponsesBecomeTypedErrorsInsteadOfEmptySuccess() =
        runBlocking {
            val credential = login()
            listOf("not-json", "[]", "{}", """{"items":[],"next_cursor":42}""", """{"items":[null]}""").forEach { body ->
                respond = { response(body) }
                assertEquals(
                    PluginErrorCodeV1.InvalidResponse,
                    assertFailsWith<PluginCallException> { page(credential) }.error.code,
                )
            }
            respond = { response(POSTS) }
            assertEquals(1, page(credential).items.size)
        }

    @Test
    fun malformedStoredCredentialsRequestReloginBeforeMakingHttpCalls() =
        runBlocking {
            listOf(JsonNull, JsonPrimitive("old-token-format"), JsonPrimitive(42)).forEach { payload ->
                val credential =
                    object : PluginCredentialAccess {
                        override suspend fun read(): JsonElement = payload

                        override suspend fun replace(value: JsonElement) = error("Unexpected credential replacement")
                    }
                assertEquals(
                    PluginErrorCodeV1.AuthenticationRequired,
                    assertFailsWith<PluginCallException> { page(credential) }.error.code,
                )
            }
            assertTrue(requests.isEmpty())
        }

    private suspend fun login(): PluginCredentialAccess {
        val success =
            PluginFormLoginCoordinatorV1(pool).login(plugin, "token", ORIGIN, "en", mapOf("token" to " secret "))
        assertEquals("alice", success.accountId)
        assertEquals("feed.example", success.profile.key.host)
        assertEquals(mapOf("flare.datasource.timeline/v1" to setOf("page")), success.capabilities)
        return object : PluginCredentialAccess {
            override suspend fun read(): JsonElement = success.credential

            override suspend fun replace(value: JsonElement) = error("Read-only template must not replace credentials")
        }
    }

    private suspend fun page(
        credential: PluginCredentialAccess,
        direction: PageDirectionV1 = PageDirectionV1.Refresh,
        cursor: String? = null,
    ): PageV1<PostV1> =
        pool.invoke(
            plugin = plugin,
            key = PluginRuntimeKeyV1.account(PLUGIN_ID, plugin.installed.packageHash, ORIGIN, "alice"),
            context =
                PluginInvocationContextV1.account(
                    PLUGIN_ID,
                    "ExampleFeed",
                    plugin.installed.packageHash,
                    ORIGIN,
                    "alice",
                    "en",
                    credential,
                ),
            method = "capabilities.timeline.page",
            request = TimelinePageRequestV1("home", PageRequestV1(direction, 2, cursor)),
            requestSerializer = TimelinePageRequestV1.serializer(),
            responseSerializer = PageV1.serializer(PostV1.serializer()),
            validate = { it.requireValid { post -> post.requireValid() } },
        )
}

private fun response(body: String): PluginTransportResponseV1 = PluginTransportResponseV1(200, emptyMap(), body.encodeToByteArray())

private const val PLUGIN_ID = "dev.example.flare.feed"
private const val ORIGIN = "https://feed.example"
private const val PROFILE = """{"id":"alice","username":"alice","name":"Alice"}"""
private const val POSTS =
    """{"items":[{"id":"post-1","author":$PROFILE,"created_at":"2026-01-01T00:00:00Z","text":"Hello","url":"https://example.org/posts/post-1"}],"next_cursor":"cursor +/2"}"""
