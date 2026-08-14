package dev.dimension.flare.feature.plugin.adapter

import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.list.ListMetaData
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.feature.plugin.host.PluginHttpTransport
import dev.dimension.flare.feature.plugin.host.PluginTransportRequestV1
import dev.dimension.flare.feature.plugin.host.PluginTransportResponseV1
import dev.dimension.flare.feature.plugin.installer.PluginInstaller
import dev.dimension.flare.feature.plugin.installer.TestFppFactory
import dev.dimension.flare.feature.plugin.lifecycle.PluginStateStore
import dev.dimension.flare.feature.plugin.lifecycle.RunningPluginV1
import dev.dimension.flare.feature.plugin.runtime.PluginRuntimePool
import dev.dimension.flare.feature.plugin.wire.EntityKeyV1
import dev.dimension.flare.feature.plugin.wire.LoginSuccessV1
import dev.dimension.flare.feature.plugin.wire.PluginAccountCredentialV1
import dev.dimension.flare.feature.plugin.wire.ProfileV1
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.SYSTEM
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginDetailCapabilityTest {
    private val fileSystem = FileSystem.SYSTEM
    private lateinit var root: Path
    private lateinit var runtimePool: PluginRuntimePool
    private lateinit var scope: CoroutineScope

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("plugin-detail-capability-test").toOkioPath()
        runtimePool = PluginRuntimePool(fileSystem, NoopTransport())
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @AfterTest
    fun tearDown() =
        runBlocking {
            scope.cancel()
            runtimePool.close()
            fileSystem.deleteRecursively(root, mustExist = false)
        }

    @Test
    fun coldListAndDirectMessageDetailsCallDedicatedPluginMethods() =
        runBlocking {
            val plugin = install()
            val accountKey = MicroBlogKey("account-1", "plugin.example")
            val success =
                LoginSuccessV1(
                    accountId = accountKey.id,
                    origin = ORIGIN,
                    credential = JsonObject(emptyMap()),
                    profile = ProfileV1(EntityKeyV1(accountKey.id, accountKey.host), "test@plugin.example", "Test"),
                    capabilities =
                        plugin.installed.manifest.platform.capabilities
                            .mapValues { it.value.operations.keys },
                )
            val context = MemoryPlatformContext(accountKey, plugin.accountCredential(success))
            val dataSource =
                PluginDataSourceV1.authenticated(
                    plugin = plugin,
                    runtimePool = runtimePool,
                    context = context,
                    timelineSpecs = emptyMap(),
                    dynamicTimelineSpec = null,
                    coroutineScope = scope,
                )

            val listAdapter = requireNotNull(dataSource.extraCapabilities.listAdapter)
            assertEquals("Cold list", listAdapter.info("list-1").title)
            assertEquals("Updated", listAdapter.update("list-1", ListMetaData(title = "Updated")).title)

            val roomKey = MicroBlogKey("room-1", accountKey.host)
            assertEquals(roomKey, requireNotNull(dataSource.extraCapabilities.directMessageAdapter).roomInfo(roomKey).key)
            assertEquals(
                setOf(RelationActionType.Follow),
                requireNotNull(dataSource.capabilitySet.relation).supportedRelationTypes,
            )
            assertEquals(
                listOf(NotificationFilter.All, NotificationFilter.Mention),
                requireNotNull(dataSource.capabilitySet.notification?.timeline).supportedNotificationFilter,
            )
        }

    @Test
    fun packageMissingDedicatedDetailMethodIsRejected() =
        runBlocking {
            val input = root / "missing-detail.fpp"
            TestFppFactory.write(input, TestFppFactory.validEntries(manifest = MANIFEST, script = SCRIPT.replace(LIST_DETAIL, "")))
            val store = PluginStateStore.open(fileSystem, root / "missing-detail-store")

            val error = runCatching { PluginInstaller(fileSystem, store).inspect(input) }.exceptionOrNull()

            assertTrue(error != null && "capabilities.list.detail" in error.message.orEmpty())
        }

    private suspend fun install(): RunningPluginV1 {
        val input = root / "plugin.fpp"
        TestFppFactory.write(input, TestFppFactory.validEntries(manifest = MANIFEST, script = SCRIPT))
        val namespace = root / "plugin-store"
        val store = PluginStateStore.open(fileSystem, namespace)
        PluginInstaller(fileSystem, store).let { installer -> installer.inspect(input).also { installer.commit(it, true) } }
        return PluginStateStore
            .open(fileSystem, namespace)
            .running.plugins
            .getValue(PLUGIN_ID)
    }
}

