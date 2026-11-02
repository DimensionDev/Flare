package dev.dimension.flare.feature.plugin.runtime

import dev.dimension.flare.feature.plugin.host.PluginCallTimeoutV1
import dev.dimension.flare.feature.plugin.host.PluginCredentialAccess
import dev.dimension.flare.feature.plugin.host.PluginHostGateway
import dev.dimension.flare.feature.plugin.host.PluginHttpTransport
import dev.dimension.flare.feature.plugin.host.PluginInvocationContextV1
import dev.dimension.flare.feature.plugin.host.PluginTransportRequestV1
import dev.dimension.flare.feature.plugin.host.PluginTransportResponseV1
import dev.dimension.flare.feature.plugin.lifecycle.InstalledPluginV1
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.manifest.CapabilityManifestV1
import dev.dimension.flare.feature.plugin.manifest.CapabilityOperationManifestV1
import dev.dimension.flare.feature.plugin.manifest.PluginManifestV1
import dev.dimension.flare.feature.plugin.manifest.PluginPlatformManifestV1
import dev.dimension.flare.feature.plugin.manifest.PluginTextV1
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.SYSTEM
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformPluginRuntimeTest {
    @Test
    fun invocationWaitsForCredentialPersistenceEvenWithoutAwait() =
        runBlocking<Unit> {
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val saved = CompletableDeferred<JsonElement>()
            val credential =
                object : PluginCredentialAccess {
                    override suspend fun read(): JsonElement = JsonNull

                    override suspend fun replace(value: JsonElement) {
                        started.complete(Unit)
                        release.await()
                        saved.complete(value)
                    }
                }
            val runtime = runtime("flare.credential.replace({ token: 'new' }); return 'done';")
            try {
                withTimeout(15_000) {
                    val result = async { invoke(runtime, credential) }
                    started.await()
                    delay(30)
                    assertFalse(result.isCompleted)
                    release.complete(Unit)
                    assertEquals("done", result.await().jsonPrimitive.content)
                    assertEquals(
                        "new",
                        saved
                            .await()
                            .jsonObject
                            .getValue("token")
                            .jsonPrimitive.content,
                    )
                }
            } finally {
                release.complete(Unit)
                runtime.close()
            }
        }

    @Test
    fun cancellationCancelsHostWorkAndRetiresTheGeneration() =
        runBlocking<Unit> {
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val credential =
                object : PluginCredentialAccess {
                    override suspend fun read(): JsonElement {
                        started.complete(Unit)
                        try {
                            CompletableDeferred<Unit>().await()
                        } finally {
                            cancelled.complete(Unit)
                        }
                        error("unreachable")
                    }

                    override suspend fun replace(value: JsonElement) = error("Cancelled call must not write")
                }
            val runtime = runtime("await flare.credential.read(); await flare.credential.replace({ token: 'late' }); return 'wrong';")
            try {
                withTimeout(15_000) {
                    val result = async { runCatching { invoke(runtime, credential) } }
                    started.await()
                    result.cancelAndJoin()
                    cancelled.await()
                    val failure = assertFailsWith<PluginRuntimeFatalException> { invoke(runtime, credential) }
                    assertFalse(failure.countsTowardPause)
                }
            } finally {
                runtime.close()
            }
        }

    @Test
    fun detachedErrorsSucceedButReturnedErrorsRetainTypedAndFatalHandling() =
        runBlocking<Unit> {
            val detached = runtime("Promise.reject(new Error('detached')); return 'ok';")
            val typed = runtime("throw flare.error({ code: 'NotFound', message: { value: 'Missing' } });")
            val fatal = runtime("return Promise.reject(null);")
            try {
                assertEquals("ok", invoke(detached).jsonPrimitive.content)
                assertEquals("Missing", assertFailsWith<PluginCallException> { invoke(typed) }.message)
                // Recoverable plugin errors do not retire the runtime.
                assertFailsWith<PluginCallException> { invoke(typed) }
                assertTrue(assertFailsWith<PluginRuntimeFatalException> { invoke(fatal) }.countsTowardPause)
            } finally {
                detached.close()
                typed.close()
                fatal.close()
            }
        }

    @Test
    fun hostPermissionsAndPerInvocationCredentialsRemainEnforced() =
        runBlocking<Unit> {
            val runtime =
                runtime(
                    """
                    let denied = false;
                    try { await flare.http.request({ url: 'https://other.example/private' }); }
                    catch (_) { denied = true; }
                    return { denied, account: context.accountId, credential: await flare.credential.read() };
                    """.trimIndent(),
                )
            try {
                for (account in listOf("first", "second")) {
                    val result = invoke(runtime, MemoryCredential(JsonPrimitive(account)), account).jsonObject
                    assertEquals("true", result.getValue("denied").jsonPrimitive.content)
                    assertEquals(account, result.getValue("account").jsonPrimitive.content)
                    assertEquals(account, result.getValue("credential").jsonPrimitive.content)
                }
            } finally {
                runtime.close()
            }
        }

    @Test
    fun engineUnavailabilityDoesNotCountTowardPluginPause() =
        runBlocking<Unit> {
            val runtime = runtime("return 'ok';", engineFactory = { throw PluginJavaScriptUnavailableException("Provider stopped") })
            try {
                assertFalse(assertFailsWith<PluginRuntimeFatalException> { invoke(runtime) }.countsTowardPause)
            } finally {
                runtime.close()
            }
        }

    private fun runtime(
        body: String,
        engineFactory: suspend (PluginHostCall) -> PluginJavaScriptEngine = { createTestPluginJavaScriptEngine(it) },
    ): IsolatedPluginRuntime {
        val plugin =
            RunningPluginV1(
                installed =
                    InstalledPluginV1(
                        pluginId = ID,
                        version = "1.0.0",
                        packageHash = HASH,
                        packageSize = 0,
                        iconSize = 0,
                        manifest =
                            PluginManifestV1(
                                apiVersion = 1,
                                id = ID,
                                version = "1.0.0",
                                name = PluginTextV1.Literal("Engine test"),
                                platform =
                                    PluginPlatformManifestV1(
                                        id = "engine",
                                        name = PluginTextV1.Literal("Engine test"),
                                        capabilities =
                                            mapOf(
                                                "flare.datasource.timeline/v1" to
                                                    CapabilityManifestV1(mapOf("page" to CapabilityOperationManifestV1())),
                                            ),
                                    ),
                            ),
                    ),
                packagePath = "unused.fpp",
                iconPath = "unused.png",
                developmentSource = "defineFlareSocialPlugin({ capabilities: { timeline: { async page(request, context) { $body } } } });",
            )
        return IsolatedPluginRuntime(
            plugin,
            PluginPackageScriptLoader(FileSystem.SYSTEM),
            PluginHostGateway(
                object : PluginHttpTransport {
                    override suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1 =
                        error("Unapproved request reached transport")
                },
            ),
            engineFactory = engineFactory,
        )
    }

    private suspend fun invoke(
        runtime: IsolatedPluginRuntime,
        credential: PluginCredentialAccess = MemoryCredential(JsonNull),
        account: String = "first",
    ): JsonElement =
        runtime.invoke(
            "capabilities.timeline.page",
            JsonObject(emptyMap()),
            PluginInvocationContextV1.account(ID, "engine", HASH, "https://one.example", account, "en", credential),
            PluginCallTimeoutV1.Normal,
        ) {}

    private class MemoryCredential(
        private var value: JsonElement,
    ) : PluginCredentialAccess {
        override suspend fun read(): JsonElement = value

        override suspend fun replace(value: JsonElement) {
            this.value = value
        }
    }

    private companion object {
        const val ID = "com.example.engine"
        val HASH = "a".repeat(64)
    }
}
