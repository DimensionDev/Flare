package dev.dimension.flare.feature.plugin.login

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.repository.AccountMicroblogDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.feature.plugin.adapter.PluginLoginMethodHandlerV1
import dev.dimension.flare.feature.plugin.host.PluginHttpTransport
import dev.dimension.flare.feature.plugin.host.PluginTransportRequestV1
import dev.dimension.flare.feature.plugin.host.PluginTransportResponseV1
import dev.dimension.flare.feature.plugin.installer.PluginInstaller
import dev.dimension.flare.feature.plugin.installer.TestFppFactory
import dev.dimension.flare.feature.plugin.lifecycle.PluginStateStore
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimePool
import dev.dimension.flare.feature.plugin.wire.CookieSnapshotV1
import dev.dimension.flare.feature.plugin.wire.CookieValueV1
import dev.dimension.flare.feature.plugin.wire.PluginAccountCredentialV1
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.login.LoginContext
import dev.dimension.flare.ui.presenter.login.LoginMethodType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import okio.SYSTEM
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginLoginCoordinatorV1Test {
    private val fileSystem = FileSystem.SYSTEM
    private lateinit var root: Path
    private lateinit var input: Path
    private lateinit var plugin: RunningPluginV1
    private lateinit var pool: PluginRuntimePool
    private lateinit var pendingStore: MemoryPendingStore
    private var now: Long = 1_000

    @BeforeTest
    fun setUp() =
        runBlocking {
            root = Files.createTempDirectory("plugin-login-test").toOkioPath()
            input = root / "input.fpp"
            plugin = install(LOGIN_SCRIPT)
            pool = PluginRuntimePool(fileSystem, NoopTransport())
            pendingStore = MemoryPendingStore()
        }

    @AfterTest
    fun tearDown() =
        runBlocking {
            pool.close()
            fileSystem.deleteRecursively(root, mustExist = false)
        }

    @Test
    fun oauthResumesAfterCoordinatorRecreationAndCannotReplay() =
        runBlocking {
            val first = oauthCoordinator()
            val start =
                assertIs<PluginOAuthStartV1.ExternalBrowser>(
                    first.begin(plugin, "oauth", ORIGIN, "en"),
                )
            assertTrue(start.url.startsWith("$ORIGIN/oauth?state="))

            val recreated = oauthCoordinator()
            val callback = "${pluginOAuthRedirectUri(start.flowId)}?code=ok&state=$STATE"
            val success = recreated.resume(callback, "en")
            assertEquals(ACCOUNT_ID, success.accountId)
            assertEquals(
                "oauth",
                success.credential.jsonObject["token"]
                    ?.jsonPrimitive
                    ?.content,
            )
            assertNull(pendingStore.load(start.flowId))
            assertEquals("oauth.missing", assertFailsWith<PluginLoginException> { recreated.resume(callback, "en") }.code)
        }

    @Test
    fun oauthCallbackStoresAccountAfterProcessRecreationAndIsIdempotent() =
        runBlocking {
            val start =
                assertIs<PluginOAuthStartV1.ExternalBrowser>(
                    oauthCoordinator().begin(plugin, "oauth", ORIGIN, "en"),
                )
            val accounts = RecordingAccountService()
            val callbacks = PluginOAuthCallbackCoordinatorV1(oauthCoordinator(), accounts)
            val callback = "${pluginOAuthRedirectUri(start.flowId)}?code=ok&state=$STATE"

            assertTrue(callbacks.handle(callback, "en"))
            assertTrue(callbacks.handle(callback, "en"))
            assertEquals(1, accounts.added.size)
            assertEquals(
                MicroBlogKey(ACCOUNT_ID, "plugin.example"),
                accounts.added
                    .single()
                    .first.accountKey,
            )
            assertEquals(
                "oauth",
                (accounts.added.single().second as PluginAccountCredentialV1)
                    .credential
                    .jsonObject["token"]
                    ?.jsonPrimitive
                    ?.content,
            )
            val iconUrl =
                requireNotNull(
                    accounts.added
                        .single()
                        .first
                        .platformIconUrl,
                )
            assertTrue(iconUrl.contains("/platform-"))
            assertFalse(iconUrl.contains(plugin.installed.packageHash))
            assertTrue(FileSystem.SYSTEM.exists(iconUrl.removePrefix("file://").toPath()))
        }

    @Test
    fun liveOAuthFlowUsesItsLoginUiCompletionInsteadOfFallbackWriter() =
        runBlocking {
            val start =
                assertIs<PluginOAuthStartV1.ExternalBrowser>(
                    oauthCoordinator().begin(plugin, "oauth", ORIGIN, "en"),
                )
            val accounts = RecordingAccountService()
            val callbacks = PluginOAuthCallbackCoordinatorV1(oauthCoordinator(), accounts)
            var completedAccountId: String? = null
            callbacks.register(start.flowId) { completedAccountId = it.accountId }

            assertTrue(
                callbacks.handle(
                    "${pluginOAuthRedirectUri(start.flowId)}?code=ok&state=$STATE",
                    "en",
                ),
            )
            assertEquals(ACCOUNT_ID, completedAccountId)
            assertTrue(accounts.added.isEmpty())
        }

    @Test
    fun failedColdStartCallbackIsReportedOnlyWithoutALiveLoginUi() =
        runBlocking {
            val start =
                assertIs<PluginOAuthStartV1.ExternalBrowser>(
                    oauthCoordinator().begin(plugin, "oauth", ORIGIN, "en"),
                )
            val failures = mutableListOf<Throwable>()
            val callbacks =
                PluginOAuthCallbackCoordinatorV1(
                    oauth = oauthCoordinator(),
                    accountService = RecordingAccountService(),
                    onUnattendedFailure = failures::add,
                )
            val invalidCallback =
                "${pluginOAuthRedirectUri(start.flowId)}?code=ok&state=${"0".repeat(64)}"

            assertFailsWith<PluginLoginException> { callbacks.handle(invalidCallback, "en") }
            assertEquals(1, failures.size)

            failures.clear()
            callbacks.register(start.flowId) {}
            assertFailsWith<PluginLoginException> { callbacks.handle(invalidCallback, "en") }
            assertTrue(failures.isEmpty())
        }

    @Test
    fun loginHandlerAcceptsOnlyTheOAuthFlowItStarted() =
        runBlocking {
            val accounts = RecordingAccountService()
            val oauth = oauthCoordinator()
            val callbacks = PluginOAuthCallbackCoordinatorV1(oauth, accounts)
            var completed = false
            val handler =
                PluginLoginMethodHandlerV1(
                    plugin = plugin,
                    method =
                        plugin.installed.manifest.platform.loginMethods
                            .single { it.id == "oauth" },
                    context =
                        LoginContext(
                            host = "plugin.example",
                            methodType = LoginMethodType.OAuth,
                            onSuccess = { completed = true },
                        ),
                    oauth = oauth,
                    oauthCallbacks = callbacks,
                    form = PluginFormLoginCoordinatorV1(pool, FixedEntropy),
                    webCookie = PluginWebCookieLoginCoordinatorV1(pool, FixedEntropy),
                    accountService = accounts,
                    coroutineScope = this,
                )
            try {
                handler.perform("login")
                val callback = "${pluginOAuthRedirectUri(FLOW_ID)}?code=ok&state=$STATE"
                assertTrue(handler.canResume(callback))
                assertFalse(
                    handler.canResume(
                        "${pluginOAuthRedirectUri(OTHER_FLOW_ID)}?code=ok&state=$STATE",
                    ),
                )

                handler.resume(callback)

                assertTrue(completed)
                assertEquals(1, accounts.added.size)
            } finally {
                handler.close()
            }
        }

    @Test
    fun oauthStateMismatchDoesNotConsumePending() =
        runBlocking {
            val coordinator = oauthCoordinator()
            val start = assertIs<PluginOAuthStartV1.ExternalBrowser>(coordinator.begin(plugin, "oauth", ORIGIN, "en"))
            val redirect = pluginOAuthRedirectUri(start.flowId)

            assertEquals(
                "oauth.state",
                assertFailsWith<PluginLoginException> {
                    coordinator.resume("$redirect?code=ok&state=${"0".repeat(64)}", "en")
                }.code,
            )
            assertTrue(pendingStore.load(start.flowId) != null)
            assertEquals(ACCOUNT_ID, coordinator.resume("$redirect?code=ok&state=$STATE", "en").accountId)
        }

    @Test
    fun expiredOrChangedOAuthFlowIsConsumedBeforeRuntimeUse() =
        runBlocking {
            val coordinator = oauthCoordinator()
            val expired = assertIs<PluginOAuthStartV1.ExternalBrowser>(coordinator.begin(plugin, "oauth", ORIGIN, "en"))
            now += 15 * 60 * 1_000L + 1
            assertEquals(
                "oauth.expired",
                assertFailsWith<PluginLoginException> {
                    coordinator.resume("${pluginOAuthRedirectUri(expired.flowId)}?state=$STATE", "en")
                }.code,
            )
            assertNull(pendingStore.load(expired.flowId))

            now = 1_000
            val changed = assertIs<PluginOAuthStartV1.ExternalBrowser>(coordinator.begin(plugin, "oauth", ORIGIN, "en"))
            val missingPluginCoordinator = oauthCoordinator(runningPlugin = { null })
            assertEquals(
                "oauth.package",
                assertFailsWith<PluginLoginException> {
                    missingPluginCoordinator.resume("${pluginOAuthRedirectUri(changed.flowId)}?state=$STATE", "en")
                }.code,
            )
            assertNull(pendingStore.load(changed.flowId))
        }

    @Test
    fun reloginAccountAndFormFieldsAreHostValidated() =
        runBlocking {
            val oauth = oauthCoordinator()
            val start =
                assertIs<PluginOAuthStartV1.ExternalBrowser>(
                    oauth.begin(plugin, "oauth", ORIGIN, "en", expectedAccountId = "different"),
                )
            assertFailsWith<IllegalArgumentException> {
                oauth.resume("${pluginOAuthRedirectUri(start.flowId)}?state=$STATE", "en")
            }

            val forms = PluginFormLoginCoordinatorV1(pool, FixedEntropy)
            assertFailsWith<IllegalArgumentException> {
                forms.login(plugin, "form", ORIGIN, "en", emptyMap())
            }
            val success = forms.login(plugin, "form", ORIGIN, "en", mapOf("token" to "secret"))
            assertEquals(
                "secret",
                success.credential.jsonObject["token"]
                    ?.jsonPrimitive
                    ?.content,
            )
        }

    @Test
    fun cookieFlowWaitsForRequiredValuesAndExposesOnlyDeclaredCookies() =
        runBlocking {
            val session = PluginWebCookieLoginCoordinatorV1(pool, FixedEntropy).begin(plugin, "cookie", ORIGIN, "en")
            assertEquals("$ORIGIN/login", session.request.startUrl)
            assertEquals(
                listOf("session", "optional"),
                session.request.probes
                    .single()
                    .cookies
                    .map { it.name },
            )

            val waiting = assertIs<PluginCookieCheckResultV1.AwaitingCookies>(session.check(CookieSnapshotV1(emptyList())))
            assertEquals(listOf("session"), waiting.missing.map { it.name })

            val success =
                assertIs<PluginCookieCheckResultV1.Success>(
                    session.check(
                        CookieSnapshotV1(
                            listOf(
                                CookieValueV1("$ORIGIN/api/cookie", "session", "valid"),
                                CookieValueV1("$ORIGIN/api/cookie", "optional", "yes"),
                                CookieValueV1("$ORIGIN/api/cookie", "undeclared", "must-not-leak"),
                            ),
                        ),
                    ),
                )
            val cookies =
                success.value.credential.jsonObject["cookies"]!!
                    .jsonArray
            assertEquals(listOf("optional", "session"), cookies.map { it.jsonObject["name"]!!.jsonPrimitive.content }.sorted())
            assertFailsWith<IllegalStateException> { session.check(CookieSnapshotV1(emptyList())) }
            Unit
        }

    @Test
    fun overlappingCookiePollIsSkippedInsteadOfQueued() =
        runBlocking {
            pool.close()
            val gate = GateTransport()
            pool = PluginRuntimePool(fileSystem, gate)
            val session = PluginWebCookieLoginCoordinatorV1(pool, FixedEntropy).begin(plugin, "cookie", ORIGIN, "en")
            val snapshot = CookieSnapshotV1(listOf(CookieValueV1("$ORIGIN/api/cookie", "session", "valid")))

            val first = async { session.check(snapshot) }
            gate.started.await()
            assertIs<PluginCookieCheckResultV1.Busy>(session.check(snapshot))
            gate.release.complete(Unit)
            assertIs<PluginCookieCheckResultV1.Success>(first.await())
            Unit
        }

    @Test
    fun callbackAndNavigationPoliciesRejectUnsafeUrls() {
        val callback = parsePluginOAuthCallback("${pluginOAuthRedirectUri(FLOW_ID)}?code=value&state=$STATE")
        assertEquals(FLOW_ID, callback.flowId)
        assertEquals("value", callback.parameters["code"])
        assertFailsWith<IllegalArgumentException> { parsePluginOAuthCallback("http://example.com") }
        assertTrue(PluginWebCookieNavigationPolicyV1.isAllowed("https://other.example/path"))
        assertEquals("https://other.example", PluginWebCookieNavigationPolicyV1.visibleOrigin("https://other.example/path"))
        assertTrue(!PluginWebCookieNavigationPolicyV1.isAllowed("http://other.example/path"))
        assertTrue(!PluginWebCookieNavigationPolicyV1.isAllowed("https://user:password@other.example/path"))
        assertTrue(!PluginWebCookieNavigationPolicyV1.isAllowed("file:///tmp/test"))
    }

    private fun oauthCoordinator(
        runningPlugin: (String) -> RunningPluginV1? = { id -> plugin.takeIf { it.installed.pluginId == id } },
    ): PluginOAuthLoginCoordinatorV1 =
        PluginOAuthLoginCoordinatorV1(
            runtimePool = pool,
            pendingStore = pendingStore,
            runningPlugin = runningPlugin,
            entropy = FixedEntropy,
            nowEpochMillis = { now },
        )

    private suspend fun install(script: String): RunningPluginV1 {
        TestFppFactory.write(input, TestFppFactory.validEntries(manifest = LOGIN_MANIFEST, script = script))
        val namespace = root / "social-plugins-v2"
        val store = PluginStateStore.open(fileSystem, namespace)
        val installer = PluginInstaller(fileSystem, store)
        installer.inspect(input).also { installer.commit(it, confirmed = true) }
        return PluginStateStore
            .open(fileSystem, namespace)
            .running.plugins
            .getValue(PLUGIN_ID)
    }
}

