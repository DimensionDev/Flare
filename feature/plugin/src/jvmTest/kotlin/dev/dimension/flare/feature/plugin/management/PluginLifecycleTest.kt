package dev.dimension.flare.feature.plugin.management

import dev.dimension.flare.data.io.AppFileStore
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.installer.TestFppFactory
import dev.dimension.flare.feature.plugin.login.PluginOAuthPendingStoreV1
import dev.dimension.flare.feature.plugin.login.PluginOAuthPendingV1
import dev.dimension.flare.feature.plugin.runtime.PluginSessionRetiredException
import dev.dimension.flare.feature.plugin.wire.AccountPluginSnapshotV1
import dev.dimension.flare.feature.plugin.wire.EntityKeyV1
import dev.dimension.flare.feature.plugin.wire.PluginAccountCredentialV1
import dev.dimension.flare.feature.plugin.wire.ProfileV1
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PluginLifecycleTest {
    private var indexWritesUntilFailure: Int? = null
    private val fileSystem =
        object : ForwardingFileSystem(FileSystem.SYSTEM) {
            override fun atomicMove(
                source: Path,
                target: Path,
            ) {
                if (target.name == "index.json") {
                    indexWritesUntilFailure?.let {
                        indexWritesUntilFailure = it - 1
                        if (it == 0) {
                            indexWritesUntilFailure = null
                            error("synthetic index failure")
                        }
                    }
                }
                super.atomicMove(source, target)
            }
        }

    @Test
    fun removalIntentFailureKeepsAccountsAndTheWorkingPlugin() =
        runTest {
            withFixture(backgroundScope) { subsystem, presenter, accounts, input ->
                val review = presenter.inspect(input.toString())
                presenter.confirmInstall()
                val owned = account(accounts, "one", review.pluginId, review.platformId)
                indexWritesUntilFailure = 0
                assertFailsWith<IllegalStateException> { presenter.uninstall(review.pluginId) }
                assertTrue(owned in accounts.accounts)
                assertTrue(
                    subsystem.running.value.plugins
                        .getValue(review.pluginId)
                        .session.isActive,
                )
                assertFalse(
                    subsystem.stateStore.desired.value.plugins
                        .getValue(review.pluginId)
                        .removing,
                )
            }
        }

    @Test
    fun finalIndexFailureKeepsRemovalRetryableWithoutRestoringDeletedAccounts() =
        runTest {
            withFixture(backgroundScope) { subsystem, presenter, accounts, input ->
                val review = presenter.inspect(input.toString())
                presenter.confirmInstall()
                account(accounts, "one", review.pluginId, review.platformId)
                indexWritesUntilFailure = 1
                assertFailsWith<IllegalStateException> { presenter.uninstall(review.pluginId) }
                assertTrue(accounts.accounts.isEmpty())
                assertFalse(review.pluginId in subsystem.running.value.plugins)
                assertTrue(
                    subsystem.stateStore.desired.value.plugins
                        .getValue(review.pluginId)
                        .removing,
                )
                val reopened =
                    dev.dimension.flare.feature.plugin.lifecycle.PluginStateStore
                        .open(fileSystem, subsystem.stateStore.paths.root)
                assertFalse(review.pluginId in reopened.running.plugins)
                assertTrue(reopened.running.issues.any { it.code == "uninstall.pending" })
                presenter.inspect(input.toString())
                assertFailsWith<IllegalStateException> { presenter.confirmInstall() }
                assertFalse(review.pluginId in subsystem.running.value.plugins)
                presenter.cancelInstall()
                presenter.uninstall(review.pluginId)
                assertFalse(review.pluginId in subsystem.stateStore.desired.value.plugins)
            }
        }

    @Test
    fun partialAccountCleanupStaysStoppedAndCanBeRetried() =
        runTest {
            withFixture(backgroundScope) { subsystem, presenter, accounts, input ->
                val review = presenter.inspect(input.toString())
                presenter.confirmInstall()
                account(accounts, "one", review.pluginId, review.platformId)
                account(accounts, "two", review.pluginId, review.platformId)
                accounts.afterAccountDeleted = { error("synthetic partial cleanup") }
                assertFailsWith<IllegalStateException> { presenter.uninstall(review.pluginId) }
                assertEquals(1, accounts.accounts.size)
                assertFalse(review.pluginId in subsystem.running.value.plugins)
                assertTrue(
                    subsystem.stateStore.desired.value.plugins
                        .getValue(review.pluginId)
                        .removing,
                )
                accounts.afterAccountDeleted = null
                presenter.uninstall(review.pluginId)
                assertTrue(accounts.accounts.isEmpty())
            }
        }

    @Test
    fun conflictingPlatformCannotReplaceAnotherPluginOrBuiltIn() =
        runTest {
            withFixture(backgroundScope) { subsystem, presenter, _, input ->
                val first = presenter.inspect(input.toString())
                presenter.confirmInstall()
                val before =
                    subsystem.running.value.plugins
                        .getValue(first.pluginId)
                for (platform in listOf(first.platformId, "BuiltIn", first.platformId.lowercase())) {
                    val manifest =
                        TestFppFactory.validManifest
                            .replace(
                                first.pluginId,
                                "test.conflicting.plugin",
                            ).replace(first.platformId, platform)
                    TestFppFactory.write(input, TestFppFactory.validEntries(manifest = manifest))
                    presenter.inspect(input.toString())
                    assertFailsWith<IllegalArgumentException> { presenter.confirmInstall() }
                    assertEquals(setOf(first.pluginId), subsystem.stateStore.desired.value.plugins.keys)
                    assertEquals(before, subsystem.running.value.plugins[first.pluginId])
                    assertTrue(before.session.isActive)
                    presenter.cancelInstall()
                }
            }
        }

    @Test
    fun installAndUninstallImmediatelyRefreshRegistryAndDeleteOnlyOwnedData() =
        runTest {
            withFixture(backgroundScope) { subsystem, presenter, accounts, input ->
                val review = presenter.inspect(input.toString())
                presenter.confirmInstall()
                val running = assertNotNull(subsystem.running.value.plugins[review.pluginId])
                assertEquals(
                    review.pluginId,
                    presenter.state.value.plugins
                        .single()
                        .pluginId,
                )
                val owned = account(accounts, "one", review.pluginId, review.platformId)
                account(accounts, "two", review.pluginId, review.platformId)
                val foreign = account(accounts, "foreign", "another.plugin", review.platformId)
                val builtIn = account(accounts, "builtin", review.pluginId, "BuiltIn")
                presenter.uninstall(review.pluginId)
                assertFalse(review.pluginId in subsystem.running.value.plugins)
                assertFalse(review.pluginId in subsystem.stateStore.desired.value.plugins)
                assertEquals(setOf(foreign, builtIn), accounts.accounts.keys)
                assertFalse(owned in accounts.accounts)
                assertFailsWith<PluginSessionRetiredException> { running.session.ensureActive() }
                assertFalse(FileSystem.SYSTEM.exists(running.packagePath.toPath()))
                assertFalse(FileSystem.SYSTEM.exists(running.iconPath.toPath()))
                presenter.cleanup()
            }
        }

    @Test
    fun failedAccountCleanupRestoresUsableSessionAndCanBeRetried() =
        runTest {
            withFixture(backgroundScope) { subsystem, presenter, accounts, input ->
                val review = presenter.inspect(input.toString())
                presenter.confirmInstall()
                val before = assertNotNull(subsystem.running.value.plugins[review.pluginId])
                val owned = account(accounts, "one", review.pluginId, review.platformId)
                accounts.deletionFailure = IllegalStateException("synthetic deletion failure")
                assertFailsWith<IllegalStateException> { presenter.uninstall(review.pluginId) }
                val restored = assertNotNull(subsystem.running.value.plugins[review.pluginId])
                assertTrue(restored.session.isActive)
                assertFalse(before.session.isActive)
                assertTrue(owned in accounts.accounts)
                assertTrue(review.pluginId in subsystem.stateStore.desired.value.plugins)
                assertTrue(FileSystem.SYSTEM.exists(restored.packagePath.toPath()))
                accounts.deletionFailure = null
                presenter.uninstall(review.pluginId)
                assertTrue(accounts.accounts.isEmpty())
                assertTrue(
                    subsystem.running.value.plugins
                        .isEmpty(),
                )
            }
        }

    @Test
    fun malformedCredentialsDoNotLeakThroughTheDeleteError() =
        runTest {
            withFixture(backgroundScope) { subsystem, presenter, accounts, input ->
                val review = presenter.inspect(input.toString())
                presenter.confirmInstall()
                val account = UiAccount(MicroBlogKey("broken", "same.example"), review.platformId)
                accounts.accounts[account.accountKey] = account to "{secret_token:do-not-disclose}"
                val error = assertFailsWith<IllegalStateException> { presenter.uninstall(review.pluginId) }
                assertFalse(error.message.orEmpty().contains("do-not-disclose"))
                assertTrue(account.accountKey in accounts.accounts)
                assertTrue(review.pluginId in subsystem.running.value.plugins)
            }
        }

    @Test
    fun publishingBlocksUninstallAndPreservesAccountsUntilItCompletes() =
        runTest {
            withFixture(backgroundScope) { subsystem, presenter, accounts, input ->
                val review = presenter.inspect(input.toString())
                presenter.confirmInstall()
                val running = assertNotNull(subsystem.running.value.plugins[review.pluginId])
                val owned = account(accounts, "one", review.pluginId, review.platformId)
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                val writer =
                    launch {
                        running.session.invoke("posts.publish") {
                            entered.complete(Unit)
                            release.await()
                        }
                    }
                entered.await()
                try {
                    assertFailsWith<IllegalStateException> { presenter.uninstall(review.pluginId) }
                    assertTrue(running.session.isActive)
                    assertTrue(owned in accounts.accounts)
                } finally {
                    release.complete(Unit)
                    writer.join()
                }
                presenter.uninstall(review.pluginId)
                assertTrue(accounts.accounts.isEmpty())
            }
        }

    private suspend fun account(
        accounts: PluginTestAccounts,
        id: String,
        pluginId: String,
        platformId: String,
    ): MicroBlogKey {
        val key = MicroBlogKey(id, "same.example")
        accounts.addAccount(
            UiAccount(key, platformId),
            PluginAccountCredentialV1(
                AccountPluginSnapshotV1(
                    pluginId,
                    platformId,
                    "a".repeat(64),
                    "https://same.example",
                    id,
                    ProfileV1(EntityKeyV1(id, "same.example"), id, id),
                    emptyMap(),
                    emptyMap(),
                    credentialSchemaVersion = 1,
                ),
                JsonObject(emptyMap()),
            ),
            PluginAccountCredentialV1.serializer(),
        )
        return key
    }

    private suspend fun withFixture(
        scope: CoroutineScope,
        block: suspend (PluginSubsystemV1, PluginManagementPresenterV1, PluginTestAccounts, Path) -> Unit,
    ) {
        val root = Files.createTempDirectory("plugin-lifecycle").toOkioPath()
        val subsystem =
            PluginSubsystemV1(
                object : AppFileStore {
                    override val fileSystem = this@PluginLifecycleTest.fileSystem

                    override fun directory(name: String): Path = root / name
                },
                object : PluginOAuthPendingStoreV1 {
                    override suspend fun save(pending: PluginOAuthPendingV1) = Unit

                    override suspend fun load(flowId: String): PluginOAuthPendingV1? = null

                    override suspend fun consume(pending: PluginOAuthPendingV1) = false
                },
            )
        val registry = testPluginRegistry(subsystem, scope)
        val accounts = PluginTestAccounts()
        val presenter = PluginManagementPresenterV1(subsystem, scope, registry = { registry }, accounts = { accounts })
        val input = root / "input.fpp"
        TestFppFactory.write(input)
        try {
            block(subsystem, presenter, accounts, input)
            assertEquals(
                subsystem.running.value.plugins.values
                    .map { it.installed.manifest.platform.id }
                    .toSet(),
                registry.all
                    .map { it.platformId }
                    .filterNot {
                        it ==
                            "BuiltIn"
                    }.toSet(),
            )
        } finally {
            presenter.close()
            subsystem.runtimePool.close()
            FileSystem.SYSTEM.deleteRecursively(root)
        }
    }
}
