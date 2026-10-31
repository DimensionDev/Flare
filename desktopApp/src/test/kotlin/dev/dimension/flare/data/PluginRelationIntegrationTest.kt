package dev.dimension.flare.data

import androidx.paging.LoadState
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.FileSystemUtilsExt
import dev.dimension.flare.data.datasource.microblog.capabilities
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.di.DesktopKoinApplication
import dev.dimension.flare.feature.plugin.PluginSubsystemV1
import dev.dimension.flare.feature.plugin.adapter.accountCredential
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.management.PluginManagementPresenterV1
import dev.dimension.flare.feature.plugin.wire.EntityKeyV1
import dev.dimension.flare.feature.plugin.wire.LoginSuccessV1
import dev.dimension.flare.feature.plugin.wire.PluginAccountCredentialV1
import dev.dimension.flare.feature.plugin.wire.ProfileV1
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformRegistry
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.ui.model.UiRelation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.koin.core.context.stopKoin
import org.koin.plugin.module.dsl.startKoin
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginRelationIntegrationTest {
    private lateinit var root: Path
    private lateinit var originalHome: String
    private lateinit var subsystem: PluginSubsystemV1
    private lateinit var scope: CoroutineScope
    private lateinit var plugin: RunningPluginV1
    private lateinit var platform: PlatformSpec
    private val userKey = MicroBlogKey("user-1", "plugin.example")

    @Before
    fun setUp() =
        runBlocking<Unit> {
            originalHome = System.getProperty("user.home")
            root = Files.createTempDirectory("flare-plugin-relation-test")
            System.setProperty("user.home", root.toString())
            assertEquals(root.resolve(".flare").toFile(), FileSystemUtilsExt.flareDirectory())
            val application = startKoin<DesktopKoinApplication>()
            scope = application.koin.get()
            subsystem = application.koin.get()

            val packagePath = root.resolve("plugin.fpp")
            ZipOutputStream(Files.newOutputStream(packagePath)).use { zip ->
                mapOf(
                    "manifest.json" to MANIFEST.toByteArray(),
                    "plugin.js" to SCRIPT.toByteArray(),
                    "assets/icon.png" to Base64.getDecoder().decode(ICON),
                ).forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            val presenter = PluginManagementPresenterV1(subsystem, scope)
            try {
                presenter.inspect(packagePath.toString())
                presenter.confirmInstall()
            } finally {
                presenter.close()
            }
            plugin =
                subsystem.running.value.plugins.values
                    .single()
            platform = application.koin.get<PlatformRegistry>().require(plugin.installed.manifest.platform.id)
        }

    @After
    fun tearDown() =
        runBlocking {
            if (::scope.isInitialized) scope.coroutineContext.job.cancelAndJoin()
            if (::subsystem.isInitialized) subsystem.runtimePool.close()
            stopKoin()
            System.setProperty("user.home", originalHome)
            root.toFile().deleteRecursively()
            Unit
        }

    @Test
    fun blockAndUnblockApplyReturnedRelationWithoutRefreshingOtherAccounts() =
        runBlocking {
            val handler = relationHandler("account-1")
            val otherHandler = relationHandler("account-2")
            val relation = loadRelation(handler)
            val otherRelation = loadRelation(otherHandler)
            assertEquals(UiRelation(following = true), relation.current())

            handler.block(userKey).join()
            assertEquals(UiRelation(blocking = true), relation.current())
            handler.unblock(userKey).join()
            assertEquals(UiRelation(isFans = true, muted = true), relation.current())
            assertEquals(UiRelation(following = true), otherRelation.current())

            handler.follow(userKey).join()
            assertEquals(UiRelation(following = true, isFans = true, muted = true), relation.current())
            handler.unfollow(userKey).join()
            assertEquals(UiRelation(isFans = true, muted = true), relation.current())
            handler.unmute(userKey).join()
            assertEquals(UiRelation(isFans = true), relation.current())
            handler.mute(userKey).join()
            assertEquals(UiRelation(isFans = true, muted = true), relation.current())
        }

    @Test
    fun noChangeKeepsOptimisticStateAndFailureRestoresPreviousState() =
        runBlocking {
            val handler = relationHandler("account-1")
            val noChangeKey = userKey.copy(id = "no-change")
            val unchangedRelation = loadRelation(handler, noChangeKey)
            handler.unfollow(noChangeKey).join()
            assertEquals(UiRelation(following = false), unchangedRelation.current())

            val failedKey = userKey.copy(id = "failed")
            val failedRelation = loadRelation(handler, failedKey)
            handler.block(failedKey).join()
            assertEquals(UiRelation(following = true), failedRelation.current())
        }

    private fun relationHandler(accountId: String): RelationHandler {
        val accountKey = MicroBlogKey(accountId, userKey.host)
        val credential =
            plugin.accountCredential(
                LoginSuccessV1(
                    accountId = accountId,
                    origin = "https://plugin.example",
                    credential = JsonObject(emptyMap()),
                    profile = ProfileV1(EntityKeyV1(accountId, userKey.host), "$accountId@plugin.example", "Test"),
                    capabilities =
                        plugin.installed.manifest.platform.capabilities
                            .mapValues { it.value.operations.keys },
                ),
            )
        val dataSource = platform.createDataSource(RelationAccountContext(accountKey, credential))
        return requireNotNull(dataSource.capabilities.relation).relationHandler
    }

    private suspend fun loadRelation(
        handler: RelationHandler,
        key: MicroBlogKey = userKey,
    ): Cacheable<UiRelation> =
        handler.relation(key).also { relation ->
            assertEquals(LoadState.NotLoading(true), withTimeout(5_000) { relation.refreshState.first { it !is LoadState.Loading } })
        }

    private suspend fun Cacheable<UiRelation>.current(): UiRelation =
        withTimeout(5_000) { data.filterIsInstance<CacheState.Success<UiRelation>>().first().data }

    private companion object {
        const val ICON = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        val MANIFEST =
            """
            {
              "schemaVersion": 1, "apiVersion": 1, "id": "dev.dimension.flare.test.relation",
              "version": "1.0.0", "defaultLocale": "en", "name": "Relation test",
              "platform": {
                "id": "RelationTest", "name": "Relation test",
                "loginMethods": [{"id": "oauth", "interaction": "OAuth", "title": "OAuth"}],
                "capabilities": {
                  "flare.datasource.relation/v1": {
                    "operations": {"state": {}, "mutate": {}}, "relationActions": ["follow", "block", "mute"]
                  }
                }
              }
            }
            """.trimIndent()
        val SCRIPT =
            """
            let current = {following: true, blocking: false, followedBy: false, muting: false};
            let token = 0;
            function relation(key) {
              const actionTokens = {};
              for (const action of ["Follow", "Unfollow", "Block", "Unblock", "Mute", "Unmute"]) {
                actionTokens[action] = "token-" + token;
              }
              return {...current, profileKey: key, actionTokens};
            }
            defineFlareSocialPlugin({
              login: {oauth: {
                async begin() { return {type: "Pending"}; },
                async resume() { return {type: "Pending"}; }
              }},
              capabilities: {relation: {
                async state(request) { return relation(request.key); },
                async mutate(request) {
                  if (request.actionToken !== "token-" + token) throw new Error("Stale relation token");
                  if (request.key.id === "no-change") return {type: "noChange"};
                  if (request.key.id === "failed") {
                    throw flare.error({code: "Network", message: {key: "failed", fallback: "Mutation failed"}});
                  }
                  switch (request.action) {
                    case "Block": current = {following: false, blocking: true, followedBy: false, muting: false}; break;
                    case "Unblock": current = {following: false, blocking: false, followedBy: true, muting: true}; break;
                    case "Follow": current.following = true; break;
                    case "Unfollow": current.following = false; break;
                    case "Mute": current.muting = true; break;
                    case "Unmute": current.muting = false; break;
                  }
                  token++;
                  return {type: "updatedRelation", relation: relation(request.key)};
                }
              }}
            });
            """.trimIndent()
    }
}

private class RelationAccountContext(
    override val accountKey: MicroBlogKey,
    credential: PluginAccountCredentialV1,
) : PlatformDataSourceContext {
    private val value = MutableStateFlow(Json.encodeToString(PluginAccountCredentialV1.serializer(), credential))

    override fun <T : Any> credential(serializer: KSerializer<T>): T = Json.decodeFromString(serializer, value.value)

    override fun <T : Any> credentialFlow(serializer: KSerializer<T>): Flow<T> = value.map { Json.decodeFromString(serializer, it) }

    override suspend fun <T : Any> updateCredential(
        serializer: KSerializer<T>,
        credential: T,
    ) {
        value.value = Json.encodeToString(serializer, credential)
    }
}