private class RecordingAccountService : AccountService {
    val added = mutableListOf<Pair<UiAccount, Any>>()

    override fun accountServiceFlow(accountType: AccountType): Flow<MicroblogDataSource> = emptyFlow()

    override fun allAccountServicesFlow(): Flow<List<AccountMicroblogDataSource>> = emptyFlow()

    override fun <T : Any> addAccount(
        account: UiAccount,
        credential: T,
        serializer: KSerializer<T>,
    ): Job {
        added += account to credential
        return Job().apply { complete() }
    }

    override fun <T : Any> credentialFlow(
        accountKey: MicroBlogKey,
        serializer: KSerializer<T>,
    ): Flow<T> = emptyFlow()

    override fun <T : Any> updateCredential(
        accountKey: MicroBlogKey,
        credential: T,
        serializer: KSerializer<T>,
    ): Job = Job().apply { complete() }
}

private class MemoryPendingStore : PluginOAuthPendingStoreV1 {
    private var pending: PluginOAuthPendingV1? = null

    override suspend fun save(pending: PluginOAuthPendingV1) {
        this.pending = pending
    }

    override suspend fun load(flowId: String): PluginOAuthPendingV1? = pending?.takeIf { it.flowId == flowId }

    override suspend fun consume(pending: PluginOAuthPendingV1): Boolean {
        if (this.pending != pending) return false
        this.pending = null
        return true
    }
}

