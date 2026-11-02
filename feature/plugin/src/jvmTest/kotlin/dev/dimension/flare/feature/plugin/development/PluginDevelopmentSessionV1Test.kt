package dev.dimension.flare.feature.plugin.development

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.io.AppFileStore
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.repository.AccountMicroblogDataSource
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.host.PluginInvocationContextV1
import dev.dimension.flare.feature.plugin.installer.TestFppFactory
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthPendingStoreV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthPendingV1
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimeKeyV1
import dev.dimension.flare.feature.plugin.runtime.PluginSessionRetiredException
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformMetadata
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.model.PlatformRuntimeData
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.ui.model.PluginMediaPermissions
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.asType
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PluginDevelopmentSessionV1Test {
    private lateinit var root: Path
    private lateinit var scope: CoroutineScope
    private lateinit var subsystem: PluginSubsystemV1
    private lateinit var registry: PlatformRegistry
    private lateinit var session: PluginDevelopmentSessionV1
    private val accounts = TestAccounts()
    private val files = FileSystem.SYSTEM
    private val directory get() = root / "build"
    private val current get() =
        subsystem.running.value.plugins
            .getValue(ID)
    private val appFiles get() =
        object : AppFileStore {
            override val fileSystem = files

            override fun directory(name: String) = root / name
        }

    @BeforeTest
    fun setup() {
        root = Files.createTempDirectory("plugin-development-test").toOkioPath()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @AfterTest
    fun teardown() =
        runBlocking {
            scope.cancel()
            if (::subsystem.isInitialized) subsystem.runtimePool.close()
            files.deleteRecursively(root)
        }

    @Test
    fun reloadsInPlaceAndRestoresInstalledCodeWithoutChangingTheIndex() =
        runBlocking {
            prepare(installed = true)
            val original = current
            val originalSpec = registry.require(PLATFORM)
            val unchanged = subsystem.stateStore.desired.value
            writeSource("development")
            session.open(directory.toString())
            session.reload()
            assertNotNull(session.state.value.reviewHash)
            assertFalse(session.state.value.active)
            session.reload(confirmed = true)
            val first = current
            val icon = Paths.get(URI((registry.require(PLATFORM).metadata.icon as IconType.Url).url)).toOkioPath()
            assertNotSame(originalSpec, registry.require(PLATFORM))
            assertEquals("development", page()["marker"]!!.jsonPrimitive.content)
            assertEquals(2, page()["count"]!!.jsonPrimitive.int)
            session.reload()
            assertEquals(1, page()["count"]!!.jsonPrimitive.int)
            assertFailsWith<PluginSessionRetiredException> { page(first) }
            assertFailsWith<PluginSessionRetiredException> { page(original) }
            assertEquals(unchanged, subsystem.stateStore.desired.value)

            files.write(directory / "plugin.js") { writeUtf8("defineFlareSocialPlugin({ broken") }
            awaitState { it.error != null }
            assertTrue(session.state.value.active)
            assertNull(session.state.value.candidateHash)
            assertEquals("development", page()["marker"]!!.jsonPrimitive.content)
            assertFailsWith<IllegalStateException> { session.reload() }
            writeSource("edited")
            awaitState { it.candidateHash != null && it.error == null }
            assertEquals("development", page()["marker"]!!.jsonPrimitive.content)
            session.reload()
            assertEquals("edited", page()["marker"]!!.jsonPrimitive.content)

            session.stop()
            assertEquals("installed", page()["marker"]!!.jsonPrimitive.content)
            assertEquals(original.installed, current.installed)
            assertEquals(unchanged, subsystem.stateStore.desired.value)
            assertFalse(session.state.value.active)
            assertNull(session.state.value.directory)
            assertEquals(directory.toString(), session.state.value.recentDirectory)
            assertTrue(accounts.invalidated.all { it == PLATFORM })
            assertEquals(listOf(icon), files.list(root / "plugin-development" / "icons"))
        }

    @Test
    fun reviewsPermissionExpansionAndRevokesItOnReloadAndStop() =
        runBlocking {
            prepare()
            writeSource("first")
            session.open(directory.toString())
            session.reload()
            val review = session.state.value.reviewHash
            writeSource("second", permissions = true)
            assertFailsWith<IllegalArgumentException> { session.reload(confirmed = true) }
            assertNotSame(review, session.state.value.candidateHash)
            session.reload()
            assertTrue(
                session.state.value.permissions
                    .isNotEmpty(),
            )
            session.reload(confirmed = true)
            assertTrue(PluginMediaPermissions.isAllowed(ID))
            repeat(24) { page() }
            assertEquals(40, session.state.value.messages.size)
            assertTrue(
                session.state.value.messages
                    .all { "request" !in it && "marker" !in it },
            )
            writeSource("third")
            session.reload()
            assertNull(session.state.value.reviewHash)
            assertFalse(PluginMediaPermissions.isAllowed(ID))
            session.stop()
            assertFalse(registry.isRegistered(PLATFORM))
            assertFalse(
                subsystem.running.value.plugins
                    .containsKey(ID),
            )
            assertFalse(PluginMediaPermissions.isAllowed(ID))
            session.open(directory.toString())
            session.reload()
            session.reload(confirmed = true)
            assertTrue(registry.isRegistered(PLATFORM))
        }

    @Test
    fun refusesReloadDuringWritesAndRollsBackIfCacheInvalidationFails() =
        runBlocking {
            prepare(installed = true)
            writeSource("development")
            session.open(directory.toString())
            session.reload()
            val original = current
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val publishing =
                async {
                    original.session.invoke("capabilities.compose.publish") {
                        entered.complete(Unit)
                        release.await()
                    }
                }
            entered.await()
            assertFailsWith<IllegalStateException> { session.reload(confirmed = true) }
            assertSame(original, current)
            original.session.ensureActive()
            release.complete(Unit)
            publishing.await()
            accounts.failNext = true
            assertFailsWith<IllegalStateException> { session.reload(confirmed = true) }
            assertNotSame(original, current)
            assertEquals("installed", page()["marker"]!!.jsonPrimitive.content)
            session.reload(confirmed = true)
            assertEquals("development", page()["marker"]!!.jsonPrimitive.content)
        }

    @Test
    fun manualReloadRetiresLoginAndRollbackPreservesTemporaryOwnership() =
        runBlocking {
            prepare(installed = true)
            val installed = subsystem.stateStore.desired.value
            writeSource("development")
            session.open(directory.toString())
            session.reload()
            val original = current
            val release = original.session.holdInteraction()
            try {
                session.reload(confirmed = true)
                assertFalse(original.session.isActive)
                repeat(3) {
                    val active = current
                    accounts.failNext = true
                    assertFailsWith<IllegalStateException> { session.reload() }
                    assertFalse(active.session.isActive)
                    assertTrue(current.session.isActive)
                    assertTrue(current.generation > active.generation)
                    assertTrue(session.state.value.active)
                    assertEquals(current.generation, session.state.value.generation)
                    assertEquals(current.installed.packageHash, session.state.value.runningHash)
                    val messages = session.state.value.messages
                    active.diagnostic?.invoke(active, "retired diagnostic")
                    assertEquals(messages, session.state.value.messages)
                    assertEquals("development", page()["marker"]!!.jsonPrimitive.content)
                    assertTrue(
                        session.state.value.messages
                            .last()
                            .startsWith("#${current.generation} "),
                    )
                    assertEquals(installed, subsystem.stateStore.desired.value)
                }
                val restored = current
                session.reload()
                assertTrue(current.generation > restored.generation)
                session.stop()
                assertEquals("installed", page()["marker"]!!.jsonPrimitive.content)
                assertEquals(installed, subsystem.stateStore.desired.value)
            } finally {
                release()
            }
        }

    @Test
    fun directoryWatchingSurvivesClosingTheManagementPageDuringValidation() =
        runBlocking<Unit> {
            prepare()
            writeSource("development")
            val opening = launch { session.open(directory.toString()) }
            awaitState { it.validating }
            opening.cancelAndJoin()
            awaitState { it.candidateHash != null }
            assertFalse(session.state.value.active)
            session.stop()
        }

    @Test
    fun samePluginInstallationChangesAreBlockedDuringDevelopment() =
        runBlocking {
            prepare(installed = true)
            val presenter =
                dev.dimension.flare.feature.plugin.management
                    .PluginManagementPresenterV1(subsystem, scope, registry = { registry }, accounts = { accounts })
            try {
                writeSource("development")
                session.open(directory.toString())
                session.reload()
                session.reload(confirmed = true)
                val installed = subsystem.stateStore.desired.value
                assertFailsWith<IllegalStateException> { presenter.uninstall(ID) }
                presenter.inspect((root / "installed.fpp").toString())
                assertFailsWith<IllegalStateException> { presenter.confirmInstall() }
                assertEquals(installed, subsystem.stateStore.desired.value)
                session.stop()
                presenter.uninstall(ID)
                assertFalse(ID in subsystem.stateStore.desired.value.plugins)
                assertFalse(ID in subsystem.running.value.plugins)
            } finally {
                presenter.close()
            }
        }

    @Test
    fun rejectsPlatformIdentityChangesAndRecoversAfterMissingFilesReturn() =
        runBlocking<Unit> {
            prepare(installed = true)
            writeSource("wrong", manifest = TestFppFactory.validManifest.replace(PLATFORM, "OtherPlatform"))
            session.open(directory.toString())
            assertNotNull(session.state.value.error)
            assertFalse(registry.isRegistered("OtherPlatform"))
            writeSource("correct")
            awaitState { it.candidateHash != null }
            val hash = session.state.value.candidateHash
            files.delete(directory / "plugin.js")
            awaitState { it.error != null }
            writeSource("correct")
            awaitState { it.candidateHash == hash && it.error == null }
        }

    @Test
    fun rechecksInstalledIdentityBeforeStartingAnUnchangedCandidate() =
        runBlocking {
            prepare()
            writeSource("development")
            session.open(directory.toString())
            session.reload()
            val candidateHash = session.state.value.candidateHash
            val archive = root / "newly-installed.fpp"
            TestFppFactory.write(
                archive,
                TestFppFactory.validEntries(
                    manifest = TestFppFactory.validManifest.replace(PLATFORM, "OtherPlatform"),
                ),
            )
            subsystem.installer.commit(subsystem.installer.inspect(archive), confirmed = true, activate = subsystem.stateStore::install)

            assertFailsWith<IllegalStateException> { session.reload(confirmed = true) }
            assertFalse(session.state.value.active)
            assertFalse(registry.isRegistered(PLATFORM))
            assertEquals(
                "OtherPlatform",
                subsystem.stateStore.desired.value.plugins
                    .getValue(ID)
                    .manifest.platform.id,
            )

            subsystem.stateStore.uninstall(ID)
            session.reload()
            assertEquals(candidateHash, session.state.value.candidateHash)
            session.reload(confirmed = true)
            assertTrue(session.state.value.active)
        }

    @Test
    fun retiredGuestSpecCannotRestoreRevokedMediaPermission() =
        runBlocking {
            prepare()
            val guestManifest =
                TestFppFactory.validManifest.replace(
                    "\"detector\":",
                    "\"guest\": { \"enabled\": true }, \"detector\":",
                )
            writeSource("first", permissions = true, manifest = guestManifest)
            session.open(directory.toString())
            session.reload()
            session.reload(confirmed = true)
            val oldSpec = registry.require(PLATFORM)
            assertTrue(PluginMediaPermissions.isAllowed(ID))
            writeSource("revoked", manifest = guestManifest)
            session.reload()
            assertFalse(PluginMediaPermissions.isAllowed(ID))
            oldSpec.guestDataSource("test.example", "en")
            assertFalse(PluginMediaPermissions.isAllowed(ID), "A retired guest spec restored media permission after revocation")
            session.stop()
            oldSpec.guestDataSource("test.example", "en")
            assertFalse(PluginMediaPermissions.isAllowed(ID))
        }

    private suspend fun prepare(installed: Boolean = false) {
        fun newSubsystem() =
            PluginSubsystemV1(
                appFiles,
                object : PluginOAuthPendingStoreV1 {
                    override suspend fun save(pending: PluginOAuthPendingV1) = Unit

                    override suspend fun load(flowId: String): PluginOAuthPendingV1? = null

                    override suspend fun consume(pending: PluginOAuthPendingV1) = false
                },
            )
        subsystem = newSubsystem()
        if (installed) {
            val archive = root / "installed.fpp"
            TestFppFactory.write(archive, TestFppFactory.validEntries(script = script("installed")))
            subsystem.installer.commit(subsystem.installer.inspect(archive), confirmed = true, activate = subsystem.stateStore::install)
            subsystem.runtimePool.close()
            subsystem = newSubsystem()
        }
        val guest =
            object : PlatformSpec {
                override val platformId = "BuiltIn"
                override val metadata = PlatformMetadata(platformId, UiIcon.World.asType())
                override val isDefaultGuest = true
                override val timelineSpecs = persistentListOf<TimelineSpec<out TimelineSpec.Data>>()

                override fun deepLinks(accountKey: MicroBlogKey) = persistentListOf<PlatformDeepLink<*>>()

                override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource = error("unused")

                override fun guestDataSource(
                    host: String,
                    locale: String,
                ): MicroblogDataSource = error("unused")
            }
        registry = PlatformRegistry(PlatformRuntimeData(listOf(guest), emptyList(), listOf(subsystem.platformSource(scope))))
        session = PluginDevelopmentSessionV1(subsystem, registry, accounts, scope)
    }

    private fun script(marker: String) =
        TestFppFactory.validScript.replace(
            "return { items: [] };",
            "globalThis.count = (globalThis.count || 0) + 1; return { count: globalThis.count, marker: '$marker' };",
        )

    private fun writeSource(
        marker: String,
        permissions: Boolean = false,
        manifest: String = TestFppFactory.validManifest,
    ) {
        val content =
            if (permissions) {
                manifest.replace(
                    "\"schemaVersion\": 1,",
                    "\"schemaVersion\": 1, \"permissions\": { \"mediaNetwork\": true },",
                )
            } else {
                manifest
            }
        TestFppFactory.validEntries(manifest = content, script = script(marker)).forEach { (name, bytes) ->
            val path = directory / name
            files.createDirectories(path.parent!!)
            files.write(path) { write(bytes) }
        }
    }

    private suspend fun awaitState(predicate: (PluginDevelopmentStateV1) -> Boolean) = withTimeout(8_000) { session.state.first(predicate) }

    private suspend fun page(plugin: RunningPluginV1 = current): JsonObject =
        coroutineScope {
            async {
                val hash = plugin.installed.packageHash
                subsystem.runtimePool
                    .invokeJson(
                        plugin,
                        PluginRuntimeKeyV1.guest(ID, hash, "https://test.example"),
                        PluginInvocationContextV1.guest(ID, PLATFORM, hash, "https://test.example", "en"),
                        "capabilities.timeline.page",
                        JsonObject(emptyMap()),
                    ).jsonObject
            }.await()
        }
}

private class TestAccounts : AccountService {
    override suspend fun deleteAccounts(
        platformId: String,
        ownsCredential: (String) -> Boolean,
        beforeDelete: suspend () -> Unit,
    ) = beforeDelete()

    val invalidated = mutableListOf<String>()
    var failNext = false

    override suspend fun invalidatePlatform(platformId: String) {
        if (failNext) {
            failNext = false
            error("cache unavailable")
        }
        invalidated += platformId
    }

    override fun accountServiceFlow(accountType: AccountType): Flow<MicroblogDataSource> = error("unused")

    override fun allAccountServicesFlow(): Flow<List<AccountMicroblogDataSource>> = error("unused")

    override suspend fun <T : Any> addAccount(
        account: UiAccount,
        credential: T,
        serializer: KSerializer<T>,
    ) = Unit

    override fun <T : Any> credentialFlow(
        accountKey: MicroBlogKey,
        serializer: KSerializer<T>,
    ): Flow<T> = error("unused")

    override fun <T : Any> updateCredential(
        accountKey: MicroBlogKey,
        credential: T,
        serializer: KSerializer<T>,
    ): Job = error("unused")
}

private const val ID = "dev.dimension.flare.test.plugin"
private const val PLATFORM = "TestPlugin"