private class MemoryPlatformContext(
    override val accountKey: MicroBlogKey,
    private var value: PluginAccountCredentialV1,
) : PlatformDataSourceContext {
    override fun <T : Any> credential(serializer: KSerializer<T>): T {
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    override fun <T : Any> credentialFlow(serializer: KSerializer<T>): Flow<T> = kotlinx.coroutines.flow.flowOf(credential(serializer))

    override suspend fun <T : Any> updateCredential(
        serializer: KSerializer<T>,
        credential: T,
    ) {
        value = credential as PluginAccountCredentialV1
    }
}

private class NoopTransport : PluginHttpTransport {
    override suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1 =
        PluginTransportResponseV1(200, emptyMap(), "{}".encodeToByteArray())
}

private const val MANIFEST =
    """
    {
      "schemaVersion": 1,
      "apiVersion": 1,
      "id": "dev.dimension.flare.test.detail",
      "version": "1.0.0",
      "name": "Detail test",
      "platform": {
        "id": "DetailTest",
        "name": "Detail test",
        "capabilities": {
          "flare.datasource.list/v1": { "operations": {
            "page": {}, "detail": {}, "create": {}, "update": {}, "delete": {}, "timeline": {},
            "members": {}, "memberships": {}, "addMember": {}, "removeMember": {}
          } },
          "flare.datasource.direct-message/v1": { "operations": {
            "rooms": {}, "room": {}, "messages": {}, "send": {}, "delete": {}, "leave": {},
            "create": {}, "badge": {}, "canSend": {}
          } },
          "flare.datasource.relation/v1": {
            "operations": { "state": {}, "mutate": {} },
            "relationActions": ["follow"]
          },
          "flare.datasource.notification/v1": {
            "operations": { "page": {}, "badge": {} },
            "notificationFilters": ["all", "mention"]
          }
        }
      }
    }
    """

private const val LIST_DETAIL =
    """detail(request) { return { id: request.key.id, title: "Cold list", entityToken: "list-token" }; },"""

private const val SCRIPT =
    """
    const profile = { key: { id: "account-1", host: "plugin.example" }, handle: "test@plugin.example", displayName: "Test" };
    definePlugin({ capabilities: {
      list: {
        page() { return { items: [] }; },
        $LIST_DETAIL
        create(request) { return { id: "created", title: request.title || "Created" }; },
        update(request) {
          if (request.entityToken !== "list-token") throw new Error("missing list token");
          return { id: request.id, title: request.title, entityToken: request.entityToken };
        },
        delete() { return { type: "deleted" }; },
        timeline() { return { items: [] }; },
        members() { return { items: [] }; },
        memberships() { return { items: [] }; },
        addMember() { return profile; },
        removeMember() { return { type: "deleted" }; },
      },
      directMessage: {
        rooms() { return { items: [] }; },
        room(request) { return { key: request.key, title: "Cold room", participants: [profile], entityToken: "room-token" }; },
        messages() { return { items: [] }; },
        send(request) { return { key: { id: "message-1", host: "plugin.example" }, roomKey: request.roomKey, sender: profile, createdAt: "2026-01-01T00:00:00Z", content: { format: "plain", value: request.text } }; },
        delete() { return { type: "deleted" }; },
        leave() { return { type: "deleted" }; },
        create() { return { key: { id: "room-1", host: "plugin.example" }, title: "Room", participants: [profile] }; },
        badge() { return { value: 0 }; },
        canSend() { return { value: true }; },
      },
      relation: {
        state(request) { return { profileKey: request.key }; },
        mutate() { return { type: "noChange" }; },
      },
      notification: {
        page() { return { items: [] }; },
        badge() { return { value: 0 }; },
      },
    } });
    """

private const val PLUGIN_ID = "dev.dimension.flare.test.detail"
private const val ORIGIN = "https://plugin.example"