private data object FixedEntropy : PluginLoginEntropyV1 {
    override fun newFlowId(): String = FLOW_ID

    override fun newState(): String = STATE
}

private open class NoopTransport : PluginHttpTransport {
    override suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1 =
        PluginTransportResponseV1(status = 200, headers = emptyMap(), body = "ok".encodeToByteArray())
}

private class GateTransport : NoopTransport() {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()

    override suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1 {
        started.complete(Unit)
        release.await()
        return super.execute(request)
    }
}

private const val LOGIN_MANIFEST =
    """
    {
      "schemaVersion": 1,
      "apiVersion": 1,
      "id": "dev.dimension.flare.test.plugin",
      "version": "1.0.0",
      "defaultLocale": "en",
      "name": "Login test",
      "platform": {
        "id": "TestPlugin",
        "name": "Test platform",
        "capabilities": {
          "flare.datasource.timeline/v1": {
            "operations": { "page": {} }
          }
        },
        "timelines": [
          { "id": "home", "title": "Home", "defaultForNewAccount": true }
        ],
        "loginMethods": [
          { "id": "oauth", "interaction": "OAuth", "title": "OAuth" },
          {
            "id": "form",
            "interaction": "Form",
            "title": "Form",
            "fields": [{ "id": "token", "type": "Secret", "label": "Token" }]
          },
          {
            "id": "cookie",
            "interaction": "WebCookie",
            "title": "Cookie",
            "cookie": {
              "startUrl": "${'$'}accountOrigin/login",
              "probes": [{
                "url": "${'$'}accountOrigin/api/cookie",
                "cookies": [
                  { "name": "session", "required": true },
                  { "name": "optional", "required": false }
                ]
              }]
            }
          }
        ]
      }
    }
    """

