package dev.dimension.flare.feature.plugin.runtime

import dev.dimension.flare.feature.plugin.host.PluginCredentialAccess
import dev.dimension.flare.feature.plugin.host.PluginHttpTransport
import dev.dimension.flare.feature.plugin.host.PluginInvocationContextV1
import dev.dimension.flare.feature.plugin.host.PluginTransportRequestV1
import dev.dimension.flare.feature.plugin.host.PluginTransportResponseV1
import dev.dimension.flare.feature.plugin.installer.PluginInstaller
import dev.dimension.flare.feature.plugin.installer.TestFppFactory
import dev.dimension.flare.feature.plugin.lifecycle.PluginStateStore
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
            val context = accountContext(plugin, "first", "https://one.example", MemoryCredential())
            try {
                assertEquals(1, invokePage(pool, plugin, context, request("normal"))["count"]!!.jsonPrimitive.int)
                assertFailsWith<PluginCallException> { invokePage(pool, plugin, context, request("pluginError")) }
                assertEquals(3, invokePage(pool, plugin, context, request("normal"))["count"]!!.jsonPrimitive.int)
                assertFailsWith<PluginRuntimeUnavailableException> {
                    invokePage(pool, plugin, context, request("throw"))
                }
                assertEquals(1, invokePage(pool, plugin, context, request("normal"))["count"]!!.jsonPrimitive.int)

                assertFailsWith<PluginRuntimeUnavailableException> {
                    invokePage(pool, plugin, context, request("normal")) { error("invalid Wire output") }
                }
                assertEquals(1, invokePage(pool, plugin, context, request("normal"))["count"]!!.jsonPrimitive.int)

                assertFailsWith<kotlinx.coroutines.TimeoutCancellationException> {
                    withTimeout(100) { invokePage(pool, plugin, context, request("loop")) }
                }
                pool.retry(PLUGIN_ID)
                assertEquals(1, invokePage(pool, plugin, context, request("normal"))["count"]!!.jsonPrimitive.int)
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
                assertEquals("new", result["credential"]!!.jsonObject["token"]!!.jsonPrimitive.content)
                assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result["hash"]!!.jsonPrimitive.content)
                assertEquals(32, result["random"]!!.jsonPrimitive.content.length)
                assertTrue(result["uuid"]!!.jsonPrimitive.content.matches(Regex("[0-9a-f-]{36}")))
                assertEquals(
                    "new",
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
            val pool = PluginRuntimePool(fileSystem, CapturingTransport())
            val first = accountContext(plugin, "first", "https://one.example", MemoryCredential())
            val second = accountContext(plugin, "second", "https://two.example", MemoryCredential())
            try {
                assertEquals(1, invokePage(pool, plugin, first, request("normal"))["count"]!!.jsonPrimitive.int)
                assertEquals(1, invokePage(pool, plugin, second, request("normal"))["count"]!!.jsonPrimitive.int)
                pool.closeIdle()
                assertEquals(1, invokePage(pool, plugin, first, request("normal"))["count"]!!.jsonPrimitive.int)
                assertEquals(1, invokePage(pool, plugin, second, request("normal"))["count"]!!.jsonPrimitive.int)
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

    private suspend fun install(script: String): RunningPluginV1 {
        TestFppFactory.write(input, TestFppFactory.validEntries(script = script))
        val namespace = root / "social-plugins-v2"
        val store = PluginStateStore.open(fileSystem, namespace)
        val installer = PluginInstaller(fileSystem, store)
        installer.inspect(input).also { installer.commit(it, confirmed = true) }
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
    ): PluginInvocationContextV1 =
        PluginInvocationContextV1.account(
            pluginId = PLUGIN_ID,
            platformId = PLATFORM_ID,
            packageHash = plugin.installed.packageHash,
            origin = origin,
            accountId = accountId,
            locale = "en",
            credential = credential,
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
    override suspend fun read(): JsonElement = value

    override suspend fun replace(value: JsonElement) {
        this.value = value
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
    definePlugin({
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
        throw flare.error({ code: "NotFound", message: { value: "missing" } });
      }
      if (request.mode === "throw") throw new Error("synthetic");
      if (request.mode === "waitThrow") {
        await flare.http.request({ url: "https://one.example/wait" });
        throw new Error("synthetic");
      }
      if (request.mode === "loop") while (true) {}
      if (request.mode === "host") {
        await flare.credential.replace({ token: "new" });
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