private const val LOGIN_SCRIPT =
    """
    function success(request, credential) {
      return {
        type: "success",
        value: {
          accountId: "account-1",
          origin: request.origin,
          credential,
          profile: {
            key: { id: "account-1", host: "plugin.example" },
            handle: "test@plugin.example",
            displayName: "Test"
          },
          capabilities: {
            "flare.datasource.timeline/v1": ["page"]
          }
        }
      };
    }
    definePlugin({
      login: {
        oauth: {
          async begin(request) {
            return {
              type: "externalBrowser",
              url: request.origin + "/oauth?state=" + request.state,
              pendingPayload: { nonce: "opaque" }
            };
          },
          async resume(request) {
            if (request.pendingPayload.nonce !== "opaque") throw new Error("missing pending");
            return success(request, { token: "oauth" });
          }
        },
        form: {
          async begin(request) { return success(request, { token: request.values.token }); }
        },
        cookie: {
          async begin(request) { return { type: "webCookie", startUrl: request.origin + "/login" }; },
          async check(request) {
            await flare.http.request({ url: request.origin + "/validate" });
            const session = request.cookies.cookies.find(it => it.name === "session");
            return session?.value === "valid" ? success(request, { cookies: request.cookies.cookies }) : { type: "pending" };
          }
        }
      },
      capabilities: {
        timeline: {
          async page() { return { items: [] }; }
        }
      }
    });
    """

private const val PLUGIN_ID = "dev.dimension.flare.test.plugin"
private const val ORIGIN = "https://plugin.example"
private const val ACCOUNT_ID = "account-1"
private const val FLOW_ID = "123e4567-e89b-42d3-a456-426614174000"
private const val OTHER_FLOW_ID = "123e4567-e89b-42d3-a456-426614174001"
private val STATE = "a".repeat(64)
